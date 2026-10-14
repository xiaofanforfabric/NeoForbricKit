/*
 * Forbric Bukkit bridge — the three region schedulers, on a server that has one thread.
 */
package org.cn.xiaofan.bukkit;

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
 */
final class ForbricSchedulers {

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

    ForbricSchedulers(ServerImpl server) {
        this.server = server;
    }

    RegionScheduler region() {
        return new Region();
    }

    GlobalRegionScheduler global() {
        return new Global();
    }

    AsyncScheduler async() {
        return new Async();
    }

    /** Stops every timer, for a clean shutdown. */
    void shutdown() {
        gameTimer.shutdownNow();
        asyncPool.shutdownNow();
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
            task.runOnGameThread(server);
            return task;
        }

        Runnable dispatch = () -> task.runOnGameThread(server);
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

    /**
     * One scheduled task, and its state.
     *
     * <p>Holds its own cancelled flag rather than asking the executor: a task cancelled after it was queued
     * but before it ran must not run, and the executor has no way to say that. {@code repeating} is
     * reported to the plugin, which branches on it to decide whether the handle needs cancelling at all.
     */
    final class Task implements ScheduledTask {

        private final Plugin plugin;
        private final Consumer<ScheduledTask> body;
        private final boolean repeating;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final int id = TASK_IDS.incrementAndGet();
        private volatile ExecutionState state = ExecutionState.IDLE;

        Task(Plugin plugin, Consumer<ScheduledTask> body, boolean repeating) {
            this.plugin = plugin;
            this.body = body;
            this.repeating = repeating;
        }

        /** Runs the body on the game thread, skipping it when it was cancelled while queued. */
        void runOnGameThread(ServerImpl server) {
            if (stopped()) {
                state = ExecutionState.CANCELLED;
                return;
            }
            // The body is run here rather than re-dispatched: the timer thread is not the game thread, so
            // this hops onto it through the server's own queue when it has to.
            if (server.isPrimaryThread()) {
                invoke();
            } else {
                server.minecraft().execute(() -> invoke());
            }
        }

        /** True once {@link #cancel()} has been called, whatever the reason. */
        boolean stopped() {
            return cancelled.get();
        }

        /** Marks the body as about to run, for the state a plugin can read from inside it. */
        void markRunning() {
            state = ExecutionState.RUNNING;
        }

        /** Runs the body and records what happened, on whichever thread the caller chose. */
        void runBody() {
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
    final class Region implements RegionScheduler {

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
    final class Global implements GlobalRegionScheduler {

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
    final class Async implements AsyncScheduler {

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
}
