package bons.furious.patch.subtleeffects;

import einstein.subtle_effects.init.ModConfigs;
import einstein.subtle_effects.util.Util;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch subtleeffects_idle_fluid_hooks (Subtle Effects 1.15.1-hotfix.1 for Minecraft 1.20.1, client; our
 * pack does not install Subtle Effects, the switch is for other packs, proven offline on the real classes).
 *
 * <p>Water. Subtle Effects hooks WaterFluid.animateTick (every display-tick sample that lands in water): it reads the
 * fluid's falling flag, its height (a fluid lookup above), both steam settings, then calls
 * ParticleSpawnUtil.spawnHeatedWaterParticles, which reads the block light and, by spawn logic, the block below or the 27
 * fluids around, and finally reaches its private spawner, which does nothing unless "steaming water" or "boiling water"
 * is on. Both are off by default. With the switch on, the hook returns at its start while both settings are off:
 * everything it would have done is reads (world, light, settings) with no random number drawn, no particle and no state
 * change. Turning either setting on brings the hook back on the next sample (the settings are read every time).
 *
 * <p>Lava. Subtle Effects' LavaFluid.animateTick hook, for lava that is not falling, walks the 3 x 3 cells around the
 * sample (reading each one's fluid, block and the block above it) and returns, with nothing drawn or spawned, when it
 * reaches the centre cell and finds the lava's own top solid or not empty (lava under lava, under a block): every sample
 * inside a lava lake or sea. The four cells it reads before the centre change nothing. With the switch on, the same
 * centre test (Subtle Effects' own Util.isSolidOrNotEmpty, the same fluid and block tests) is made first, just before the
 * walk; when it holds the hook returns at once, otherwise the walk runs unchanged. Falling lava never reaches either.
 *
 * <p>-Dbons_and_furious.subtleeffectsIdleFluidHooks=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.subtleeffectsIdleFluidHooks.shadow=true never skips: SHADOW_CHECKS counts the hooks the switch would
 * have skipped (water and lava), and for lava SHADOW_MISMATCHES counts would-be skips after which Subtle Effects' hook still
 * reached its first random draw (expected 0; WARN for the first 20).
 */
public final class IdleFluids {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.subtleeffectsIdleFluidHooks", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.subtleeffectsIdleFluidHooks.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Hooks skipped (statistics; plain, racy). */
    public static long waterSkipped, lavaSkipped;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;
    /** Shadow mode, render thread: the current lava hook call would have been skipped. */
    private static boolean lavaShadowPending;

    private IdleFluids() {
    }

    /** HEAD of Subtle Effects' WaterFluid.animateTick hook: true = return at once (both steam settings off). */
    public static boolean skipWater() {
        if (!enabled || ModConfigs.BLOCKS.steam.steamingWater.get() || ModConfigs.BLOCKS.steam.boilingWater.get()) return false;
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            return false;
        }
        waterSkipped++;
        announce();
        return true;
    }

    /**
     * Just before the 3 x 3 walk of Subtle Effects' LavaFluid.animateTick hook (lava not falling): true = return at once,
     * because the walk would return at its centre cell. self = the LavaFluid the hook runs on (its isSame test).
     */
    public static boolean skipLava(Fluid self, Level level, BlockPos pos) {
        if (!enabled) return false;
        boolean centreReturns = Util.isSolidOrNotEmpty(level, pos.above())          // open top (the lake surface): walk
                && self.isSame(level.getFluidState(pos).getType())
                && !level.getBlockState(pos).isSolidRender(level, pos);
        if (SHADOW) {
            lavaShadowPending = centreReturns;
            if (centreReturns) SHADOW_CHECKS.incrementAndGet();
            return false;
        }
        if (!centreReturns) return false;
        lavaSkipped++;
        announce();
        return true;
    }

    /** Shadow mode: Subtle Effects' lava hook reached its first random draw (its value passes through unchanged). */
    public static double lavaDraw(double value) {
        if (SHADOW && lavaShadowPending) {
            lavaShadowPending = false;
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: subtleeffects_idle_fluid_hooks SHADOW MISMATCH {}: a lava hook the switch would have skipped went on to draw a random number", n);
        }
        return value;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: subtleeffects_idle_fluid_hooks: Subtle Effects' water steam hook is skipped while steaming and boiling water are off, and its lava spark hook returns early for covered lava");
        }
    }
}
