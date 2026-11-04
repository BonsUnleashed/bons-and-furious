package bons.furious.patch.chimes_coo49;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the chimes_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin (switches,
 * guard table, yields, log lines; the emf_render / faunify_coo49 delegation pattern), plus one look at the Mob class for
 * chimes_client_phantom_scan_skip: when its watch mixin (MobClientTargetWatchMixin, priority 2000) has been applied to
 * Minecraft's Mob, postApply reads the class as it now is, with every mixin of priority up to 2000 merged, and records
 * whether (1) Mob.getTarget (m_5448_) is still the plain read of the target field (aload 0, getfield f_21362_, areturn) and
 * (2) no method of Mob writes that field except setTarget (m_6710_) and merged handler methods that only setTarget calls
 * (the watch mixin reads the field at every return of setTarget, after them). The client skip acts only when both hold
 * ({@link #mobVerdict} is ""); ChimesScans also stands down at run time when a mixin of another mod with priority 2000 or
 * more was applied to Mob after this look. No game class is loaded here (the verdict is a plain string).
 */
public final class ChimesPlugin implements IMixinConfigPlugin {
    static final String WATCH_MIXIN = "bons.furious.mixin.chimes_coo49.MobClientTargetWatchMixin";
    static final String MOB = "net/minecraft/world/entity/Mob";
    static final String TARGET_FIELD = "f_21362_", TARGET_DESC = "Lnet/minecraft/world/entity/LivingEntity;";
    static final String GET_TARGET = "m_5448_", GET_TARGET_DESC = "()Lnet/minecraft/world/entity/LivingEntity;";
    static final String SET_TARGET = "m_6710_", SET_TARGET_DESC = "(Lnet/minecraft/world/entity/LivingEntity;)V";
    /** Null until the watch mixin was applied to Mob; then "" (the client skip may act) or why it may not. */
    public static volatile String mobVerdict;
    /** The watch mixin's priority as Mixin applied it (mixins of other mods at or above it were not looked at). */
    public static volatile int watchPriority = Integer.MIN_VALUE;
    private final PureMixinPlugin delegate = new PureMixinPlugin();

    @Override
    public void onLoad(String mixinPackage) {
        this.delegate.onLoad(mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return this.delegate.getRefMapperConfig();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.delegate.shouldApplyMixin(targetClassName, mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        this.delegate.acceptTargets(myTargets, otherTargets);
    }

    @Override
    public List<String> getMixins() {
        return this.delegate.getMixins();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
        if (WATCH_MIXIN.equals(mixinClassName) && targetClass != null && MOB.equals(targetClass.name)) {
            String verdict;
            try {
                verdict = inspectMob(targetClass);
            } catch (Throwable t) {
                verdict = "Mob could not be inspected (" + t + ")";
            }
            watchPriority = mixinInfo == null ? Integer.MIN_VALUE : mixinInfo.getPriority();
            mobVerdict = verdict;
        }
    }

    /**
     * "" when Mob.getTarget is the plain field read and the target field is written only by setTarget (or by merged handler
     * methods that only setTarget calls); otherwise the first reason found. Public for the offline proof.
     */
    public static String inspectMob(ClassNode mob) {
        MethodNode get = method(mob, GET_TARGET, GET_TARGET_DESC);
        if (get == null) return "Mob.getTarget (m_5448_) is missing";
        if (!plainGetter(get)) return "Mob.getTarget (m_5448_) is not the plain field read" + mergedBy(get);
        for (MethodNode m : mob.methods) {
            if (m.name.equals(SET_TARGET) && m.desc.equals(SET_TARGET_DESC)) continue;
            if (!writesTarget(m)) continue;
            if (mergedHandler(m) && onlyCalledFromSetTarget(mob, m)) continue;
            return "Mob." + m.name + m.desc + " writes the target field" + mergedBy(m);
        }
        return "";
    }

    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }

    private static boolean plainGetter(MethodNode m) {
        int n = 0;
        AbstractInsnNode[] code = new AbstractInsnNode[3];
        for (AbstractInsnNode i : m.instructions) {
            if (i.getOpcode() < 0) continue;   // labels, line numbers, frames
            if (n == 3) return false;
            code[n++] = i;
        }
        return n == 3
                && code[0] instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0
                && code[1] instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.owner.equals(MOB)
                && f.name.equals(TARGET_FIELD) && f.desc.equals(TARGET_DESC)
                && code[2].getOpcode() == Opcodes.ARETURN;
    }

    private static boolean writesTarget(MethodNode m) {
        for (AbstractInsnNode i : m.instructions)
            if (i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && f.name.equals(TARGET_FIELD) && f.desc.equals(TARGET_DESC))
                return true;   // any owner: Mob itself, or a subclass reference to the inherited field
        return false;
    }

    private static boolean mergedHandler(MethodNode m) {
        return annotation(m, "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") != null;
    }

    /** True when every call or method handle naming {@code target} in the class sits in setTarget (and there is one). */
    private static boolean onlyCalledFromSetTarget(ClassNode c, MethodNode target) {
        int calls = 0;
        for (MethodNode m : c.methods) {
            boolean isSet = m.name.equals(SET_TARGET) && m.desc.equals(SET_TARGET_DESC);
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof MethodInsnNode mi && mi.owner.equals(c.name) && mi.name.equals(target.name) && mi.desc.equals(target.desc)) {
                    if (!isSet) return false;
                    calls++;
                } else if (i instanceof InvokeDynamicInsnNode indy) {
                    for (Object a : indy.bsmArgs)
                        if (a instanceof Handle h && h.getName().equals(target.name) && h.getDesc().equals(target.desc)) return false;
                }
            }
        }
        return calls > 0;
    }

    private static AnnotationNode annotation(MethodNode m, String desc) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) if (a.desc.equals(desc)) return a;
        }
        return null;
    }

    private static String mergedBy(MethodNode m) {
        AnnotationNode a = annotation(m, "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        if (a == null || a.values == null) return "";
        for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) return " (merged by the mixin " + a.values.get(i + 1) + ")";
        return "";
    }
}
