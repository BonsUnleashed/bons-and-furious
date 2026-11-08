package bons.furious.patch.vanilla_frame_budget;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

/**
 * The frame-budget helper of Bons and Furious (since 1.0.49; Minecraft 1.21.1 on NeoForge 21.1.252, client only): one
 * helper behind three trade-off switches, each OFF by default and each with its own visible effect.
 *
 * <ul>
 * <li>vanilla_frame_task_budget: Minecraft.runTick runs BlockableEventLoop.runAllTasks once per frame (inside
 *     {@code if (renderLevel)}, before the client ticks), which handles EVERY task queued since the last frame (network
 *     packets, other threads' execute calls) before the frame is drawn. With the switch on, while a world is being played
 *     (a level and a player, no resource-reload overlay, no ReceivingLevelScreen - the joining and dimension-change screen,
 *     which NeoForge's per-dimension transition screens extend - and no LevelLoadingScreen), the drain runs pollTask (the
 *     call runAllTasks itself makes) until 0.5 ms + 0.02 ms x (tasks queued when the drain starts) have passed. At least
 *     one task always runs, never more than stock would run (stock stops only when the queue is empty: Minecraft.shouldRun
 *     is always true), and the tasks left stay in the queue in their order for the next frame's drain (or for any other
 *     drain of the same queue, which also takes them from the head). When the world is left, Minecraft.disconnect(Screen,
 *     boolean) drops every queued task with dropAllTasks while a packet listener exists: the tasks this frame's drain left
 *     behind - the ones stock would already have run - run first, unless disconnect is called from inside a task (a
 *     disconnect packet), where stock drops them too. Minecraft.clearClientLevel (the server sends the client back to
 *     configuration) drops no task, so the tasks left run in the next drains, in their order.</li>
 * <li>vanilla_light_poll_budget: ClientLevel.pollLightUpdates (once per frame from LevelRenderer.renderLevel) runs
 *     j = (n &lt; 1000 ? max(10, n / 10) : n) queued light jobs of n (chunk light setup of received chunks, light
 *     updates, unloads). With the switch on its loop also stops once 0.5 ms + 0.02 ms x n have passed (at least one job
 *     per frame, never more than j); the stock loop itself runs, only its poll() call answers "empty" once the time is up.</li>
 * <li>vanilla_ambient_particle_defer: Minecraft.tick calls ClientLevel.animateTick (667 x 2 random block positions around
 *     the player: ambient block, fluid and biome particles) once per client tick while the game is not paused and the
 *     level's tick rate runs normally. With the switch on the call is recorded with its exact arguments and made at the
 *     start of the next Minecraft.runTick instead (the next frame), with the same position; a second recorded call before
 *     that runs the first one at once (the number of calls never changes); a call recorded for a level that is no longer
 *     the client's level (or without a player or game mode) is dropped.</li>
 * </ul>
 *
 * What the player can notice (the "Trade-off (off by default)" sentences): packets and other main-thread tasks may be
 * handled up to a few frames later (in order) while a backlog is cleared; newly received chunks may get their light a few
 * frames later; ambient particles of a tick spawn one frame later and join the particle engine at the next client tick
 * (up to 50 ms), and the level's random numbers are drawn at another moment, so client-side effects that use the same
 * random draw other values. No packet is sent, nothing is saved, nothing changes on the server.
 *
 * Runtime flags (the emergency off; the config keys decide): -Dbons_and_furious.vanillaFrameTaskBudget=false,
 * -Dbons_and_furious.vanillaLightPollBudget=false, -Dbons_and_furious.vanillaAmbientParticleDefer=false. The counters below
 * are for test rigs (the render thread writes them, any thread may read them reflectively).
 */
public final class FrameBudget {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The budget: half a millisecond per drain plus 0.02 ms for every item waiting when the drain starts. */
    public static final long BASE_NANOS = 500_000L, PER_ITEM_NANOS = 20_000L;

    public static volatile boolean tasksEnabled = flag("vanillaFrameTaskBudget");
    public static volatile boolean lightEnabled = flag("vanillaLightPollBudget");
    public static volatile boolean ambientEnabled = flag("vanillaAmbientParticleDefer");

    /** Rig counters (render thread writes): budgeted drains, drains that left tasks, tasks left in all. */
    public static volatile long taskDrains, taskDrainsDeferred, tasksDeferred, tasksFlushedAtLeave;
    /** Rig counters: budgeted light polls, polls stopped by the budget, light jobs left at those stops. */
    public static volatile long lightPolls, lightPollsDeferred, lightJobsDeferred;
    /** Rig counters: animateTick calls moved to the next frame, run early (second call in one frame), dropped. */
    public static volatile long ambientDeferred, ambientRunEarly, ambientDropped;

    private static boolean announcedTasks, announcedLight, announcedAmbient;

    // ---- render-thread state
    /** Tasks the last budgeted drain left queued in this frame (0 after a full drain or a stock drain). */
    private static int backlog;
    private static boolean lightBudgeted;
    private static long lightDeadline;
    private static int lightPolled;
    private static ClientLevel pendingLevel;
    private static int pendingX, pendingY, pendingZ;

    private FrameBudget() {
    }

    private static boolean flag(String camelName) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camelName, "true"));
    }

    /** The budget for a drain that starts with {@code queued} items waiting: 0.5 ms + 0.02 ms x queued. */
    public static long budgetNanos(int queued) {
        return BASE_NANOS + PER_ITEM_NANOS * Math.max(0, queued);
    }

    /** A world is being played: a level and a player, no resource-reload overlay, no level receiving / loading screen. */
    public static boolean active(Minecraft mc) {
        if (mc == null || mc.level == null || mc.player == null || mc.getOverlay() != null) return false;
        Screen screen = mc.screen;
        return !(screen instanceof ReceivingLevelScreen) && !(screen instanceof LevelLoadingScreen);
    }

    /** Implemented by ReentrantBlockableEventLoop through our invoker mixin: its protected runningTask(). */
    public interface RunningTaskProbe {
        boolean bons$runningTask();
    }

    // ---------------------------------------------------------------------------------------------- vanilla_frame_task_budget

    /** Minecraft.runTick's runAllTasks() call. */
    public static void runAllTasks(Minecraft mc, Operation<Void> original) {
        if (!tasksEnabled || !active(mc)) {
            backlog = 0;
            original.call(mc);
            return;
        }
        int left = drainBudgeted(mc, mc.getPendingTasksCount());
        backlog = left;
        taskDrains++;
        if (left > 0) {
            taskDrainsDeferred++;
            tasksDeferred += left;
            if (!announcedTasks) {
                announcedTasks = true;
                LOGGER.info("Bons and Furious: vanilla_frame_task_budget applies (a frame handles queued packets and tasks for at most "
                        + "0.5 ms + 0.02 ms per queued task; {} of them wait for the next frame, in order)", left);
            }
        }
    }

    /**
     * The budgeted drain: pollTask() (stock's own step) until the queue is empty or the budget for {@code queued} items has
     * passed after a task; at least one task runs when one is queued. Returns the number of tasks still queued when the
     * budget stopped it, 0 when the queue ran empty.
     */
    public static int drainBudgeted(BlockableEventLoop<?> loop, int queued) {
        long deadline = System.nanoTime() + budgetNanos(queued);
        while (loop.pollTask()) {
            if (System.nanoTime() - deadline >= 0) return loop.getPendingTasksCount();
        }
        return 0;
    }

    /** Minecraft.disconnect(Screen, boolean)'s dropAllTasks() call: first the tasks this frame's budgeted drain left (stock ran them). */
    public static void beforeDropAllTasks(Minecraft mc, Operation<Void> original) {
        int n = backlog;
        backlog = 0;
        if (n > 0 && tasksEnabled && (Object) mc instanceof RunningTaskProbe probe && !probe.bons$runningTask()) {
            while (n-- > 0 && mc.pollTask()) {
                tasksFlushedAtLeave++;
            }
        }
        original.call(mc);
    }

    // ---------------------------------------------------------------------------------------------- vanilla_light_poll_budget

    /** ClientLevel.pollLightUpdates' first call, lightUpdateQueue.size(): the drain's start and its budget. */
    public static int lightQueueSize(Deque<Runnable> queue, Operation<Integer> original) {
        int size = original.call(queue);
        Minecraft mc = Minecraft.getInstance();
        lightBudgeted = lightEnabled && mc != null && mc.isSameThread() && active(mc);
        if (lightBudgeted) {
            lightDeadline = System.nanoTime() + budgetNanos(size);
            lightPolled = 0;
            lightPolls++;
        }
        return size;
    }

    /** ClientLevel.pollLightUpdates' lightUpdateQueue.poll() call: "empty" (null, the loop's own end) once the budget is used. */
    public static Object lightPoll(Deque<Runnable> queue, Operation<Object> original) {
        if (lightBudgeted && lightPolled > 0 && System.nanoTime() - lightDeadline >= 0) {
            lightBudgeted = false;
            int left = queue.size();
            if (left > 0) {
                lightPollsDeferred++;
                lightJobsDeferred += left;
                if (!announcedLight) {
                    announcedLight = true;
                    LOGGER.info("Bons and Furious: vanilla_light_poll_budget applies (a frame sets up received light for at most "
                            + "0.5 ms + 0.02 ms per queued job; {} jobs wait for the next frame, in order)", left);
                }
                return null;
            }
        }
        lightPolled++;
        return original.call(queue);
    }

    // ---------------------------------------------------------------------------------------------- vanilla_ambient_particle_defer

    /**
     * Minecraft.tick's level.animateTick(x, y, z) call: true = make it now (switch off), false = recorded for the start of
     * the next frame. A call still recorded from an earlier tick of the same frame is made first, so no call is lost.
     */
    public static boolean animateTickNow(ClientLevel level, int x, int y, int z) {
        if (!ambientEnabled) return true;
        ClientLevel older = pendingLevel;
        if (older != null) {
            pendingLevel = null;
            if (older == level) {
                ambientRunEarly++;
                older.animateTick(pendingX, pendingY, pendingZ);
            } else {
                ambientDropped++;
            }
        }
        pendingLevel = level;
        pendingX = x;
        pendingY = y;
        pendingZ = z;
        ambientDeferred++;
        if (!announcedAmbient) {
            announcedAmbient = true;
            LOGGER.info("Bons and Furious: vanilla_ambient_particle_defer applies (ambient block particles of a client tick spawn at the start of the next frame)");
        }
        return false;
    }

    /** Start of Minecraft.runTick: the recorded call, if the client still plays that level with a player and a game mode. */
    public static void runDeferredAnimateTick(Minecraft mc) {
        ClientLevel level = pendingLevel;
        if (level == null) return;
        pendingLevel = null;
        if (mc.level != level || mc.player == null || mc.gameMode == null) {
            ambientDropped++;
            return;
        }
        ProfilerFiller profiler = mc.getProfiler();
        profiler.push("animateTick");
        try {
            level.animateTick(pendingX, pendingY, pendingZ);
        } finally {
            profiler.pop();
        }
    }

    /** For the offline proof: whether a call is recorded, and its arguments. */
    public static Object pendingForTest() {
        ClientLevel level = pendingLevel;
        return level == null ? null : new Object[] {level, pendingX, pendingY, pendingZ};
    }

    /** For the offline proof: the backlog the last budgeted drain left. */
    public static int backlogForTest() {
        return backlog;
    }
}
