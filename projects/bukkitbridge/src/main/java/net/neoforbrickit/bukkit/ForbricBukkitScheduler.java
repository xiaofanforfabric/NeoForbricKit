/*
 * Forbric Bukkit bridge — the classic {@code BukkitScheduler}, on a server that ticks on one thread.
 */
package net.neoforbrickit.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;

import net.neoforbrickit.bridge.util.ForbricLog;

/**
 * The scheduler every plugin written before 2023 asks for, and most still do.
 *
 * <h2>Why this class has to exist</h2>
 *
 * <p>Paper exposes TWO scheduler families. The classic {@link BukkitScheduler} has been there since 2013 and
 * is what {@code Bukkit.getScheduler()} returns; the region family ({@code RegionScheduler},
 * {@code GlobalRegionScheduler}, {@code AsyncScheduler}) arrived with Folia and was merged back into Paper,
 * so a modern server answers both. This bridge shipped only the region family, and that turned out to be the
 * single most damaging gap in it: a plugin whose {@code onEnable} calls {@code Bukkit.getScheduler()} died
 * during load, which is every plugin that schedules anything.
 *
 * <h2>Why Folia-aware plugins died too (the reason this was hard to see)</h2>
 *
 * <p>The community pattern for "supports Folia, still runs on Paper" is a reflective try/catch:
 *
 * <pre>{@code
 * try {
 *     Object s = server.getClass().getMethod("getGlobalRegionScheduler").invoke(server);
 *     s.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
 *      .invoke(s, plugin, task, delay);                    // walked this path
 *     return;
 * } catch (Throwable ignored) {}                          // swallowed the failure
 * Bukkit.getScheduler().runTaskLater(plugin, task, delay); // landed here instead
 * }</pre>
 *
 * <p>That fallback is CORRECT on a real Paper server, where both families work. Here it walked into this
 * bridge's {@code UnsupportedOperationException}, so the plugin reported a failed load and named a method it
 * never meant to call. The reason the lookup itself failed is separate and was fixed alongside this class:
 * the region schedulers used to be package-private nested types, and {@code Method.invoke} refuses a
 * package-private <em>declaring class</em> regardless of the method's own visibility.
 *
 * <h2>How the two families relate here</h2>
 *
 * <p>On a server with one tick thread, "the global region" and "the main thread" are the same thread, so the
 * classic family is a thin adapter over {@link ForbricSchedulers} rather than a second timer. Sharing one
 * timer is not just economy: a plugin that interleaves the two APIs would otherwise see its work fire out of
 * the order it was written, because two independent queues have no reason to agree on "next tick".
 *
 * <p>The one family that is genuinely different is async. Those methods go to a real thread pool on every
 * server, and this bridge already has one, so they forward to it.
 *
 * <h2>Ids</h2>
 *
 * <p>{@code BukkitScheduler} reports work by {@code int} id, and {@link ForbricSchedulers.Task} already
 * carries one. Both families therefore number out of the same counter, so an id shown by this API always
 * names the task this API created — the alternative, a second counter, makes a plugin that cancels by id
 * cancel the wrong thing when both families are in play.
 */
final class ForbricBukkitScheduler implements BukkitScheduler {

    /**
     * The length of a tick, 50 ms — the same unit {@link ForbricSchedulers} counts in.
     */
    private static final long MILLIS_PER_TICK = 50L;

    /**
     * A period of at least one tick, in milliseconds.
     *
     * <p>The classic API accepts {@code period = 0} and means "every tick", which is why the count is
     * clamped upward rather than rejected. It has to be clamped on BOTH sides of the conversion:
     * {@code gameTimer.scheduleAtFixedRate} rejects a non-positive period outright, and the async
     * scheduler below forwards to the game timer's sibling — passing 0 through multiplied by 50 still
     * gives 0, which throws {@code IllegalArgumentException} from inside the executor and surfaces as
     * the plugin's scheduling call failing for a reason no plugin author can read.
     */
    private static long periodMillis(long periodTicks) {
        return Math.max(1L, periodTicks) * MILLIS_PER_TICK;
    }

    /** The async pool's delay: 0 ticks means "now", so the clamp is at zero rather than one. */
    private static long delayMillis(long delayTicks) {
        return Math.max(0L, delayTicks) * MILLIS_PER_TICK;
    }

    private final ForbricSchedulers schedulers;

    ForbricBukkitScheduler(ForbricSchedulers schedulers) {
        this.schedulers = schedulers;
    }

    // ---------------------------------------------------------------------------------------------
    // The five handle-returning families, in their Runnable / Consumer / BukkitRunnable flavours.
    //
    // The Consumer overloads differ from the Runnable ones in a way worth naming: they run the plugin's
    // Consumer with the task's OWN handle, which is how a plugin cancels itself from inside a repeating
    // body ("stop after 10 runs"). That is why the Consumer form cannot simply wrap the body in a Runnable
    // at the call site -- the handle does not exist until after the task is created, and the task cannot be
    // created until it has a body. The indirection below resolves that: the Consumer is stored on a
    // one-element holder that the body reads when it finally runs.
    //
    // The BukkitRunnable flavours are the same thing with the body being the runnable's own run().
    // ---------------------------------------------------------------------------------------------

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) {
        return game(plugin, ignore -> task.run(), 0L, 0L, false);
    }

    @Override
    public void runTask(Plugin plugin, Consumer<? super BukkitTask> task) {
        gameConsumer(plugin, task, 0L, 0L, false);
    }

    @Override
    public BukkitTask runTask(Plugin plugin, BukkitRunnable task) {
        return gameFrom(plugin, task, 0L, 0L, false);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        return async(plugin, ignore -> task.run(), 0L, 0L, false);
    }

    @Override
    public void runTaskAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task) {
        asyncConsumer(plugin, task, 0L, 0L, false);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, BukkitRunnable task) {
        return asyncFrom(plugin, task, 0L, 0L, false);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        return game(plugin, ignore -> task.run(), delay, 0L, false);
    }

    @Override
    public void runTaskLater(Plugin plugin, Consumer<? super BukkitTask> task, long delay) {
        gameConsumer(plugin, task, delay, 0L, false);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, BukkitRunnable task, long delay) {
        return gameFrom(plugin, task, delay, 0L, false);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
        return async(plugin, ignore -> task.run(), delay, 0L, false);
    }

    @Override
    public void runTaskLaterAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay) {
        asyncConsumer(plugin, task, delay, 0L, false);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, BukkitRunnable task, long delay) {
        return asyncFrom(plugin, task, delay, 0L, false);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        return game(plugin, ignore -> task.run(), delay, period, true);
    }

    @Override
    public void runTaskTimer(Plugin plugin, Consumer<? super BukkitTask> task, long delay, long period) {
        gameConsumer(plugin, task, delay, period, true);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, BukkitRunnable task, long delay, long period) {
        return gameFrom(plugin, task, delay, period, true);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        return async(plugin, ignore -> task.run(), delay, period, true);
    }

    @Override
    public void runTaskTimerAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay,
                    long period) {
        asyncConsumer(plugin, task, delay, period, true);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, BukkitRunnable task, long delay, long period) {
        return asyncFrom(plugin, task, delay, period, true);
    }

    // ---------------------------------------------------------------------------------------------
    // The three deprecated int-returning families.
    //
    // Deleted from the API's documentation and kept in its bytecode, which is why they must still work:
    // a plugin compiled in 2014 calls these and nothing else. Each forwards to its modern equivalent, so
    // the behaviour a plugin gets is the CURRENT one -- a deprecated entry point is not a reason to
    // reproduce whatever the old implementation did wrong. That is also what Paper does with them.
    // ---------------------------------------------------------------------------------------------

    @Override
    @Deprecated
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay) {
        return runTaskLater(plugin, task, delay).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task, long delay) {
        return runTaskLater(plugin, task, delay).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task) {
        return runTask(plugin, task).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task) {
        return runTask(plugin, task).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        return runTaskTimer(plugin, task, delay, period).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleSyncRepeatingTask(Plugin plugin, BukkitRunnable task, long delay, long period) {
        return runTaskTimer(plugin, task, delay, period).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delay) {
        return runTaskLaterAsynchronously(plugin, task, delay).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task) {
        return runTaskAsynchronously(plugin, task).getTaskId();
    }

    @Override
    @Deprecated
    public int scheduleAsyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        return runTaskTimerAsynchronously(plugin, task, delay, period).getTaskId();
    }

    // ---------------------------------------------------------------------------------------------
    // Introspection.  Answers come from the one task registry, so the two families cannot disagree about
    // what is pending.
    // ---------------------------------------------------------------------------------------------

    @Override
    public void cancelTask(int taskId) {
        schedulers.cancelTaskById(taskId);
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        schedulers.cancelTasksOf(plugin);
    }

    /**
     * Whether a task is running right now.
     *
     * <p>True only while its body is executing, which is what the API means -- a task that is merely queued
     * answers false here and true from {@link #isQueued(int)}. The two are separate questions and a plugin
     * uses them for different things.
     */
    @Override
    public boolean isCurrentlyRunning(int taskId) {
        for (ForbricSchedulers.Task task : schedulers.pendingTasks()) {
            if (task.id == taskId) {
                return task.isRunningNow();
            }
        }
        return false;
    }

    @Override
    public boolean isQueued(int taskId) {
        return schedulers.isQueuedById(taskId);
    }

    /**
     * The async workers that are running.
     *
     * <p>The API's {@code BukkitWorker} describes a thread mid-task. This bridge's async pool runs a task on
     * a pool thread with no wrapper object around it, so there is nothing to report -- and an empty list is
     * the truthful answer, not a stub: the method asks "which of my worker wrappers are active", and there
     * are none, because this implementation has no such wrapper. A plugin using it for diagnostics prints
     * nothing rather than printing a lie.
     */
    @Override
    public List<BukkitWorker> getActiveWorkers() {
        return new ArrayList<>();
    }

    @Override
    public List<BukkitTask> getPendingTasks() {
        List<BukkitTask> pending = new ArrayList<>();
        for (ForbricSchedulers.Task task : schedulers.pendingTasks()) {
            pending.add(new Handle(task, true));
        }
        return pending;
    }

    /**
     * Runs a {@code Callable} on the game thread and returns a future with its value.
     *
     * <p>Calls from the game thread itself are answered inline by {@link ForbricSchedulers#callOnGameThread}:
     * submitting the work and then waiting for it would block the very thread that has to run it.
     */
    @Override
    public <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) {
        return schedulers.callOnGameThread(plugin, task);
    }

    /**
     * An {@code Executor} that runs everything on the game thread.
     *
     * <p>The name says "main thread", and on a server with one tick thread that is simply the game thread --
     * there is no per-region distinction to make, so no region has to be chosen.
     */
    @Override
    public Executor getMainThreadExecutor(Plugin plugin) {
        return command -> {
            if (command == null) {
                throw new IllegalArgumentException("[Forbric/Bukkit] a null command cannot be scheduled");
            }
            schedulers.scheduleGameNow(plugin, ignore -> command.run());
        };
    }

    // ---------------------------------------------------------------------------------------------
    // The plumbing the overloads above share.
    // ---------------------------------------------------------------------------------------------

    /** Queues a body on the game thread and wraps the resulting task as a {@code BukkitTask}. */
    private BukkitTask game(Plugin plugin, Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> body,
                    long delay, long period, boolean repeating) {
        ForbricSchedulers.Task task = (ForbricSchedulers.Task) (repeating
                        ? schedulers.scheduleGameRepeating(plugin, body, delay, period)
                        : schedulers.scheduleGameTask(plugin, body, delay));
        return new Handle(task, true);
    }

    /** The {@code Consumer<? super BukkitTask>} flavour: the plugin's consumer receives the live handle. */
    private void gameConsumer(Plugin plugin, Consumer<? super BukkitTask> body, long delay, long period,
                    boolean repeating) {
        // The handle does not exist when the task is created, and the task cannot exist without a body.
        // So the body reads the handle from this one-element array WHEN IT RUNS rather than being handed
        // one at construction time.
        //
        // An earlier version filled the array before the body could run, on the reasoning that the next
        // tick was the earliest the body could fire. That is false for `runTask` (and any 0-tick delay):
        // scheduleGame runs the body INLINE when the caller is already on the game thread, which is the
        // common case inside a command -- so the consumer received a null handle and a plugin calling
        // task.cancel() from it threw a NullPointerException out of its own first run.
        Handle[] handle = new Handle[1];
        Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> wrapped =
                        ignore -> body.accept(handle[0]);
        ForbricSchedulers.Task task = (ForbricSchedulers.Task) (repeating
                        ? schedulers.scheduleGameRepeating(plugin, wrapped, delay, period)
                        : schedulers.scheduleGameTask(plugin, wrapped, delay));
        handle[0] = new Handle(task, true);
    }

    /** The {@code BukkitRunnable} flavour: the runnable is bound to its handle before the body runs. */
    private BukkitTask gameFrom(Plugin plugin, BukkitRunnable task, long delay, long period, boolean repeating) {
        Handle[] handle = new Handle[1];
        Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> wrapped = ignore -> {
            bind(task, handle[0]);
            task.run();
        };
        ForbricSchedulers.Task scheduled = (ForbricSchedulers.Task) (repeating
                        ? schedulers.scheduleGameRepeating(plugin, wrapped, delay, period)
                        : schedulers.scheduleGameTask(plugin, wrapped, delay));
        handle[0] = new Handle(scheduled, true);
        return handle[0];
    }

    /**
     * Queues a body on the async pool and wraps the resulting task as a {@code BukkitTask}.
     *
     * <p>The delay and period arrive in TICKS — that is what every method of this API takes — while the
     * async scheduler speaks real time. The conversion is the same 50 ms the game thread uses, so an async
     * task and a sync task asked for the same delay fire on the same wall clock. Getting this wrong (passing
     * tick counts as milliseconds) makes every asynchronous delay 20 times too short.
     */
    private BukkitTask async(Plugin plugin, Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> body,
                    long delay, long period, boolean repeating) {
        ForbricSchedulers.Task task = (ForbricSchedulers.Task) (repeating
                        ? schedulers.asyncScheduler().runAtFixedRate(plugin, body, delayMillis(delay),
                                        periodMillis(period), java.util.concurrent.TimeUnit.MILLISECONDS)
                        : schedulers.asyncScheduler().runDelayed(plugin, body, delayMillis(delay),
                                        java.util.concurrent.TimeUnit.MILLISECONDS));
        return new Handle(task, false);
    }

    /**
     * The async {@code Consumer<? super BukkitTask>} flavour.
     *
     * <p>The handle is handed to the consumer on EVERY run rather than being filled in once before the
     * task starts. The array is empty until the scheduling call returns, and with {@code delay = 0} the
     * body can run before that happens — the consumer would then receive null, and a plugin that calls
     * {@code task.cancel()} on it throws a NullPointerException from inside its own first run. Reading
     * the array at run time is what makes the ordering irrelevant.
     */
    private void asyncConsumer(Plugin plugin, Consumer<? super BukkitTask> body, long delay, long period,
                    boolean repeating) {
        Handle[] handle = new Handle[1];
        Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> wrapped =
                        ignore -> body.accept(handle[0]);
        ForbricSchedulers.Task task = (ForbricSchedulers.Task) (repeating
                        ? schedulers.asyncScheduler().runAtFixedRate(plugin, wrapped, delayMillis(delay),
                                        periodMillis(period), java.util.concurrent.TimeUnit.MILLISECONDS)
                        : schedulers.asyncScheduler().runDelayed(plugin, wrapped, delayMillis(delay),
                                        java.util.concurrent.TimeUnit.MILLISECONDS));
        handle[0] = new Handle(task, false);
    }

    /** The async {@code BukkitRunnable} flavour. */
    private BukkitTask asyncFrom(Plugin plugin, BukkitRunnable task, long delay, long period, boolean repeating) {
        Handle[] handle = new Handle[1];
        Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> wrapped = ignore -> {
            bind(task, handle[0]);
            task.run();
        };
        ForbricSchedulers.Task scheduled = (ForbricSchedulers.Task) (repeating
                        ? schedulers.asyncScheduler().runAtFixedRate(plugin, wrapped, delayMillis(delay),
                                        periodMillis(period), java.util.concurrent.TimeUnit.MILLISECONDS)
                        : schedulers.asyncScheduler().runDelayed(plugin, wrapped, delayMillis(delay),
                                        java.util.concurrent.TimeUnit.MILLISECONDS));
        handle[0] = new Handle(scheduled, false);
        return handle[0];
    }

    /**
     * Hands a {@link BukkitRunnable} the handle it was scheduled under.
     *
     * <p>{@code BukkitRunnable} keeps its handle in a PRIVATE field that only its own package can fill, and
     * only from inside its own {@code runTask*}/{@code runTaskTimer*} methods — which this scheduler cannot
     * reach, because those methods call {@code Bukkit.getScheduler()} and land back here. So the field is
     * written reflectively, once, the first time the body runs.
     *
     * <p>Without it, {@code runnable.cancel()} throws {@code IllegalStateException("Not yet scheduled")} and
     * {@code runnable.getTaskId()} throws the same, which breaks the single most common use of this API:
     * a repeating {@code BukkitRunnable} that cancels ITSELF from inside {@code run()} after N passes.
     *
     * <p>A failure here is logged rather than thrown: the task itself is already queued and will run, and
     * losing the self-cancel convenience must not take down a plugin that is otherwise working.
     */
    private static void bind(BukkitRunnable runnable, BukkitTask handle) {
        if (handle == null) return;
        try {
            java.lang.reflect.Field field = BukkitRunnable.class.getDeclaredField("task");
            field.setAccessible(true);
            field.set(runnable, handle);
        } catch (ReflectiveOperationException | RuntimeException notBindable) {
            ForbricLog.warn("[Forbric/Bukkit] could not hand a BukkitRunnable its task handle: "
                            + notBindable);
        }
    }

    /**
     * A {@code BukkitTask} over a {@link ForbricSchedulers.Task}.
     *
     * <p>Not an implementation on {@code Task} itself, and it cannot be: {@code ScheduledTask.cancel()}
     * returns an enum describing what was cancelled while {@code BukkitTask.cancel()} returns void, and Java
     * refuses two methods that differ only in return type. The two APIs also want the same task under
     * different names -- {@code getOwningPlugin()} versus {@code getOwner()} -- so a small adapter is the
     * shape that fits, whatever the interfaces allow.
     *
     * <p>{@code sync} is passed in rather than asked of the task: it says which family created this handle,
     * which is a fact about the CALL, not about the task -- and a plugin branches on it to decide whether it
     * may touch the world.
     */
    static final class Handle implements BukkitTask {

        private final ForbricSchedulers.Task task;
        private final boolean sync;

        Handle(ForbricSchedulers.Task task, boolean sync) {
            this.task = task;
            this.sync = sync;
        }

        @Override
        public int getTaskId() {
            return task.id;
        }

        @Override
        public Plugin getOwner() {
            return task.owner();
        }

        @Override
        public boolean isSync() {
            return sync;
        }

        @Override
        public boolean isCancelled() {
            // "Cancelled" means the plugin called it off, not "already finished". A one-shot that ran to
            // completion is not cancelled, and a plugin that polls this while waiting for its work reports a
            // failure if the two are conflated.
            return task.cancelledOrNot();
        }

        @Override
        public void cancel() {
            // The enum the region API returns is dropped here on purpose: the classic API has no way to
            // report it, and a plugin that needs the detail should be using the region API.
            task.cancel();
        }

        @Override
        public String toString() {
            return "ForbricBukkitTask#" + task.id + "[" + getOwner().getName() + (sync ? ", sync" : ", async") + "]";
        }
    }
}
