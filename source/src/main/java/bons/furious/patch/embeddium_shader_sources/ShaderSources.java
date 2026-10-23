package bons.furious.patch.embeddium_shader_sources;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.resources.ResourceLocation;
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

    private ShaderSources() {
    }

    /** ShaderLoader.getShaderSource, wrapped. */
    public static String source(ResourceLocation name, Operation<String> original) {
        if (!enabled || name == null) return original.call(name);
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

    /** Kept texts (for the offline proof). */
    public static int size() {
        return SOURCES.size();
    }

    /** Forgets every kept text (for the offline proof only). */
    public static void clearForTests() {
        SOURCES.clear();
    }
}
