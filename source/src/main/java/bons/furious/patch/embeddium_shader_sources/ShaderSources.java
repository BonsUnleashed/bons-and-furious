package bons.furious.patch.embeddium_shader_sources;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.embeddium.impl.gl.shader.ShaderLoader;
import org.slf4j.Logger;

/**
 * Bons and Furious switch embeddium_shader_source_memo (Embeddium 0.3.31 on Minecraft 1.20.1; client). Idea: Collections Of
 * Optimizations (embeddium.cacheShaderSources), idea text only; the design below is ours.
 *
 * What Embeddium does. ShaderLoader.getShaderSource(name) reads /assets/NAMESPACE/shaders/PATH with
 * ShaderLoader.class.getClassLoader().getResourceAsStream every time a chunk shader program is compiled (each renderer reload
 * without a shader pack: world join, dimension change, F3+A, video settings; a program and its #import files are about seven
 * reads). Forge's module class loader cannot map a name that starts with '/' to a package, so it asks every mod jar and library
 * module for the file (cpw ModuleClassLoader.findResourceList: Jar.findFile on each) and takes the first match.
 *
 * What the switch does. The text of a shader that was read successfully is kept per ResourceLocation and returned on later
 * calls. Where it comes from cannot change while the game runs: the class loader, its module list and their order are fixed,
 * jar files cannot change underneath it, and resource packs do not take part in this lookup. A shader that is missing or
 * fails to read is never kept: every call reads again and throws Embeddium's own exception as before. The kept String has the
 * same characters (callers parse it into lines). With a shader pack (Oculus), Oculus's ShaderChunkRenderer hook returns before
 * any program is compiled, so this switch is idle there.
 *
 * Since 1.0.50.1: another mod may change what getShaderSource returns, and its answer for one name may depend on what is being
 * compiled. On Forge 1.20.1, Luxium 2.8.x returns other texts for block_layer_opaque while its water pass compiles; keeping the
 * first text made the water program miss its uniforms and the game stopped at world load (CurseForge comment 8746834). Luxium has
 * no NeoForge build; the same check covers any other mod that changes this loader. Before the first kept text is used, the mixins
 * merged into ShaderLoader are listed (their @MixinMerged annotations, as vanilla_camera_fluid_memo does for Camera); when any of
 * them is not ours the switch stands down for the session (one WARN line) and every call runs the original, that mod's change
 * included.
 *
 * -Dbons_and_furious.embeddiumShaderSourceMemo=false switches it off at run time.
 * -Dbons_and_furious.embeddiumShaderSourceMemo.shadow=true (verification runs only): every call reads the file as Embeddium
 * does and returns that; a kept text is compared with it: SHADOW_CHECKS, SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class ShaderSources {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.embeddiumShaderSourceMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.embeddiumShaderSourceMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Calls answered from a kept text. */
    public static final LongAdder HITS = new LongAdder();
    private static final ConcurrentHashMap<ResourceLocation, String> SOURCES = new ConcurrentHashMap<>();
    private static volatile boolean announced;
    private static final String MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";
    /** Since 1.0.50.1: the only mixin this switch was tested with in ShaderLoader (ours); any other turns the switch off at its first use. */
    public static final Set<String> TESTED_LOADER_MIXINS = Set.of("bons.furious.mixin.embeddium_shader_sources.ShaderLoaderSourceMemoMixin");
    /** Since 1.0.50.1: 0 = ShaderLoader's mixins not listed yet, 1 = only ours (the memo may answer), -1 = stood down for the session. */
    private static volatile int inventory;

    private ShaderSources() {
    }

    /** ShaderLoader.getShaderSource, wrapped. */
    public static String source(ResourceLocation name, Operation<String> original) {
        if (!enabled || name == null) return original.call(name);
        if (inventory <= 0 && !inventoryMatches()) return original.call(name);
        String known = SOURCES.get(name);
        if (known != null) {
            if (SHADOW) {
                String read = original.call(name);
                SHADOW_CHECKS.incrementAndGet();
                if (!known.equals(read)) {
                    long m = SHADOW_MISMATCHES.incrementAndGet();
                    if (m <= 20) LOGGER.warn("Bons and Furious: embeddium_shader_source_memo shadow mismatch #{}: {} read {} chars, kept {}", m, name, read == null ? -1 : read.length(), known.length());
                }
                return read;
            }
            HITS.increment();
            return known;
        }
        String read = original.call(name);   // a missing or unreadable shader throws here, every time (nothing is kept)
        if (read != null) {
            SOURCES.putIfAbsent(name, read);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: embeddium_shader_source_memo applies (Embeddium's chunk shader sources are read from the mod jars once)");
            }
        }
        return read;
    }

    /**
     * Since 1.0.50.1: true when every mixin merged into Embeddium's ShaderLoader is one this switch was tested with (ours); otherwise
     * the switch stands down for the session (WARN, original behaviour). Decided once, at the first call; ShaderLoader is loaded and
     * transformed by then (the call runs inside it).
     */
    static boolean inventoryMatches() {
        int v = inventory;
        if (v != 0) return v > 0;
        synchronized (ShaderSources.class) {
            if (inventory != 0) return inventory > 0;
            Set<String> unknown = new TreeSet<>();
            try {
                for (Method m : ShaderLoader.class.getDeclaredMethods()) {
                    for (Annotation a : m.getDeclaredAnnotations()) {
                        if (!a.annotationType().getName().equals(MERGED)) continue;
                        String mixin = String.valueOf(a.annotationType().getMethod("mixin").invoke(a));
                        if (!TESTED_LOADER_MIXINS.contains(mixin)) unknown.add(mixin);
                    }
                }
            } catch (Throwable t) {
                inventory = -1;
                LOGGER.warn("Bons and Furious: embeddium_shader_source_memo stands down: could not list the mixins merged into Embeddium's ShaderLoader ({}); "
                        + "shader sources are read every time", t.toString());
                return false;
            }
            if (unknown.isEmpty()) {
                inventory = 1;
                return true;
            }
            inventory = -1;
            LOGGER.warn("Bons and Furious: embeddium_shader_source_memo stands down: Embeddium's ShaderLoader carries mixins it was not tested with ({}); "
                    + "shader sources are read every time", String.join(", ", unknown));
            return false;
        }
    }

    /** Kept texts (for the offline proof). */
    public static int size() {
        return SOURCES.size();
    }

    /** Forgets every kept text (for the offline proof only). */
    public static void clearForTests() {
        SOURCES.clear();
    }
}
