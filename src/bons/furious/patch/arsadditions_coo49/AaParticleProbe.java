package bons.furious.patch.arsadditions_coo49;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Bons and Furious 1.0.49, arsadditions_magelight_far_burst_skip: the probe that decides, while LevelRenderer and
 * ClientLevel are transformed, whether the particle path the switch reasons about is the game's own: ClientLevel.addParticle
 * (m_7106_) and LevelRenderer's addParticle (m_109743_, m_109752_), addParticleInternal (m_109804_: the 32-block cull) and
 * calculateParticleLevel (m_109767_). Called from AaPlugin.postApply for the two probe mixins (priority 2,000,000: after
 * every other mod's mixin on those classes). A method is the game's own when no other mod's mixin replaced it and none of
 * its calls goes to a handler another mod's mixin merged into the class; Valkyrien Skies' particle transform (its HEAD
 * hook re-adds ship particles at their world position and leaves every other particle alone) is allowed, ship lanterns
 * run the original anyway. Anything else (a cull distance changed, a particle chance or a reduced setting added) leaves
 * the switch off for the session. No Minecraft code is carried.
 */
public final class AaParticleProbe {
    static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    static final Set<String> ALLOWED = Set.of("org.valkyrienskies.mod.mixin.feature.transform_particles.MixinLevelRenderer");
    public static final String[][] LEVEL_RENDERER = {
            {"m_109743_", "(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"},
            {"m_109752_", "(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V"},
            {"m_109804_", "(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;"},
            {"m_109767_", "(Z)Lnet/minecraft/client/ParticleStatus;"}};
    public static final String[][] CLIENT_LEVEL = {{"m_7106_", "(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"}};

    private static volatile boolean levelRendererOk, clientLevelOk;
    private static volatile String note = "its probes did not run";

    private AaParticleProbe() {
    }

    static String mergedBy(MethodNode m) {
        for (List<AnnotationNode> list : Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) {
                if (!MERGED.equals(a.desc) || a.values == null) continue;
                for (int i = 0; i + 1 < a.values.size(); i += 2)
                    if ("mixin".equals(a.values.get(i))) return String.valueOf(a.values.get(i + 1)).replace('/', '.');
            }
        }
        return null;
    }

    /** Null when the listed methods of the transformed class are the game's own (allowed mixins aside), else why not. */
    public static String verdict(ClassNode now, String[][] methods) {
        if (now == null) return "the class could not be read";
        Map<String, String> handlers = new HashMap<>();
        for (MethodNode m : now.methods) {
            String by = mergedBy(m);
            if (by != null) handlers.put(m.name + m.desc, by);
        }
        for (String[] t : methods) {
            MethodNode m = null;
            for (MethodNode x : now.methods) if (x.name.equals(t[0]) && x.desc.equals(t[1])) m = x;
            if (m == null) return now.name + "." + t[0] + " is missing";
            String own = mergedBy(m);
            if (own != null && !ALLOWED.contains(own)) return now.name + "." + t[0] + " is replaced by " + own;
            for (AbstractInsnNode n : m.instructions) {
                if (!(n instanceof MethodInsnNode call) || !call.owner.equals(now.name)) continue;
                String by = handlers.get(call.name + call.desc);
                if (by != null && !ALLOWED.contains(by)) return now.name + "." + t[0] + " is changed by " + by;
            }
        }
        return null;
    }

    /** AaPlugin.postApply: LevelRenderer as it will run. */
    static void recordLevelRenderer(ClassNode now) {
        record(now, LEVEL_RENDERER, true);
    }

    /** AaPlugin.postApply: ClientLevel as it will run. */
    static void recordClientLevel(ClassNode now) {
        record(now, CLIENT_LEVEL, false);
    }

    private static void record(ClassNode now, String[][] methods, boolean renderer) {
        String why;
        try {
            why = verdict(now, methods);
        } catch (Throwable t) {
            why = "the probe failed: " + t;
        }
        if (why == null) {
            if (renderer) levelRendererOk = true;
            else clientLevelOk = true;
        } else note = why;
    }

    /** For the harness: the verdicts as the plugin would set them. */
    public static void set(boolean renderer, boolean clientLevel) {
        levelRendererOk = renderer;
        clientLevelOk = clientLevel;
    }

    /** Whether the exact variant may leave bursts out (both probes saw the game's own particle path). */
    public static boolean ok() {
        return levelRendererOk && clientLevelOk;
    }

    /** Why the variant stays off, for a log line. */
    public static String note() {
        return note;
    }
}
