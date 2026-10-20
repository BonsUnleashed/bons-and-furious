package bons.furious.patch.subtleeffects;

import bons.pure.config.PureConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch subtleeffects_display_tick_biome_reuse (Subtle Effects 1.15.1-hotfix.1 for Minecraft 1.20.1,
 * client; our pack does not install Subtle Effects, the switch is for other packs, proven offline on the real classes).
 *
 * <p>Subtle Effects runs two managers for every ClientLevel.doAnimateTick sample (1,334 per client tick): FireflyManager.tick
 * asks level.getBiome(pos) once (after its config checks) and BiomeEffectsManager.tick asks it again for every registered
 * biome effect (mushroom spores, pollen, sculk dust with the default settings: three identical lookups per air sample),
 * each one vanilla's full biome zoom (eight fiddled cell distances, then the chunk's biome container).
 *
 * <p>With the switch on:
 * <ul>
 * <li>each manager's first lookup goes through vanilla_animate_tick_uniform_biome's AnimateTickBiome.biome (called through a
 *   method handle, the vanilla group is not touched): when the eight candidate cells lie in one single-biome chunk section it
 *   returns that section's Holder without the zoom, otherwise it calls level.getBiome(pos); that helper's 1.0.23 proof
 *   shows it returns the very Holder getBiome returns. It is used only while that switch is enabled in the config;
 *   otherwise level.getBiome(pos) is called as before;</li>
 * <li>inside one BiomeEffectsManager.tick call, the second and third effect reuse the first lookup's Holder for the same
 *   level and the same position object. Nothing between those lookups can change client biome data (it changes only
 *   through packets and chunk loading, handled on this thread between ticks; the managers only read the world and add
 *   particles), so every call returns the object getBiome would return. The remembered answer is dropped at the start of
 *   every BiomeEffectsManager.tick call and is used only by the first thread that runs it (the render thread); other
 *   threads always look up.</li>
 * </ul>
 * Random draws, particles and sounds are unchanged: biome lookups draw no random numbers. The managers' other biome lookup
 * (the surface position) is untouched.
 *
 * <p>-Dbons_and_furious.subtleeffectsDisplayTickBiomeReuse=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.subtleeffectsDisplayTickBiomeReuse.shadow=true returns level.getBiome(pos) every time and compares the
 * switch's answer by identity (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class DisplayTickBiomes {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.subtleeffectsDisplayTickBiomeReuse", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.subtleeffectsDisplayTickBiomeReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Lookups answered from the per-call memory and through the uniform-section helper (statistics; plain, racy). */
    public static long reused, uniform;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle UNIFORM = uniformHelper();
    private static volatile int layer = -1;            // vanilla_animate_tick_uniform_biome enabled in the config: 1 / 0
    private static volatile Thread owner;
    private static Level memoLevel;
    private static BlockPos memoPos;
    private static Holder<Biome> memoBiome;
    private static volatile boolean announced;

    private DisplayTickBiomes() {
    }

    private static MethodHandle uniformHelper() {
        try {
            Class<?> c = Class.forName("bons.furious.patch.vanilla.AnimateTickBiome", true, DisplayTickBiomes.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(c, "biome", MethodType.methodType(Holder.class, ClientLevel.class, BlockPos.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: subtleeffects_display_tick_biome_reuse cannot reach vanilla_animate_tick_uniform_biome's helper ({}); first lookups use level.getBiome", e.toString());
            return null;
        }
    }

    private static boolean layerOn() {
        int l = layer;
        if (l < 0) {
            boolean on;
            try {
                on = UNIFORM != null && PureConfig.isEnabled("vanilla_animate_tick_uniform_biome");
            } catch (Throwable t) {
                on = false;
            }
            layer = l = on ? 1 : 0;
        }
        return l == 1;
    }

    /** One fresh lookup: through the uniform-section helper when allowed, else level.getBiome(pos). */
    @SuppressWarnings("unchecked")
    static Holder<Biome> lookup(Level level, BlockPos pos) {
        if (level instanceof ClientLevel client && layerOn()) {
            try {
                Holder<Biome> h = (Holder<Biome>) (Holder<?>) UNIFORM.invokeExact(client, pos);
                uniform++;
                return h;
            } catch (Throwable t) {
                return level.m_204166_(pos);
            }
        }
        return level.m_204166_(pos);
    }

    /** In place of FireflyManager.tick's level.getBiome(pos). */
    public static Holder<Biome> firefly(Level level, BlockPos pos) {
        if (!enabled) return level.m_204166_(pos);
        if (SHADOW) return shadow(level, pos, lookup(level, pos));
        announce();
        return lookup(level, pos);
    }

    /** At the start of every BiomeEffectsManager.tick call (its first test): the previous call's answer is dropped. */
    public static boolean begin(boolean fullBlock) {
        if (Thread.currentThread() == owner) memoPos = null;
        return fullBlock;
    }

    /** In place of BiomeEffectsManager.tick's per-effect level.getBiome(pos). */
    public static Holder<Biome> biomeEffects(Level level, BlockPos pos) {
        if (!enabled) return level.m_204166_(pos);
        Thread t = Thread.currentThread();
        Thread o = owner;
        if (o == null) {
            synchronized (DisplayTickBiomes.class) {
                if (owner == null) owner = t;
                o = owner;
            }
        }
        Holder<Biome> h;
        if (t == o && memoPos == pos && memoLevel == level) {
            h = memoBiome;
            reused++;
        } else {
            h = lookup(level, pos);
            if (t == o) {
                memoLevel = level;
                memoPos = pos;
                memoBiome = h;
            }
        }
        if (SHADOW) return shadow(level, pos, h);
        announce();
        return h;
    }

    private static Holder<Biome> shadow(Level level, BlockPos pos, Holder<Biome> ours) {
        Holder<Biome> stock = level.m_204166_(pos);
        SHADOW_CHECKS.incrementAndGet();
        if (stock != ours) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: subtleeffects_display_tick_biome_reuse SHADOW MISMATCH {}: at {} getBiome gave {}, the switch {}", n, pos, stock, ours);
        }
        return stock;
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: subtleeffects_display_tick_biome_reuse: Subtle Effects' display-tick biome lookups reuse one answer per sample{}",
                    layerOn() ? " (uniform sections through vanilla_animate_tick_uniform_biome)" : "");
        }
    }
}
