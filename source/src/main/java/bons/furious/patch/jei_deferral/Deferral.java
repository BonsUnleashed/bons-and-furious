package bons.furious.patch.jei_deferral;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe;
import mezz.jei.library.recipes.RecipeManagerInternal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * jei_join_recipe_deferral (JEI 19.51.0.418): the deferred part of JEI's vanilla plugin. JEI 15.59 builds its anvil
 * enchanted-book recipes and its grindstone recipes (repair and disenchanting) by running a simulated anvil or grindstone
 * for every enchantable item at every enchantment level while the player joins a world. With the switch, JEI's cheap
 * anvil repair recipes are registered at its start as before and the rest are built in "lanes" (anvil enchanted-book,
 * grindstone), one recipe at a time in a 2 ms slice of every rendered frame once JEI is running, and added through the
 * same RecipeManagerInternal.addRecipes that JEI's registration uses. When every lane is done the job ends and costs nothing.
 * <p>
 * Order is kept exactly per recipe type: whatever the vanilla plugin registers itself for that type goes in at registration
 * as before; every later addition of that type (other plugins at registration, anyone at run time) is held and added, in its
 * original order, right after the lane's own recipes, which is where JEI's original list put them.
 * <p>
 * Everything here runs on the render thread: JEI start, JEI plugin callbacks and render ticks all do. When JEI is started
 * on another thread (Just Enough Threads does that), the switch steps aside for that start and JEI builds the recipes itself.
 * The frame listener is registered the first time the switch arms; afterwards it returns at once while nothing is pending.
 */
public final class Deferral {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    static final Object PAUSE = new Object();
    static final Object END = new Object();
    /** Render-thread time per frame spent on the deferred recipes, only until they are all in. */
    static final long SLICE_NANOS = 2_000_000L;
    private static final int MAX_BATCH = 256;

    /** next recipe, PAUSE (out of time before one was ready) or END */
    interface Producer {
        Object next(long deadlineNanos);
    }

    private static final class Lane {
        final String name;
        final RecipeType<?> type;
        final Producer producer;
        RecipeManagerInternal target;   // captured at the vanilla plugin's own addRecipes for this type
        boolean holding;
        final List<Object> held = new ArrayList<>();
        int added;

        Lane(String name, RecipeType<?> type, Producer producer) {
            this.name = name;
            this.type = type;
            this.producer = producer;
        }
    }

    private static boolean listening;
    private static boolean offThreadLogged;
    private static boolean armAnvil;
    private static boolean armGrindstone;
    private static boolean snapshotting;
    private static final List<Lane> lanes = new ArrayList<>();
    private static boolean ownAdd;
    private static boolean running;
    private static long runningSince;
    private static long workNanos;
    private static long longestSliceNanos;
    private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();
    private static long cpuNanos;
    private static int frames;
    private static int slicesOver16;
    private static int slicesOver50;
    private static int totalAdded;
    private static int finishedRuns;

    private Deferral() {
    }

    // --- the vanilla plugin's registration window --------------------------------------------------------------------

    /** VanillaPlugin.registerRecipes HEAD: a new JEI start. */
    public static void arm() {
        stop("JEI started again");
        if (!RenderSystem.isOnRenderThread()) {
            if (!offThreadLogged) {
                offThreadLogged = true;
                LOGGER.info("Bons and Furious: jei_join_recipe_deferral steps aside: JEI is starting on thread {}, not the render thread "
                        + "(Just Enough Threads?); JEI builds its anvil and grindstone recipes itself", Thread.currentThread().getName());
            }
            return;
        }
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(Deferral::onRenderTick);
        }
        armAnvil = true;
        armGrindstone = true;
    }

    /** VanillaPlugin.registerRecipes RETURN: from now on, later additions of a deferred type are held. */
    public static void disarm() {
        armAnvil = false;
        armGrindstone = false;
        for (Lane lane : lanes) {
            if (lane.target != null) {
                lane.holding = true;
            }
        }
    }

    public static boolean takeAnvilArm() {
        boolean armed = armAnvil;
        armAnvil = false;
        return armed;
    }

    public static boolean takeGrindstoneArm() {
        boolean armed = armGrindstone;
        armGrindstone = false;
        return armed;
    }

    public static boolean snapshotting() {
        return snapshotting;
    }

    public static void snapshotting(boolean value) {
        snapshotting = value;
    }

    public static void scheduleAnvil(Stream<IJeiAnvilRecipe> bookRecipes, int repairRecipes) {
        lanes.add(new Lane("anvil enchanted-book", RecipeTypes.ANVIL, fromIterator(bookRecipes.iterator())));
        LOGGER.info("Bons and Furious: JEI deferral: {} anvil repair recipes registered with JEI's start; the anvil enchanted-book "
                + "recipes follow in the background ({} ms per frame)", repairRecipes, SLICE_NANOS / 1_000_000L);
    }

    public static void scheduleGrindstone(Iterator<IJeiGrindstoneRecipe> repairRecipes, GrindstoneDisenchant disenchantRecipes) {
        lanes.add(new Lane("grindstone", RecipeTypes.GRINDSTONE, concat(fromIterator(repairRecipes), disenchantRecipes::next)));
        LOGGER.info("Bons and Furious: JEI deferral: the grindstone recipes follow in the background ({} ms per frame)",
                SLICE_NANOS / 1_000_000L);
    }

    /** ingredients.flatMap(mapper), walked one element of the mapped streams at a time (same elements, same order). */
    public static Stream<IJeiAnvilRecipe> oneAtATime(Iterator<ItemStack> ingredients, Function<ItemStack, Stream<IJeiAnvilRecipe>> mapper) {
        Iterator<IJeiAnvilRecipe> recipes = new Iterator<>() {
            private Iterator<IJeiAnvilRecipe> current = Collections.emptyIterator();

            @Override
            public boolean hasNext() {
                while (!current.hasNext()) {
                    if (!ingredients.hasNext()) {
                        return false;
                    }
                    Stream<IJeiAnvilRecipe> next = mapper.apply(ingredients.next());
                    current = next == null ? Collections.emptyIterator() : next.iterator();
                }
                return true;
            }

            @Override
            public IJeiAnvilRecipe next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(recipes, Spliterator.ORDERED), false);
    }

    private static Producer fromIterator(Iterator<?> it) {
        return deadline -> it.hasNext() ? it.next() : END;
    }

    private static Producer concat(Producer first, Producer second) {
        return new Producer() {
            private boolean inFirst = true;

            @Override
            public Object next(long deadline) {
                if (inFirst) {
                    Object r = first.next(deadline);
                    if (r != END) {
                        return r;
                    }
                    inFirst = false;
                }
                return second.next(deadline);
            }
        };
    }

    // --- every RecipeManagerInternal.addRecipes call -----------------------------------------------------------------

    /** @return true = this addition is held back (the caller cancels it) */
    public static boolean beforeAdd(RecipeManagerInternal manager, RecipeType<?> type, List<?> recipes) {
        if (ownAdd) {
            return false;
        }
        for (Lane lane : lanes) {
            if (!lane.type.equals(type)) {
                continue;
            }
            if (lane.target == null) {
                lane.target = manager;   // the vanilla plugin's own list for this type: goes in now, as before
                return false;
            }
            if (lane.holding && manager == lane.target) {
                lane.held.addAll(recipes);
                return true;
            }
            return false;
        }
        return false;
    }

    // --- JEI lifecycle -------------------------------------------------------------------------------------------------

    static void runtimeAvailable() {
        lanes.removeIf(lane -> {
            if (lane.target == null) {
                LOGGER.warn("Bons and Furious: JEI deferral: no {} registration was seen; those recipes are not added", lane.name);
                return true;
            }
            return false;
        });
        if (lanes.isEmpty()) {
            return;
        }
        running = true;
        runningSince = System.nanoTime();
        workNanos = 0;
        longestSliceNanos = 0;
        cpuNanos = 0;
        frames = 0;
        slicesOver16 = 0;
        slicesOver50 = 0;
        totalAdded = 0;
    }

    static void runtimeUnavailable() {
        stop("JEI stopped");
    }

    private static void stop(String why) {
        if (running && !lanes.isEmpty()) {
            LOGGER.info("Bons and Furious: JEI deferral: background job stopped with {} lane(s) unfinished ({})", lanes.size(), why);
        }
        lanes.clear();
        running = false;
        armAnvil = false;
        armGrindstone = false;
        snapshotting = false;
    }

    // --- the background job: a slice of every frame until done --------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void add(Lane lane, List<?> recipes) {
        ownAdd = true;
        try {
            lane.target.addRecipes((RecipeType) lane.type, (List) recipes);
        } finally {
            ownAdd = false;
        }
        lane.added += recipes.size();
        totalAdded += recipes.size();
    }

    private static void finish(Lane lane) {
        lane.holding = false;
        int others = lane.held.size();
        if (others > 0) {
            List<Object> copy = new ArrayList<>(lane.held);
            lane.held.clear();
            add(lane, copy);
            lane.added -= others;
        }
        LOGGER.info("Bons and Furious: JEI deferral: added {} {} recipes{}", lane.added, lane.name,
                others > 0 ? ", then " + others + " held " + lane.type.getUid() + " recipes of other plugins in their original order" : "");
    }

    static void onRenderTick(RenderFrameEvent.Post event) {
        if (!running) {
            return;
        }
        long start = System.nanoTime();
        long deadline = start + SLICE_NANOS;
        long cpuBefore = THREADS.getCurrentThreadCpuTime();
        long gcBefore = gcPauseMillis();
        int addedBefore = totalAdded;
        Lane lane = null;
        try {
            while (!lanes.isEmpty()) {
                lane = lanes.get(0);
                List<Object> batch = new ArrayList<>();
                Object r = PAUSE;
                while (System.nanoTime() < deadline) {
                    r = lane.producer.next(deadline);
                    if (r == END || r == PAUSE) {
                        break;
                    }
                    batch.add(r);
                    if (batch.size() == MAX_BATCH) {
                        add(lane, batch);
                        batch = new ArrayList<>();
                    }
                }
                if (!batch.isEmpty()) {
                    add(lane, batch);
                }
                if (r != END) {
                    break;
                }
                finish(lane);
                lanes.remove(0);
            }
        } catch (RuntimeException | LinkageError e) {
            if (lane != null) {
                LOGGER.error("Bons and Furious: JEI deferral: the {} recipes failed after {}; the rest of them are not shown", lane.name, lane.added, e);
                try {
                    finish(lane);
                } catch (RuntimeException | LinkageError e2) {
                    LOGGER.error("Bons and Furious: JEI deferral: could not add the held recipes of other plugins", e2);
                }
                lanes.remove(lane);
            }
        }
        long spent = System.nanoTime() - start;
        workNanos += spent;
        long cpuAfter = THREADS.getCurrentThreadCpuTime();
        if (cpuBefore >= 0 && cpuAfter >= 0) {
            cpuNanos += cpuAfter - cpuBefore;
        }
        frames++;
        longestSliceNanos = Math.max(longestSliceNanos, spent);
        if (spent > 16_000_000L) {
            slicesOver16++;
            long gc = gcPauseMillis() - gcBefore;
            if (spent > 50_000_000L) {
                slicesOver50++;
                LOGGER.info("Bons and Furious: JEI deferral: long slice {} ms ({}, {} recipes in it, {} ms of it GC pauses)",
                        String.format(Locale.ROOT, "%.1f", spent / 1e6), lane != null ? lane.name : "-", totalAdded - addedBefore, gc);
            }
        }
        if (lanes.isEmpty()) {
            running = false;
            finishedRuns++;
            LOGGER.info("Bons and Furious: JEI deferral: done, {} recipes in {} s ({} ms of render-thread work, {} ms of it CPU, over {} "
                    + "frames, longest slice {} ms, {} slices over 16 ms, {} over 50 ms); nothing runs per frame from here on", totalAdded,
                    String.format(Locale.ROOT, "%.1f", (System.nanoTime() - runningSince) / 1e9), workNanos / 1_000_000L,
                    cpuNanos / 1_000_000L, frames, String.format(Locale.ROOT, "%.1f", longestSliceNanos / 1e6), slicesOver16, slicesOver50);
        }
    }

    /** Stop-the-world GC pause time so far (G1: young/old collections; ZGC: the "Pauses" beans; concurrent work excluded). */
    private static long gcPauseMillis() {
        long total = 0;
        for (java.lang.management.GarbageCollectorMXBean bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = bean.getName();
            if (!name.contains("Concurrent") && !name.contains("Cycles")) {
                total += Math.max(0, bean.getCollectionTime());
            }
        }
        return total;
    }

    /** For tests and diagnostics: true while deferred recipes are scheduled or being added. */
    public static boolean busy() {
        return !lanes.isEmpty();
    }

    public static int finishedRuns() {
        return finishedRuns;
    }

    public static int addedLastRun() {
        return totalAdded;
    }
}
