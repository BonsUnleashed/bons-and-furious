package bons.furious.patch.basalt_guards;

import bons.furious.guard.Fingerprint;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch basalt_guards_vanilla_first (Minecraft 1.21.1, tested build NeoForge 21.1.252, with the guards
 * of L_Ender's Cataclysm 1.21.1-3.33 and YUNG's API 1.21.1-NeoForge-5.1.9; on 1.20.1 Forge 47.4.16 with seven mods'
 * guards; logical server incl. the integrated server and Distant Horizons' world generation; Mojang names).
 *
 * What the pack does. BasaltColumnsFeature.canPlaceAt is vanilla's two-read test: the spot must be air or lava ocean
 * (isAirOrLavaOcean) and the block below must be neither air nor one of CANNOT_PLACE_ON; the MutableBlockPos is moved
 * down and back up. The basalt-delta column search calls it at every step of
 * its downward probes. On 1.20.1 seven mods inject at its HEAD to keep basalt out of their structures (Cataclysm,
 * Repurposed Structures, YUNG's Better Dungeons, YUNG's Better Nether Fortresses, Bygone Nether, Moog's Structure Lib,
 * Nether Expansion; on 1.21.1 the known ones are Cataclysm's and YUNG's API's): each looks up the chunk, the structure
 * registry and its structure tag and asks the structure manager for starts at the spot (Nether Expansion read the two
 * blocks for its sanctum tag), and the only answer any of them can give is setReturnValue(false); all of them run before
 * vanilla's test on every probe.
 *
 * What the switch does. One MixinExtras @WrapMethod around canPlaceAt (it wraps the HEAD callbacks too) first
 * evaluates vanilla's test with vanilla's own helper and list; when that says no it returns false without running the
 * original. Otherwise the original runs unchanged (guards, then vanilla's body). Why identical: the original answers
 * true only if no guard says no AND vanilla's test says yes; a guard can only answer no; so whenever vanilla says no the
 * answer is false whatever the guards would do. Vanilla's reads stay within the column the feature has already read and
 * restore the position. Exact variant, documented: for positions vanilla rejects, the guards' read-only lookups do not
 * run, so a guard's own log line (Bygone Nether warns when the chunk is not at STRUCTURE_REFERENCES yet) or exception
 * appears only for positions vanilla accepts: reached less often, never more. World output is the same.
 *
 * Guarded at run time (once, on first use): every method another mod merged into BasaltColumnsFeature (@MixinMerged)
 * must be one of the known handlers (KNOWN), by mixin class, handler name, descriptor and the fingerprint of the handler
 * as it is in that mod's mixin class file (read from the jar); anything else (an unknown handler, an @Overwrite, a
 * redirect into vanilla's body, a changed guard) makes the switch step aside and the original run on every call.
 *
 * -Dbons_and_furious.basaltGuardsVanillaFirst=false switches it off at run time.
 * -Dbons_and_furious.basaltGuardsVanillaFirst.shadow=true (verification runs only): the original always runs; when
 * vanilla's test said no, its answer must be false (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 *
 * Ported to 1.21.1: canPlaceAt, isAirOrLavaOcean, CANNOT_PLACE_ON and the declared methods of BasaltColumnsFeature are
 * unchanged. KNOWN lists only guard builds read for 1.21.1 (the 1.20.1 builds cannot load on NeoForge 1.21.1):
 * L_Ender's Cataclysm 1.21.1-3.33 and YUNG's API 1.21.1-NeoForge-5.1.9 (on 1.21.1 YUNG's API carries the guard YUNG's
 * Better Dungeons and Better Nether Fortresses had in their own jars on 1.20.1). Both are the 1.20.1 shape: leave unless
 * the level is a WorldGenRegion, then MixinUtils.isPositionInTaggedStructure (reads pos through SectionPos.of and
 * BoundingBox.isInside only, looks up chunks, references and starts) and setReturnValue(false) inside a tagged
 * structure; never true, never a write. Jaden's Nether Expansion 2.4.1 has no basalt guard on 1.21.1. A 1.21.1 build of
 * Repurposed Structures, Bygone Nether or Moog's Structure Lib that carries a guard is not read yet: the switch then
 * steps aside (one WARN) and every guard runs as before. NeoForge's Mixin (0.8.7, Fabric's fork) names a merged handler
 * handler$uid$modid$name and drops a leading "modid_" from the handler's own name; both that form and Forge's are
 * matched (mergedName), and the mixin class, descriptor and code fingerprint are checked as before.
 */
public final class BasaltGuards {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.basaltGuardsVanillaFirst", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.basaltGuardsVanillaFirst.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final String HANDLER_DESC = "(Lnet/minecraft/world/level/LevelAccessor;ILnet/minecraft/core/BlockPos$MutableBlockPos;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

    /** A guard known to answer only false and only read: mixin class, handler name, fingerprint of its code. */
    public record Known(String mixin, String handler, String print) {}

    /** 1.21.1 builds read for the port; the fingerprint is Fingerprint.of(the handler in its mixin class file). */
    public static final List<Known> KNOWN = List.of(
            new Known("com.github.L_Ender.cataclysm.mixin.NoBasaltColumnsInStructuresMixin", "cataclysm_noBasaltColumnsInStructures",
                    "0959dcb39e3bc2c6148c11c0a7535b534be9220dc9fbe3069a56d72d9723c70e"),        // L_Ender's Cataclysm 1.21.1-3.33
            new Known("com.yungnickyoung.minecraft.yungsapi.mixin.NoBasaltColumnsInStructuresMixin", "yungsapi_noBasaltColumnsInStructures",
                    "0fa25edd6b883142c5ce3a7d6c6fdbec108e44a9172cf4e19efa2fb63330b1ef"));       // YUNG's API 1.21.1-NeoForge-5.1.9

    /** Vanilla BasaltColumnsFeature's own methods (names of declared methods, constructors excluded; the same six on 1.20.1 and 1.21.1). */
    static final Set<String> VANILLA = Set.of("place", "placeColumn", "findSurface", "canPlaceAt", "findAir", "isAirOrLavaOcean");

    private static volatile Boolean decision;
    /** What the run-time check found (the harness prints it): one line per merged method. */
    public static volatile List<String> report = List.of();

    private BasaltGuards() {
    }

    /** True when the switch may answer before the guards (switch on, every merged handler known). */
    public static boolean vanillaFirst(Class<?> feature) {
        if (!enabled) return false;
        Boolean d = decision;
        if (d == null) decision = d = check(feature);
        return d;
    }

    private static synchronized boolean check(Class<?> feature) {
        if (decision != null) return decision;
        List<String> lines = new ArrayList<>();
        String problem = null;
        int known = 0;
        try {
            for (Method m : feature.getDeclaredMethods()) {
                String name = m.getName();
                MixinMerged merged = m.getAnnotation(MixinMerged.class);
                if (merged == null) {
                    if (VANILLA.contains(name) || name.contains("$mixinextras$") || name.startsWith("mixinextras$")) continue;
                    lines.add("unknown method " + name);
                    problem = problem != null ? problem : "unknown method " + name;
                    continue;
                }
                String mixin = merged.mixin();
                if (mixin.startsWith("bons.furious.mixin.")) continue;
                Known k = match(mixin, name);
                String desc = Type.getMethodDescriptor(m);
                if (k == null || !HANDLER_DESC.equals(desc)) {
                    lines.add("foreign " + mixin + " " + name + desc + ": not a known guard");
                    problem = problem != null ? problem : mixin + " (" + name + ") is not a known basalt guard";
                    continue;
                }
                String print = handlerPrint(k, feature);
                if (!k.print().equals(print)) {
                    lines.add("guard " + mixin + "." + k.handler() + " fingerprint " + print + " (tested " + k.print() + ")");
                    problem = problem != null ? problem : mixin + " is not the tested build";
                    continue;
                }
                lines.add("guard " + mixin + "." + k.handler() + " known");
                known++;
            }
        } catch (Throwable t) {
            problem = "BasaltColumnsFeature could not be inspected (" + t + ")";
        }
        report = List.copyOf(lines);
        boolean ok = problem == null;
        if (ok) LOGGER.info("Bons and Furious: basalt_guards_vanilla_first applies (vanilla's basalt test runs before {} structure guard(s)){}", known,
                SHADOW ? " - shadow verification on" : "");
        else LOGGER.warn("Bons and Furious: basalt_guards_vanilla_first steps aside: {}; every guard runs as before", problem);
        decision = ok;
        return ok;
    }

    private static Known match(String mixin, String mergedName) {
        for (Known k : KNOWN) if (k.mixin().equals(mixin) && mergedName(k.handler(), mergedName)) return k;
        return null;
    }

    /**
     * True when {@code merged} is the name Mixin gives the handler {@code handler} in the target class: Forge's Mixin
     * (0.8.5) merges it as handler$uid$handler; NeoForge's (0.8.7, Fabric's fork) as handler$uid$sourceid$handler, and
     * when the handler's name starts with the source id and '_' or '$' that prefix is dropped, so
     * cataclysm_noBasaltColumnsInStructures becomes handler$uid$cataclysm$noBasaltColumnsInStructures. Both forms match.
     */
    static boolean mergedName(String handler, String merged) {
        if (merged.equals(handler) || merged.endsWith("$" + handler)) return true;
        for (int i = 1; i < handler.length() - 1; i++) {
            char c = handler.charAt(i);
            if (c == '_' || c == '$') return merged.endsWith("$" + handler.substring(0, i) + "$" + handler.substring(i + 1));
        }
        return false;
    }

    /** The fingerprint of a guard handler as it is in its mod's mixin class file (null when the file is not found). */
    public static String handlerPrint(Known k, Class<?> feature) throws Exception {
        String res = k.mixin().replace('.', '/') + ".class";
        byte[] bytes = null;
        for (ClassLoader cl : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), feature.getClassLoader(), BasaltGuards.class.getClassLoader()}) {
            if (cl == null) continue;
            try (InputStream in = cl.getResourceAsStream(res)) {
                if (in != null) {
                    bytes = in.readAllBytes();
                    break;
                }
            }
        }
        if (bytes == null) return null;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        for (MethodNode m : node.methods) if (m.name.equals(k.handler()) && m.desc.equals(HANDLER_DESC)) return Fingerprint.of(m);
        return null;
    }

    /** Shadow mode: vanilla said no, so the original must have said no. */
    public static void shadow(boolean vanilla, boolean original) {
        if (vanilla) return;
        SHADOW_CHECKS.incrementAndGet();
        if (original) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: basalt_guards_vanilla_first shadow mismatch #{}: vanilla's test said no but canPlaceAt said yes", m);
        }
    }

    /** For tests: forget the run-time decision. */
    public static void resetDecision() {
        decision = null;
    }

    /** For tests: the decision with a given set of merged (mixin, name, descriptor) entries and fingerprints. */
    public static String classify(List<String[]> merged, Map<String, String> prints) {
        for (String[] e : merged) {
            if (e[0] == null) {
                if (VANILLA.contains(e[1]) || e[1].contains("$mixinextras$") || e[1].startsWith("mixinextras$")) continue;
                return "unknown method " + e[1];
            }
            if (e[0].startsWith("bons.furious.mixin.")) continue;
            Known k = match(e[0], e[1]);
            if (k == null || !HANDLER_DESC.equals(e[2])) return "not a known guard: " + e[0];
            if (!k.print().equals(prints.get(e[0]))) return "changed guard: " + e[0];
        }
        return null;
    }
}
