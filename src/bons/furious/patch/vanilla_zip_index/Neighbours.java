package bons.furious.patch.vanilla_zip_index;

import bons.furious.guard.Fingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * vanilla_zip_pack_listing_index: the other mods' mixins on FilePackResources this switch was tested with. The switch hands
 * vanilla's own filter loop a smaller list of entries, so it is exact only while that loop is vanilla's: a mixin that changed
 * the loop's test (a redirect of startsWith, say) could keep an entry outside the bucket. Checked once, at the first listing,
 * from Mixin's record of the mixins applied to the class (Mixins.getMixinsForClass): every mixin of another mod must be in
 * TESTED with the same class print (print: every method's code, Fingerprint.of, and its annotations, which hold the injection
 * targets). Our own mixins are skipped. Anything else makes the switch step aside, logged once.
 *
 * Tested (client mixin index 2026-10-19): Fusion 1.3.14a FilePackResourcesMixin (a @ModifyVariable of the output at HEAD that
 * wraps it, walking the zip itself for its overrides folder; getNamespaces RETURN; getResource HEAD) and CreativeCore 2.12.39
 * FilePackResourcesAccessor (an invoker of the ZipFile getter and an accessor). ModernFix 5.27.77's FilePackResourcesMixin
 * (stability level BETA, off at the default GA level) answers listResources from its own index; the switch leaves the method to it.
 */
public final class Neighbours {
    static final String TARGET = "net.minecraft.server.packs.FilePackResources";
    static final String MODERNFIX_ZIP_INDEX = "org.embeddedt.modernfix.common.mixin.perf.resourcepacks.FilePackResourcesMixin";

    /** Tested mixin class -> its print (see print). */
    public static final Map<String, String> TESTED = Map.of(
            "com.supermartijn642.fusion.mixin.FilePackResourcesMixin", "24e7d499bb89939f8f7b1e6b9ff19539ce8480cbf04f32be5fe85c5893797b7e",
            "team.creative.creativecore.mixin.FilePackResourcesAccessor", "e9b6b61ceac05128fbe5a55929d9d0d73b58e3bf6bfbb6a6bdb2d461a4f9afb8");

    private Neighbours() {
    }

    /** Why the switch steps aside, or null when every other mod's mixin on FilePackResources is a tested one. */
    static String whyNot() {
        Set<IMixinInfo> mixins;
        try {
            mixins = Mixins.getMixinsForClass(TARGET);
        } catch (Throwable t) {
            return null;   // no Mixin environment (offline tools): nothing was applied by anyone else
        }
        for (IMixinInfo m : mixins) {
            String name = m.getClassName();
            if (ours(name)) continue;
            if (MODERNFIX_ZIP_INDEX.equals(name)) return "ModernFix's FilePackResourcesMixin (stability level BETA) indexes zipped packs itself";
            String problem = problem(name, read(name));
            if (problem != null) return problem;
        }
        return null;
    }

    static boolean ours(String name) {
        return name.startsWith("bons.furious.") || name.startsWith("bons.pure.") || name.startsWith("agentcraft.");
    }

    /** Null when the mixin class is a tested one and unchanged; otherwise what is wrong with it. Public for the offline proof. */
    public static String problem(String name, ClassNode node) {
        String expected = TESTED.get(name);
        if (expected == null) return name + " is applied to FilePackResources and was not tested with this switch";
        if (node == null) return name + " could not be read to compare it with the tested build";
        if (!expected.equals(print(node))) return name + " differs from the build this switch was tested with";
        return null;
    }

    /** SHA-256 over every method's name, descriptor, annotations and code fingerprint, sorted by name and descriptor. */
    public static String print(ClassNode node) {
        List<String> lines = new ArrayList<>();
        for (MethodNode m : node.methods) {
            StringBuilder sb = new StringBuilder(m.name).append(m.desc).append(' ').append(Fingerprint.of(m));
            annotations(sb, m.visibleAnnotations);
            annotations(sb, m.invisibleAnnotations);
            lines.add(sb.toString());
        }
        Collections.sort(lines);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : d) hex.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void annotations(StringBuilder sb, List<AnnotationNode> list) {
        if (list == null) return;
        for (AnnotationNode a : list) {
            sb.append(" @");
            value(sb, a);
        }
    }

    private static void value(StringBuilder sb, Object v) {
        if (v instanceof AnnotationNode a) {
            sb.append(a.desc).append('(');
            if (a.values != null) for (int i = 0; i + 1 < a.values.size(); i += 2) {
                sb.append(a.values.get(i)).append('=');
                value(sb, a.values.get(i + 1));
                sb.append(',');
            }
            sb.append(')');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (Object x : l) {
                value(sb, x);
                sb.append(',');
            }
            sb.append(']');
        } else if (v instanceof String[] e) {
            sb.append(e[0]).append('.').append(e[1]);   // enum value
        } else if (v instanceof Type t) {
            sb.append(t.getDescriptor());
        } else {
            sb.append(v);
        }
    }

    private static ClassNode read(String name) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(name);
        } catch (Throwable t) {
            return null;
        }
    }
}
