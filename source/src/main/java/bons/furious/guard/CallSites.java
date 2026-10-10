package bons.furious.guard;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The calls whose argument one of our @ModifyArg handlers replaces, read once every mod's mixins are in the class.
 *
 * NoiseChunk's constructor hands its wrap visitor to NoiseRouter.mapAll and, for the final density, to
 * DensityFunction.mapAll. terrain_density_memo and terrain_final_density_reuse put their own visitor around it and
 * vanilla_noise_wrap_presize prepares the wrap table at the same point. The fingerprint guard reads the class before any
 * mixin is applied, so it cannot see another mod working on the same two calls:
 *
 *  - another @ModifyArg on the same call changes the visitor before or after ours (on Minecraft 1.21.1 / NeoForge: the
 *    density-function compiler of C2ME 0.4, on both calls): our visitor may be wrapped, replaced or thrown away;
 *  - a @Redirect replaces the call (Generator Accelerator 1.6's NoiseChunkSessionMixin on the final density; on Forge
 *    1.20.1 the c2meforge port's compiler): our visitor would be handed to code that does not map the router the way the
 *    visitor expects.
 *
 * postApply runs when all mixins of all mods have been applied. For each of the three hooks it walks from our handler
 * call to the call it feeds. The switch keeps working only when nothing but our own handlers sits between the
 * constructor's own wrap visitor (the invokedynamic that creates it) and the original call. Otherwise the switch stands
 * down: its handler hands the visitor through untouched (MemoizingVisitor.wrap, FinalDensityReuse.pass2,
 * WrapPresize.presized read the flags below), the mapAll hooks of the two switches never see one of our visitors and do
 * nothing, and the constructor behaves as if the switch were off. One log line names the other mod's mixin.
 *
 * A foreign hook listed in PASS_THROUGH (verified in game for one exact mod version to hand our visitor on to the call,
 * e.g. Bye Pregen's redirect that maps the final density with the visitor it is given) does not count as foreign: the
 * switch keeps working and logs one line naming it.
 *
 * The three handlers no longer name an argument index and do not require their injection, so Mixin cannot refuse them
 * when the call has been redirected (an explicit index 0 pointed at the redirect handler's first argument, the
 * NoiseRouter: InvalidInjectionException at world load with c2meforge 0.2.0-forge.9.8 on Forge 1.20.1).
 *
 * Bye Pregen 1.1.3.0 (Minecraft 1.21.1) changes the final density with a @ModifyExpressionValue that runs after the call
 * returns, so it is not a hook between our handler and the call: next to it the switches keep working without a
 * pass-through entry. (Its Forge 1.20.1 version 1.1.2.4 redirects the call and is listed in the Forge line's PASS_THROUGH;
 * that entry names a Forge build and is not carried here.)
 */
public final class CallSites {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String NOISE_ROUTER = "net/minecraft/world/level/levelgen/NoiseRouter";
    private static final String DENSITY_FUNCTION = "net/minecraft/world/level/levelgen/DensityFunction";
    private static final String VISITOR = "Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;";
    /** probe mixin -> switch, method, call owner, call name, call desc, our handler. */
    private static final Map<String, String[]> PROBES = Map.of(
            "agentcraft.terrain.mixin.NoiseChunkMixin", new String[] {"terrain_density_memo", "<init>", NOISE_ROUTER, "mapAll",
                    "(" + VISITOR + ")L" + NOISE_ROUTER + ";", "acTerrain$memoizeRouter"},
            "bons.furious.mixin.terrain.NoiseChunkWrapPresizeMixin", new String[] {"vanilla_noise_wrap_presize", "<init>", NOISE_ROUTER, "mapAll",
                    "(" + VISITOR + ")L" + NOISE_ROUTER + ";", "bons$presizeWrapTable"},
            "bons.furious.mixin.terrain.NoiseChunkFinalDensityMixin", new String[] {"terrain_final_density_reuse", "<init>", DENSITY_FUNCTION, "mapAll",
                    "(" + VISITOR + ")L" + DENSITY_FUNCTION + ";", "bons$secondPassVisitor"});
    /**
     * Foreign hooks on these calls that are known to hand our visitor on to the call, verified in game for exactly the
     * named mod id and version: next to them the switch did its full work and the terrain was the same as without that
     * mod. They do not make the switch step aside; any other version, and any other foreign hook, still does.
     */
    // None verified on Minecraft 1.21.1 / NeoForge yet (see the class comment for Bye Pregen 1.1.3.0, which needs none).
    private static final Map<String, String> PASS_THROUGH = Map.of();
    private static final Map<String, String> RESULT = new ConcurrentHashMap<>();   // switch -> "" (the call is ours alone) or why not
    private static final Map<String, String> PASSED = new ConcurrentHashMap<>();   // switch -> the pass-through hook it met
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** True = the switch stands down: its handler hands the visitor (or the table) through untouched. */
    public static volatile boolean densityMemoForeign, finalDensityForeign, wrapPresizeForeign;

    private CallSites() {}

    /** IMixinConfigPlugin.postApply, for every mixin of ours; only the three probe mixins are looked at. */
    public static void afterApply(String mixinClass, ClassNode target) {
        String[] p = PROBES.get(mixinClass);
        if (p == null) return;
        String why;
        try {
            why = inspect(target, p);
        } catch (Throwable t) {
            why = "the call could not be read (" + t + ")";
        }
        RESULT.put(p[0], why);
        boolean foreign = !why.isEmpty();
        switch (p[0]) {
            case "terrain_density_memo" -> densityMemoForeign = foreign;
            case "terrain_final_density_reuse" -> finalDensityForeign = foreign;
            default -> wrapPresizeForeign = foreign;
        }
        String call = p[2].substring(p[2].lastIndexOf('/') + 1) + ".mapAll in " + simple(target.name);
        String passed = PASSED.get(p[0]);
        if (!foreign && passed == null) LOGGER.debug("Bons and Furious: {}: the visitor of {} is changed by this switch alone", p[0], call);
        else if (!foreign) {
            if (LOGGED.add(p[0] + "|pass")) LOGGER.info("Bons and Furious: {} keeps working next to {}: that mod's hook on {} hands the "
                    + "visitor on (verified for this version)", p[0], passed, call);
        } else if (LOGGED.add(p[0])) LOGGER.info("Bons and Furious: {} steps aside: {}; the visitor of {} is left to it", p[0], why, call);
    }

    /** "" when the switch's call is changed by our handlers alone; the reason it stands down otherwise; null if never read. */
    public static String result(String key) {
        return RESULT.get(key);
    }

    private static String inspect(ClassNode target, String[] p) {
        Map<String, MethodNode> methods = new HashMap<>();
        for (MethodNode m : target.methods) methods.put(m.name + m.desc, m);
        boolean found = false;
        for (MethodNode m : target.methods) {
            if (!m.name.equals(p[1])) continue;
            for (AbstractInsnNode n : m.instructions) {
                if (!(n instanceof MethodInsnNode mi) || !mi.owner.equals(target.name) || !mi.name.contains("$" + p[5])) continue;
                found = true;
                String why = after(target, methods, mi, p);
                if (why == null) why = before(target, methods, mi, p);
                if (why != null) return why;
            }
        }
        return found ? "" : "its hook is not in " + simple(target.name) + "." + p[1];
    }

    /** From our handler call forward: only our own handlers (or a verified pass-through hook) may come before the call. */
    private static String after(ClassNode target, Map<String, MethodNode> methods, MethodInsnNode from, String[] p) {
        for (AbstractInsnNode n = from.getNext(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode mi) {
                if (ours(target, methods, mi)) continue;
                if (mi.owner.equals(p[2]) && mi.name.equals(p[3]) && mi.desc.equals(p[4])) return null;
                if (passesThrough(target, methods, mi, p)) {
                    // a redirect of the call itself: our visitor reaches the call through it; anything else: keep walking
                    if (mi.desc.equals("(L" + p[2] + ";" + VISITOR + ")L" + p[2] + ";")) return null;
                    continue;
                }
                return describe(target, methods, mi) + " also changes that call";
            }
            if (n instanceof InvokeDynamicInsnNode || n instanceof JumpInsnNode) return "other code sits between this switch's hook and the call";
            int op = n.getOpcode();
            if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN || op == Opcodes.ATHROW) break;
        }
        return "the call this switch prepares is no longer there";
    }

    /** From our handler call backward: only our own handlers may sit between it and the constructor's own visitor. */
    private static String before(ClassNode target, Map<String, MethodNode> methods, MethodInsnNode from, String[] p) {
        for (AbstractInsnNode n = from.getPrevious(); n != null; n = n.getPrevious()) {
            if (n instanceof InvokeDynamicInsnNode) return null;
            if (n instanceof MethodInsnNode mi) {
                if (ours(target, methods, mi) || passesThrough(target, methods, mi, p)) continue;
                return describe(target, methods, mi) + " changes the visitor first";
            }
            if (n instanceof JumpInsnNode) return "other code sits before this switch's hook";
        }
        return "the visitor's origin was not found";
    }

    /** A foreign hook listed in PASS_THROUGH whose mod is installed at exactly the verified id and version. */
    private static boolean passesThrough(ClassNode target, Map<String, MethodNode> methods, MethodInsnNode mi, String[] p) {
        String mixin = mi.owner.equals(target.name) ? mergedBy(methods.get(mi.name + mi.desc)) : null;
        String wanted = mixin == null ? null : PASS_THROUGH.get(mixin);
        if (wanted == null || !wanted.equals(modIdVersion(mixin))) return false;
        PASSED.put(p[0], wanted + " (" + mixin.substring(mixin.lastIndexOf('.') + 1) + ")");
        return true;
    }

    private static boolean ours(ClassNode target, Map<String, MethodNode> methods, MethodInsnNode mi) {
        String mixin = mi.owner.equals(target.name) ? mergedBy(methods.get(mi.name + mi.desc)) : null;
        return mixin != null && (mixin.startsWith("bons.furious.") || mixin.startsWith("bons.pure.") || mixin.startsWith("agentcraft."));
    }

    private static String describe(ClassNode target, Map<String, MethodNode> methods, MethodInsnNode mi) {
        String mixin = mi.owner.equals(target.name) ? mergedBy(methods.get(mi.name + mi.desc)) : null;
        if (mixin == null) return mi.owner.replace('/', '.') + "." + mi.name;
        String mod = modOf(mixin);
        return (mod == null ? "" : mod + " ") + "(" + mixin.substring(mixin.lastIndexOf('.') + 1) + ")";
    }

    /** The mixin class a merged method came from (Mixin's @MixinMerged), or null. Also Guards.checkForeignOverwrite. */
    static String mergedBy(MethodNode m) {
        if (m == null || m.visibleAnnotations == null) return null;
        for (AnnotationNode a : m.visibleAnnotations) {
            if (!a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || a.values == null) continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) return String.valueOf(a.values.get(i + 1));
        }
        return null;
    }

    /** "NAME VERSION" of the installed mod whose jar holds the mixin class, or null. Also Guards.checkForeignOverwrite. */
    static String modOf(String mixinClass) {
        try {
            var loading = net.neoforged.fml.loading.LoadingModList.get();
            if (loading == null) return null;
            String[] path = (mixinClass.replace('.', '/') + ".class").split("/");
            for (var info : loading.getModFiles()) {
                if (info.getMods().isEmpty() || !Files.isRegularFile(info.getFile().findResource(path))) continue;
                var mod = info.getMods().get(0);
                return mod.getDisplayName() + " " + mod.getVersion();
            }
        } catch (Throwable t) {
            // the mixin's class name is in the line either way
        }
        return null;
    }

    /** "modid version" of the installed mod whose jar holds the mixin class, or null (also offline, without a mod list). */
    private static String modIdVersion(String mixinClass) {
        try {
            var loading = net.neoforged.fml.loading.LoadingModList.get();
            if (loading == null) return null;
            String[] path = (mixinClass.replace('.', '/') + ".class").split("/");
            for (var info : loading.getModFiles()) {
                if (info.getMods().isEmpty() || !Files.isRegularFile(info.getFile().findResource(path))) continue;
                var mod = info.getMods().get(0);
                return mod.getModId() + " " + mod.getVersion();
            }
        } catch (Throwable t) {
            // unknown: the switch steps aside as for any other foreign hook
        }
        return null;
    }

    private static String simple(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }
}
