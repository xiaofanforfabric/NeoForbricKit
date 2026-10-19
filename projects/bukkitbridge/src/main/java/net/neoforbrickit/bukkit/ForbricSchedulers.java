/*
 * Forbric Bukkit bridge — the three region schedulers, on a server that has one thread.
 */
package net.neoforbrickit.bukkit;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.plugin.Plugin;

/**
 * The region/global/async schedulers a Folia-aware plugin expects to find.
 *
 * <h2>What these mean when there is only one thread</h2>
 *
 * <p>Folia splits the world into regions, each ticked by its own thread, and these schedulers are how a
 * plugin gets work onto the right one. This bridge runs one game thread that ticks everything, so:
 *
 * <ul>
 *   <li><b>{@code RegionScheduler} and {@code GlobalRegionScheduler} both mean "run on the game
 *       thread".</b> The region and the world arguments are accepted and ignored — every region belongs
 *       to the one thread, so every destination is the same destination. A plugin that picks a region
 *       carefully gets the behaviour it asked for.
 *   <li><b>{@code execute} runs inline when the caller is already on that thread</b>, which is the case
 *       inside a command or an event. This is not just an optimisation: {@code execute} is documented as
 *       "run on the next tick of the region's thread", and a task that both reads and writes world state
 *       between two calls would see different state if it were deferred. Running inline is what a
 *       single-threaded server does for the same call.
 *   <li><b>Delays are counted by the server's own tick counter.</b> There is no tick hook in this bridge
 *       yet, so a delay is realised with a real timer. That makes a "tick" about 50 ms, matching a healthy
 *       server, but it is a wall-clock approximation rather than the tick the game actually ran. Code that
 *       needs exact tick alignment should use {@code BukkitScheduler}, which the same caveat applies to.
 * </ul>
 *
 * <h2>Cancellation and the task handle</h2>
 *
 * <p>Every scheduling call returns a {@link ScheduledTask}; the handle is live, so {@code cancel()} stops a
 * repeating task and {@code getExecutionState()} reports what happened. A cancelled task that was already
 * queued does not run.
 *
 * <h2>Why every type here is public</h2>
 *
 * <p>These classes implement Paper's scheduler interfaces, and the objects are handed to plugins through
 * {@code Server.getRegionScheduler()} and friends. A plugin that supports Folia does NOT call those methods
 * directly — the interface is absent on older servers, so it looks the method up reflectively:
 *
 * <pre>{@code
 * Object scheduler = server.getClass().getMethod("getGlobalRegionScheduler").invoke(server);
 * scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
 *          .invoke(scheduler, plugin, task, delayTicks);
 * }</pre>
 *
 * <p>{@code invoke} checks that the CALLER may access the method's declaring class. A package-private nested
 * class fails that check with {@code IllegalAccessException} even though the method itself is public, and
 * every one of these plugins wraps the lookup in {@code catch (Throwable ignored)} — so the failure is
 * silent, the call falls through to {@code Bukkit.getScheduler()}, and the plugin dies somewhere else
 * entirely. Measured, not assumed: this is exactly how SimpleCom failed (its own stack showed
 * {@code Bukkit.getScheduler} as the last frame before the {@code UnsupportedOperationException}).
 *
 * <p>Making them public costs nothing and is the only way reflectively-dispatched calls can work.
 */
public final class ForbricSchedulers {

    /** The unit a "tick" is here: 50 ms, the length of a healthy server tick. */
    private static final long MILLIS_PER_TICK = 50L;

    private final ServerImpl server;
    /** One timer for region and global work, which both mean the game thread. */
    private final ScheduledExecutorService gameTimer =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "forbric-bukkit-scheduler");
                thread.setDaemon(true);
                return thread;
            });
    /** A separate pool for async work, so a blocking plugin task cannot stall the game timer. */
    private final ScheduledExecutorService asyncPool =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "forbric-bukkit-async");
                thread.setDaemon(true);
                return thread;
            });
    /** Every task handed out, so {@code cancelTasks(plugin)} can find a plugin's own. */
    private final CopyOnWriteArrayList<Task> tasks = new CopyOnWriteArrayList<>();

    public ForbricSchedulers(ServerImpl server) {
        this.server = server;
    }

    public RegionScheduler region() {
        return new Region();
    }

    public GlobalRegionScheduler global() {
        return new Global();
    }

    public AsyncScheduler async() {
        return new Async();
    }

    /**
     * An entity's scheduler.
     *
     * <p>A fresh handle per call, like every other wrapper this bridge hands out: the scheduler holds
     * nothing but the entity, and the tasks it hands out live in this object's own registry, which is
     * shared. So two calls for one entity are interchangeable and a plugin does not have to keep the
     * first one.
     */
    public io.papermc.paper.threadedregions.scheduler.EntityScheduler entity(
                    net.minecraft.world.entity.Entity entity) {
        return new Entity(entity);
    }

    /** Stops every timer, for a clean shutdown. */
    public void shutdown() {
        gameTimer.shutdownNow();
        asyncPool.shutdownNow();
    }

    // ---------------------------------------------------------------------------------------------
    // The seam the classic {@code BukkitScheduler} uses.  Those methods hand out int ids and work with
    // a plain Runnable, while everything here is built around a Consumer<ScheduledTask>; the adapter in
    // ForbricBukkitScheduler is thin because the actual queueing lives HERE.  Routing both APIs through
    // one timer is the point: a server that ticks on one thread has exactly one notion of "next tick",
    // and two independent schedulers would drift apart (BukkitScheduler's "later" and
    // GlobalRegionScheduler's "later" would fire in a different order than the plugin wrote them).
    // ---------------------------------------------------------------------------------------------

    /** Queues {@code body} for the game thread after {@code delayTicks}, as an AsyncScheduler-style task. */
    ScheduledTask scheduleGameTask(Plugin plugin, Consumer<ScheduledTask> body, long delayTicks) {
        return scheduleGame(plugin, body, delayTicks, 0L, false);
    }

    /** Queues {@code body} for the game thread, repeating every {@code periodTicks}. */
    ScheduledTask scheduleGameRepeating(Plugin plugin, Consumer<ScheduledTask> body, long delayTicks,
                    long periodTicks) {
        return scheduleGame(plugin, body, delayTicks, periodTicks, true);
    }

    /** Queues {@code body} the way {@link #region()} would, with no coordinates to choose between. */
    ScheduledTask scheduleGameNow(Plugin plugin, Consumer<ScheduledTask> body) {
        return scheduleGame(plugin, body, 0L, 0L, false);
    }

    /** The async pool, for the classic API's {@code runTaskAsynchronously} family. */
    AsyncScheduler asyncScheduler() {
        return async();
    }

    /**
     * Runs a {@code Callable} on the game thread and hands back its value through a future.
     *
     * <p>When the caller is already on that thread the value is computed inline: submitting and waiting
     * would deadlock, because the thread that has to run the work is the one waiting for it. That is the
     * same distinction Bukkit's own {@code callSyncMethod} makes.
     */
    <T> java.util.concurrent.Future<T> callOnGameThread(Plugin plugin, java.util.concurrent.Callable<T> callable) {
        java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
        Runnable body = () -> {
            try {
                future.complete(callable.call());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        };
        if (isGameThread()) {
            body.run();
        } else {
            scheduleGameNow(plugin, task -> body.run());
        }
        return future;
    }

    /** Whether the calling thread is the game's own tick thread, for the API's "am I on the main thread". */
    boolean onGameThread() {
        return isGameThread();
    }

    /** Stops one task by the id the classic API handed out. */
    boolean cancelTaskById(int id) {
        for (Task task : tasks) {
            if (task.id == id) {
                task.cancel();
                tasks.remove(task);
                return true;
            }
        }
        return false;
    }

    /** Whether a task with this id is still queued and not cancelled. */
    boolean isQueuedById(int id) {
        for (Task task : tasks) {
            if (task.id == id) return !task.stopped();
        }
        return false;
    }

    /** The tasks the classic API reports as pending, in id order. */
    java.util.List<Task> pendingTasks() {
        return new java.util.ArrayList<>(tasks);
    }

    /** Every task a plugin still owns, which is what {@code BukkitTask.getOwner()} answers from. */
    java.util.List<Task> tasksOf(Plugin plugin) {
        java.util.List<Task> mine = new java.util.ArrayList<>();
        for (Task task : tasks) {
            if (task.plugin == plugin) mine.add(task);
        }
        return mine;
    }

    /** Cancels every task a plugin owns, as the interface's own {@code cancelTasks(Plugin)} does. */
    void cancelTasksOf(Plugin plugin) {
        cancelTasks(plugin);
    }

    /**
     * Queues a task for the game thread, running it now when the caller is already on that thread.
     *
     * <p>The inline case is the common one — a command runs on the game thread, so a plugin's
     * {@code ensureRegion}-style wrapper takes the direct path — and it is also the correct one: the
     * caller asked for work to happen on the thread that owns the region, and it is already there.
     */
    private Task scheduleGame(Plugin plugin, Consumer<ScheduledTask> body, long delayTicks, long periodTicks,
                    boolean repeating) {
        Task task = new Task(plugin, body, repeating);
        tasks.add(task);
        long delayMillis = Math.max(0L, delayTicks) * MILLIS_PER_TICK;
        long periodMillis = Math.max(1L, periodTicks) * MILLIS_PER_TICK;

        if (delayMillis == 0L && !repeating && isGameThread()) {
            // Already where it needs to be and wanted now: run it here rather than round-tripping through
            // the timer, which would add a tick of latency to every synchronous scheduling call.
            //
            // Through runOnGameThread rather than invoke(), even though the thread is already right, and
            // that is what makes a throwing plugin task survivable: runOnGameThread routes into invoke(),
            // which contains the failure through Task#fail and reports it against the plugin. Calling the
            // body directly here would let the exception escape into the scheduling call -- so a plugin
            // that schedules from a command handler would see the game's command dispatch report the
            // throw, and the task bookkeeping (state, forget()) would be left half done.
            task.runOnGameThread(server, false);
            return task;
        }

        // `fromTimer`, not the thread we happen to be on: a plugin body running on the timer can schedule
        // the next run synchronously, and that call has to queue rather than run here.
        //
        // The zero delay is set here rather than in the task because the body runs one hop LATER now: the
        // timer fires, this dispatch hands the body to the game queue, and the body runs on the next tick.
        // A plugin that re-schedules itself from inside the body (Simple Voice Chat does, per message) reads
        // what is left of the delay, and the delay it asked for is already spent.
        Runnable dispatch = () -> {
            task.delayMillis = 0L;
            task.runOnGameThread(server, true);
        };
        if (repeating) {
            gameTimer.scheduleAtFixedRate(dispatch, delayMillis, periodMillis, TimeUnit.MILLISECONDS);
        } else {
            gameTimer.schedule(dispatch, delayMillis, TimeUnit.MILLISECONDS);
        }
        return task;
    }

    /** Whether the calling thread is the game's tick thread. */
    private boolean isGameThread() {
        return server.isPrimaryThread();
    }

    /** The process-wide timer, so {@link ForbricSchedulers#shutdown()} can reach it from the bridge. */
    private static final AtomicInteger TASK_IDS = new AtomicInteger();

    /** The next id the classic API will hand out, shared with {@link Task} so both APIs number one sequence. */
    static int nextTaskId() {
        return TASK_IDS.incrementAndGet();
    }

    /**
     * One scheduled task, and its state.
     *
     * <p>Holds its own cancelled flag rather than asking the executor: a task cancelled after it was queued
     * but before it ran must not run, and the executor has no way to say that. {@code repeating} is
     * reported to the plugin, which branches on it to decide whether the handle needs cancelling at all.
     */
    public final class Task implements ScheduledTask {

        private final Plugin plugin;
        private final Consumer<ScheduledTask> body;
        private final boolean repeating;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        /**
         * The delay this task was scheduled with, in ms — the wait left before its body runs.
         *
         * <p>Handed over as the times arrive rather than kept as ticks: the timer takes milliseconds, and
         * converting back would let a fractional tick round into a delay nobody asked for. Cleared when the
         * timer hands the body over, because from that point the wait is over — a plugin that reads it inside
         * its own body to schedule the next run would otherwise pick the previous delay up again and run a
         * tick early, which is the drift this class's timer hop was changed to stop.
         */
        volatile long delayMillis;
        /** The id BOTH APIs report: ScheduledTask has none, BukkitTask's is this. */
        final int id = TASK_IDS.incrementAndGet();
        private volatile ExecutionState state = ExecutionState.IDLE;

        public Task(Plugin plugin, Consumer<ScheduledTask> body, boolean repeating) {
            this.plugin = plugin;
            this.body = body;
            this.repeating = repeating;
        }

        /**
         * Runs the body on the game thread, skipping it when it was cancelled while queued.
         *
         * <p><b>{@code fromTimer} is not the same question as {@code isPrimaryThread()}.</b> The timer
         * threads this class owns are neither the game thread nor the caller's, and
         * {@code ServerImpl.isPrimaryThread()} has to keep answering "not the game thread" about them:
         * it is the API's answer to "may I touch the world directly", and returning {@code true} on a
         * thread the game is not ticking would let a plugin write to the world from a foreign thread
         * with no warning.
         *
         * <p>The consequence is that this hop cannot be decided by asking the thread: while a task runs
         * on the timer, the calling thread answers "not primary", {@code minecraft().execute} sees a task
         * that is already queued on the game thread and queues it behind itself, and the body runs one
         * server tick BEFORE the task's own delay elapses. A timer task would then drift a tick early
         * every period; TAB's belowname refresh hit this on the join path. So the timer says who it is,
         * and a dispatch from it is always handed to the game's own queue.
         */
        void runOnGameThread(ServerImpl server, boolean fromTimer) {
            if (stopped()) {
                state = ExecutionState.CANCELLED;
                forget();
                return;
            }
            // The body is run here rather than re-dispatched: the timer thread is not the game thread, so
            // this hops onto it through the server's own queue when it has to.
            if (!fromTimer && server.isPrimaryThread()) {
                invoke();
                forget();
            } else {
                server.minecraft().execute(() -> {
                    invoke();
                    forget();
                });
            }
        }

        /**
         * Drops a finished one-shot from the registry.
         *
         * <p>Without this the registry grows by one entry per scheduling call for the life of the
         * server, and because it is a {@link CopyOnWriteArrayList} every subsequent add copies the
         * whole array. A plugin that schedules per packet — Simple Voice Chat does, once per
         * outbound message — turns that into seconds of latency within minutes.
         *
         * <p>Repeating tasks stay listed: they can still be cancelled, and {@code cancelTasks}
         * has to be able to find them.
         */
        void forget() {
            if (repeating || cancelled.get()) {
                // A cancelled one-shot was already removed by cancel(); a repeating task is needed
                // until the plugin that owns it stops.
                return;
            }
            tasks.remove(this);
        }

        /** True once {@link #cancel()} has been called, whatever the reason. */
        boolean stopped() {
            return cancelled.get();
        }

        /**
         * Whether the body is executing on this very call.
         *
         * <p>Read by the classic API's {@code isCurrentlyRunning}, which asks a different question from
         * {@code isQueued}: a task waiting for its delay is queued but not running, and the two answers have
         * to differ or a plugin polling both cannot tell "not yet" from "in progress".
         */
        boolean isRunningNow() {
            return state == ExecutionState.RUNNING;
        }

        /** Whether this task repeats, which the classic API reports through {@code isSync()} on its handle. */
        boolean repeats() {
            return repeating;
        }

        /** The plugin that scheduled it, for {@code BukkitTask.getOwner()}. */
        Plugin owner() {
            return plugin;
        }

        /**
         * Whether the task is still queued or running.
         *
         * <p>Distinct from {@link #stopped()}: {@code BukkitTask.isCancelled()} must stay false for a task
         * that already FINISHED, because "ran to completion" and "was called off" are different answers and
         * a plugin branches on the difference.
         */
        boolean cancelledOrNot() {
            return cancelled.get();
        }

        /** Marks the body as about to run, for the state a plugin can read from inside it. */
        void markRunning() {
            state = ExecutionState.RUNNING;
        }

        /**
         * Runs the body and records what happened, on whichever thread the caller chose.
         *
         * <p>Also where the delay is spent. The hop onto the game queue means the body runs on the tick
         * AFTER the timer fired, and a plugin that schedules its next run from inside the body reads the
         * delay there: leaving the old value behind makes every such task run a tick early, which is the
         * drift the timer hop was changed to stop, only moved one call further out.
         */
        void runBody() {
            delayMillis = 0L;
            body.accept(this);
            state = repeating ? ExecutionState.RUNNING : ExecutionState.FINISHED;
        }

        /**
         * Reports a throwing task and stops it.
         *
         * <p>Swallowed here on purpose: a task that throws must not kill the thread it ran on, which for
         * the game timer would silently stop every other scheduled task on the server.
         */
        void fail(Throwable pluginBug) {
            state = ExecutionState.CANCELLED;
            server.getLogger().log(java.util.logging.Level.SEVERE,
                            "[Forbric/Bukkit] scheduled task of " + name() + " threw", pluginBug);
            cancel();
        }

        private void invoke() {
            if (stopped()) {
                state = ExecutionState.CANCELLED;
                return;
            }
            markRunning();
            try {
                runBody();
            } catch (Throwable pluginBug) {
                fail(pluginBug);
            }
        }

        private String name() {
            return plugin == null ? "an unknown plugin" : plugin.getName();
        }

        @Override
        public Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return repeating;
        }

        @Override
        public CancelledState cancel() {
            // The four answers Bukkit distinguishes, and they are not interchangeable: a plugin that
            // schedules the next run from inside the task branches on this to know whether it just killed
            // the run it is standing in.
            if (state == ExecutionState.FINISHED) {
                // An one-shot that already ran: there is nothing left to cancel.
                return CancelledState.ALREADY_EXECUTED;
            }
            boolean wasCancelled = cancelled.getAndSet(true);
            if (wasCancelled) {
                return repeating ? CancelledState.NEXT_RUNS_CANCELLED_ALREADY
                                : CancelledState.CANCELLED_ALREADY;
            }
            if (state == ExecutionState.RUNNING && !repeating) {
                // Cancelled from inside its own body: the run is happening, only the rest is stopped.
                return CancelledState.CANCELLED_BY_CALLER;
            }
            return repeating ? CancelledState.NEXT_RUNS_CANCELLED : CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public ExecutionState getExecutionState() {
            return state;
        }

        @Override
        public String toString() {
            return "ForbricTask#" + id + "[" + name() + (repeating ? ", repeating]" : "]");
        }
    }

    /** The region scheduler: every region is the one game thread, so the coordinates are ignored. */
    public final class Region implements RegionScheduler {

        @Override
        public void execute(Plugin plugin, org.bukkit.World world, int chunkX, int chunkZ, Runnable runnable) {
            // Nothing to choose between: one thread ticks every region.
            if (isGameThread()) {
                runnable.run();
            } else {
                server.minecraft().execute(runnable);
            }
        }

        @Override
        public ScheduledTask run(Plugin plugin, org.bukkit.World world, int chunkX, int chunkZ,
                        Consumer<ScheduledTask> task) {
            return scheduleGame(plugin, task, 0L, 0L, false);
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, org.bukkit.World world, int chunkX, int chunkZ,
                        Consumer<ScheduledTask> task, long delayTicks) {
            return scheduleGame(plugin, task, delayTicks, 0L, false);
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, org.bukkit.World world, int chunkX, int chunkZ,
                        Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
            return scheduleGame(plugin, task, initialDelayTicks, periodTicks, true);
        }
    }

    /** The global scheduler: the same thread as every region, minus the coordinates. */
    public final class Global implements GlobalRegionScheduler {

        @Override
        public void execute(Plugin plugin, Runnable runnable) {
            if (isGameThread()) {
                runnable.run();
            } else {
                server.minecraft().execute(runnable);
            }
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task) {
            return scheduleGame(plugin, task, 0L, 0L, false);
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks) {
            return scheduleGame(plugin, task, delayTicks, 0L, false);
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelayTicks,
                        long periodTicks) {
            return scheduleGame(plugin, task, initialDelayTicks, periodTicks, true);
        }

        @Override
        public void cancelTasks(Plugin plugin) {
            ForbricSchedulers.this.cancelTasks(plugin);
        }
    }

    /**
     * The async scheduler: a real thread pool, because that is what "async" means on any server.
     *
     * <p>Delays here are in {@link TimeUnit}s the caller passes, not ticks — that is the API's shape, and
     * it is the one scheduler whose timing this bridge does not have to approximate.
     */
    public final class Async implements AsyncScheduler {

        @Override
        public ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> task) {
            Task scheduled = new Task(plugin, task, false);
            tasks.add(scheduled);
            asyncPool.execute(() -> runAsync(scheduled));
            return scheduled;
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delay,
                        TimeUnit unit) {
            Task scheduled = new Task(plugin, task, false);
            tasks.add(scheduled);
            asyncPool.schedule(() -> runAsync(scheduled), Math.max(0L, delay), unit);
            return scheduled;
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelay,
                        long period, TimeUnit unit) {
            Task scheduled = new Task(plugin, task, true);
            tasks.add(scheduled);
            asyncPool.scheduleAtFixedRate(() -> runAsync(scheduled), Math.max(0L, initialDelay),
                            Math.max(1L, period), unit);
            return scheduled;
        }

        @Override
        public void cancelTasks(Plugin plugin) {
            ForbricSchedulers.this.cancelTasks(plugin);
        }

        /** Runs on the pool thread, which is what the caller asked for by using the async scheduler. */
        private void runAsync(Task task) {
            if (task.stopped()) {
                return;
            }
            task.markRunning();
            try {
                task.runBody();
            } catch (Throwable pluginBug) {
                task.fail(pluginBug);
            }
        }
    }

    /** Cancels every task a plugin owns and drops it from the list. */
    private void cancelTasks(Plugin plugin) {
        for (Task task : tasks) {
            if (task.plugin == plugin) {
                task.cancel();
                tasks.remove(task);
            }
        }
    }

    /**
     * The entity scheduler, which on this bridge is the global one wearing an entity.
     *
     * <p>Folia runs each entity on the thread that owns its region, and the scheduler exists so a plugin
     * can reach that thread without knowing which one it is. There is one thread here, so every entity's
     * work lands in the same place — the region arguments are simply not consulted.
     *
     * <p>The one behaviour that is NOT the same is retirement. On Folia a task queued for an entity runs
     * only while the entity exists, and the {@code retired} runnable fires instead when it is gone. This
     * implementation keeps the same contract: {@link #execute} answers {@code false} once the entity has
     * been removed or the world has unloaded, and the delivered-task paths call {@code retired} rather
     * than running a body against a dead handle. A plugin that assumes the callback never fires would
     * otherwise leave a timer running against an entity that is gone.
     */
    public final class Entity implements io.papermc.paper.threadedregions.scheduler.EntityScheduler {

        private final net.minecraft.world.entity.Entity entity;

        Entity(net.minecraft.world.entity.Entity entity) {
            this.entity = entity;
        }

        @Override
        public boolean execute(Plugin plugin, Runnable runnable, Runnable retired, long delayTicks) {
            if (!alive()) {
                if (retired != null) {
                    retired.run();
                }
                return false;
            }
            Task task = new Task(plugin, ignored -> runnable.run(), false);
            tasks.add(task);
            scheduleEntity(task, delayTicks);
            return true;
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired) {
            return schedule(plugin, task, retired, 0L, 0L, false);
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired,
                        long delayTicks) {
            return schedule(plugin, task, retired, delayTicks, 0L, false);
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired,
                        long initialDelayTicks, long periodTicks) {
            return schedule(plugin, task, retired, initialDelayTicks, periodTicks, true);
        }

        private ScheduledTask schedule(Plugin plugin, Consumer<ScheduledTask> body, Runnable retired,
                        long delayTicks, long periodTicks, boolean repeating) {
            // A retired entity still gets a handle back rather than null: the API says the return value is
            // a task, and a plugin that keeps it to cancel later must not have to null-check it.
            Task task = new Task(plugin, body, repeating);
            tasks.add(task);
            if (!alive()) {
                // Not queued at all, and already finished as far as the handle is concerned. The retired
                // callback fires on the game thread, like every other callback this class delivers.
                task.markRunning();
                if (retired != null) {
                    onGameThread(retired::run);
                }
                task.cancel();
                return task;
            }
            if (repeating) {
                scheduleEntityRepeating(task, delayTicks, periodTicks);
            } else {
                scheduleEntity(task, delayTicks);
            }
            return task;
        }

        /** Queues a one-shot on the game timer, which is the one thread that ticks entities. */
        private void scheduleEntity(Task task, long delayTicks) {
            if (delayTicks <= 0L) {
                onGameThread(() -> runIfAlive(task, null));
            } else {
                gameTimer.schedule(() -> onGameThread(() -> runIfAlive(task, null)),
                                delayTicks * MILLIS_PER_TICK, TimeUnit.MILLISECONDS);
            }
        }

        /** Repeating tasks keep the same timer the region scheduler uses, for the same reason. */
        private void scheduleEntityRepeating(Task task, long delayTicks, long periodTicks) {
            long period = Math.max(1L, periodTicks) * MILLIS_PER_TICK;
            gameTimer.scheduleAtFixedRate(() -> onGameThread(() -> runIfAlive(task, null)),
                            Math.max(0L, delayTicks) * MILLIS_PER_TICK, period, TimeUnit.MILLISECONDS);
        }

        /**
         * Runs the body, unless the entity has gone since the task was queued.
         *
         * <p>The check is here rather than only at scheduling time because the interesting case is
         * exactly this one: a task queued while the entity was alive, for an entity that dies before the
         * delay elapses.
         */
        private void runIfAlive(Task task, Runnable retired) {
            if (!alive()) {
                // A live repeating task whose entity vanished: cancel it, or the timer keeps firing
                // forever against a handle that will never come back.
                task.markRunning();
                task.cancel();
                if (retired != null) {
                    retired.run();
                }
                return;
            }
            if (task.stopped()) {
                return;
            }
            task.markRunning();
            try {
                task.runBody();
            } catch (Throwable pluginBug) {
                task.fail(pluginBug);
            }
        }

        /** Whether the game still has this entity in a level. */
        private boolean alive() {
            return entity != null && !entity.isRemoved() && entity.level() != null;
        }

        private void onGameThread(Runnable work) {
            if (isGameThread()) {
                work.run();
            } else {
                server.minecraft().execute(work);
            }
        }
    }
}
