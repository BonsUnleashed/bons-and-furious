package bons.furious.patch.forge_mod_resources_index;

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
 * forge_mod_resources_namespace_index: the other mods' mixins this switch was tested with on the classes that decide what a
 * Forge mod-file pack lists and which namespaces it declares (Forge's PathPackResources and ResourcePackLoader$1, Forge's
 * ResourcePackLoader that creates them, vanilla's PathPackResources whose static listPath Forge's listing calls) and on
 * DelegatingPackResources itself. The switch skips delegates whose namespaces do not hold the one listed; that is exact only
 * while each delegate lists nothing outside the namespaces it declares, which another mixin could change. Checked once, at the
 * first eligible listing, from Mixin's record of the mixins applied (Mixins.getMixinsForClass): every mixin of another mod must
 * be in TESTED with the same class print (every method's code, Fingerprint.of, and annotations, which hold the injection
 * targets), and the classes a tested mixin relies on (ALSO) must print the same too. Our own mixins are skipped.
 *
 * Tested (client and server mixin indexes 2026-10-19): ModernFix 5.27.77 ForgePathPackResourcesMixin (listResources answered at
 * HEAD from its per-pack PackResourcesCacheEngine; getNamespaces from that cache once it exists), Fusion 1.3.14a
 * ForgePathPackResourcesMixin, ResourcePackLoaderMixin and PathPackResourcesMixin (overrides folder: its namespaces are added to
 * getNamespaces together with its listing), CreativeCore 2.12.39 PathPackResourcesAccessor and DelegatingPackResourcesAccessor
 * (accessors and invokers).
 */
public final class Neighbours {
    static final List<String> TARGETS = List.of("net.minecraftforge.resource.PathPackResources", "net.minecraftforge.resource.ResourcePackLoader$1",
            "net.minecraftforge.resource.ResourcePackLoader", "net.minecraft.server.packs.PathPackResources",
            "net.minecraftforge.resource.DelegatingPackResources");

    /** Tested class (a mixin, or a class a tested mixin relies on) -> its print (see print). */
    public static final Map<String, String> TESTED = Map.of(
            "org.embeddedt.modernfix.common.mixin.perf.resourcepacks.ForgePathPackResourcesMixin", "7800fcc502c6099a9ca9bd4855eede0e89876698398397d6278b8cecfb5888aa",
            "org.embeddedt.modernfix.resources.PackResourcesCacheEngine", "03359dfd347bcf88e89900e29000e49a1b7b5cd0b4325d96704404f94958c221",
            "org.embeddedt.modernfix.resources.PackResourcesCacheEngine$Node", "bffd202ed30e37d71f834e1fd77b208c805cf7f0cd084d762dc6fb11390e0bb7",
            "com.supermartijn642.fusion.mixin.forge.ForgePathPackResourcesMixin", "b36865bbedd3fa2afc0634f583e844742a93fc2556be89ea3d71a595d293f67c",
            "com.supermartijn642.fusion.mixin.forge.ResourcePackLoaderMixin", "c208e353185c8fc0794fea71c7637c2311d60b40574f4c7b0eeb0168b47098a5",
            "com.supermartijn642.fusion.mixin.PathPackResourcesMixin", "07d396593cbc58f0f445c4309ec1cacebf7c2ef33ba9c9d4f182e0064a152135",
            "team.creative.creativecore.mixin.PathPackResourcesAccessor", "bedf09257fa982b1a8227f5650bb12782bbebc4013e05369f7847140ef841d6a",
            "team.creative.creativecore.mixin.DelegatingPackResourcesAccessor", "91df23405bb2c6b7106c634323d7079652f85ff2f691396edd19b42c235356f7");

    /** A tested mixin -> the classes whose code its effect depends on (they must print as tested as well). */
    public static final Map<String, List<String>> ALSO = Map.of(
            "org.embeddedt.modernfix.common.mixin.perf.resourcepacks.ForgePathPackResourcesMixin",
            List.of("org.embeddedt.modernfix.resources.PackResourcesCacheEngine", "org.embeddedt.modernfix.resources.PackResourcesCacheEngine$Node"));

    private Neighbours() {
    }

    /** Why the switch steps aside, or null when every other mod's mixin on those classes is a tested one. */
    static String whyNot() {
        for (String target : TARGETS) {
            Set<IMixinInfo> mixins;
            try {
                mixins = Mixins.getMixinsForClass(target);
            } catch (Throwable t) {
                return null;   // no Mixin environment (offline tools): nothing was applied by anyone else
            }
            for (IMixinInfo m : mixins) {
                String name = m.getClassName();
                if (ours(name)) continue;
                String problem = problem(name, read(name));
                if (problem != null) return problem;
                for (String also : ALSO.getOrDefault(name, List.of())) {
                    problem = problem(also, read(also));
                    if (problem != null) return problem;
                }
            }
        }
        return null;
    }

    static boolean ours(String name) {
        return name.startsWith("bons.furious.") || name.startsWith("bons.pure.") || name.startsWith("agentcraft.");
    }

    /** Null when the class is a tested one and unchanged; otherwise what is wrong with it. Public for the offline proof. */
    public static String problem(String name, ClassNode node) {
        String expected = TESTED.get(name);
        if (expected == null) return name + " changes how mod files list resources and was not tested with this switch";
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
