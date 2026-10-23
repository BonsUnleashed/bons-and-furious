package bons.furious.patch.cablefacades;

import bons.furious.mixin.cablefacades.DimensionDataStorageCacheAccessor;
import com.portingdeadmods.cable_facades.data.CableFacadeSavedData;
import com.portingdeadmods.cable_facades.utils.ClientFacadeManager;
import com.portingdeadmods.cable_facades.utils.FacadeUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cablefacades_no_facade_fast_exit (Cable Facades 2.1.3 for Minecraft 1.20.1, client AND server;
 * our pack does not install Cable Facades, the switch is for other packs, proven offline on the real merged classes).
 *
 * <p>Cable Facades hooks twelve of the hottest block-state methods: seven handlers on BlockStateBase (shape, collision
 * shape, occlusion shape, light block, skylight, solid render, onRemove) and five methods it adds to BlockState (the
 * appearance, light emission, face sturdiness, visual shape and light block overrides). Every call first reads its
 * thread-local recursion guard, sets it, looks the facade up (FacadeUtils.getFacadeData: on a ServerLevel the level's
 * SavedData through DimensionDataStorage.computeIfAbsent with a capturing lambda, a new ChunkPos and two map reads; for any
 * other getter ClientFacadeManager's map) and clears the guard again: three ThreadLocal operations and a lookup on every
 * light update, collision test, pathfinding and meshing call. The light engine runs it twice per getLightBlock (the
 * BlockState override and the BlockStateBase handler each have their own guard).
 *
 * <p>With the switch on, the guard read answers "held" (Boolean.TRUE, its guard-held branch) when no facade can exist for
 * that getter, so the hook goes straight to its guard-held branch, which in every one of the twelve hooks is exactly what
 * the full path does when the lookup finds nothing: the handlers return without touching their CallbackInfo, the overrides
 * call the same vanilla/Forge method (super or getBlock().getX(asState(), ...)) the null-lookup path calls. "No facade can
 * exist" is:
 * <ul>
 * <li>a ServerLevel getter: the level's CableFacadeSavedData, read from DimensionDataStorage's cache map (the first map
 *   read computeIfAbsent itself makes), exists and isEmpty() (no chunk map at all: getFacadeData's chunk-map read is null
 *   for every position). Before the first lookup of a level has loaded or created its SavedData (cache entry null) the
 *   full path runs, so loading happens exactly where it happened; an entry of another type also runs the full path
 *   (stock's cast fails there, as before). The cache is read on every call, nothing is kept;</li>
 * <li>any other getter (client levels, render regions, pathfinding / worldgen regions, light chunk getters): the
 *   ClientFacadeManager map is empty (getFacadeData answers from it; getAppearance always reads it).</li>
 * </ul>
 * Chunk maps are never removed from a SavedData, so a level that ever held a facade keeps the full path (conservative); the
 * client returns to the fast path when its map is empty again. Cable Facades' own client hooks on Block.shouldRenderFace are
 * not changed (their RETURN injection allocates its CallbackInfoReturnable before any code of ours could run, and their body
 * is a single thread-local read and map read: nothing to save).
 *
 * <p>Exactness and threads: decisions read the same map (or the same storage map) the full path reads, so every answer
 * matches a moment of the full path. One documented difference, only possible while the first facade is being placed into
 * an empty store by another thread: a BlockState override's own guard stays set during its vanilla call in the full path
 * (nested calls of the five BlockState overrides on that thread take the vanilla branch), while with the switch each nested
 * override decides for itself and could see the new facade one call earlier. Cable Facades' placement then updates, relights
 * and re-meshes that position itself, so the settled result is the same. BlockStateBase handlers hold their guard only
 * around the lookup, so they have no such window.
 *
 * <p>-Dbons_and_furious.cablefacadesNoFacadeFastExit=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.cablefacadesNoFacadeFastExit.shadow=true runs the full path every time and, wherever the switch would
 * have answered "held", checks that the full path's lookup finds nothing (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the
 * first 20).
 */
public final class CfFastExit {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cablefacadesNoFacadeFastExit", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cablefacadesNoFacadeFastExit.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Guard reads answered "held" (statistics for probes; plain field, racy by design). */
    public static long fastExits;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private CfFastExit() {
    }

    /** True when FacadeUtils.getFacadeData(getter, pos) is certain to be null for every position. */
    public static boolean noFacadeFor(BlockGetter getter) {
        if (getter instanceof ServerLevel level) {
            Object data = ((DimensionDataStorageCacheAccessor) level.getDataStorage()).bons$cfCache().get(CableFacadeSavedData.ID);
            return data instanceof CableFacadeSavedData saved && saved.isEmpty();
        }
        return ClientFacadeManager.isEmpty();
    }

    /** In place of the recursion guard's ThreadLocal.get() in a hook that looks up FacadeUtils.getFacadeData(getter, pos). */
    public static Object guard(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        if (!enabled || !noFacadeFor(getter)) return guard.get();
        if (SHADOW) {
            shadow(FacadeUtils.getFacadeData(getter, pos) == null, getter, pos);
            return guard.get();
        }
        return held();
    }

    /** In place of the guard read in BlockState.getAppearance, which always looks up ClientFacadeManager.get(pos). */
    public static Object guardClient(ThreadLocal<?> guard, BlockPos pos) {
        if (!enabled || !ClientFacadeManager.isEmpty()) return guard.get();
        if (SHADOW) {
            shadow(ClientFacadeManager.get(pos) == null, null, pos);
            return guard.get();
        }
        return held();
    }

    private static Object held() {
        fastExits++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cablefacades_no_facade_fast_exit: Cable Facades' block-state hooks skip their guard and lookup while no facade can exist");
        }
        return Boolean.TRUE;
    }

    private static void shadow(boolean nothingThere, BlockGetter getter, BlockPos pos) {
        SHADOW_CHECKS.incrementAndGet();
        if (!nothingThere) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) {
                LOGGER.warn("Bons and Furious: cablefacades_no_facade_fast_exit SHADOW MISMATCH {}: a facade was found at {} in {} where the switch would have skipped the lookup",
                        n, pos, getter);
            }
        }
    }
}
