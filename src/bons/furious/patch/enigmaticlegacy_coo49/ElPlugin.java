package bons.furious.patch.enigmaticlegacy_coo49;

import bons.pure.config.PureMixinPlugin;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the enigmaticlegacy_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines; the delegation pattern of other groups), plus two looks for
 * enigmaticlegacy_cursed_ring_anger_candidates_exact: when its look mixins (ElVisibilityLookMixin on LivingEntity,
 * ElVisibilityHooksLookMixin on ForgeHooks, both priority 999999 and empty) have been applied, postApply reads the class as
 * it now is, with every mixin of a lower priority merged, and records whether LivingEntity.getVisibilityPercent
 * (m_20968_) and ForgeHooks.getEntityVisibilityMultiplier are still the plain Minecraft / Forge bodies (only their own
 * calls, no merged handler, not overwritten). The verdicts are plain strings ("" = plain); no game class is loaded here.
 * CursedRingScan also stands down when a mixin of another mod with priority 999999 or more was applied to either class.
 */
public final class ElPlugin implements IMixinConfigPlugin {
    static final String LOOK_LIVING = "bons.furious.mixin.enigmaticlegacy_coo49.ElVisibilityLookMixin";
    static final String LOOK_HOOKS = "bons.furious.mixin.enigmaticlegacy_coo49.ElVisibilityHooksLookMixin";
    static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    static final String HOOKS = "net/minecraftforge/common/ForgeHooks";
    /** Null until the look ran; then "" (plain) or why not. */
    public static volatile String livingVerdict;
    public static volatile String hooksVerdict;
    public static volatile int livingPriority = Integer.MIN_VALUE;
    public static volatile int hooksPriority = Integer.MIN_VALUE;
    private final PureMixinPlugin delegate = new PureMixinPlugin();

    /** The calls the plain getVisibilityPercent makes (owner, name, descriptor). */
    static final String[][] VISIBILITY_CALLS = {
            {LIVING, "m_20163_", "()Z"}, {"net/minecraft/world/entity/Entity", "m_20163_", "()Z"},
            {LIVING, "m_20145_", "()Z"}, {"net/minecraft/world/entity/Entity", "m_20145_", "()Z"},
            {LIVING, "m_21207_", "()F"},
            {LIVING, "m_6844_", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"},
            {"net/minecraft/world/entity/Entity", "m_6095_", "()Lnet/minecraft/world/entity/EntityType;"},
            {"net/minecraft/world/item/ItemStack", "m_150930_", "(Lnet/minecraft/world/item/Item;)Z"},
            {HOOKS, "getEntityVisibilityMultiplier", "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;D)D"}};
    /** The calls the plain ForgeHooks.getEntityVisibilityMultiplier makes. */
    static final String[][] HOOK_CALLS = {
            {"net/minecraftforge/event/entity/living/LivingEvent$LivingVisibilityEvent", "<init>", "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;D)V"},
            {"net/minecraftforge/eventbus/api/IEventBus", "post", "(Lnet/minecraftforge/eventbus/api/Event;)Z"},
            {"net/minecraftforge/event/entity/living/LivingEvent$LivingVisibilityEvent", "getVisibilityModifier", "()D"},
            {"java/lang/Math", "max", "(DD)D"}};

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
        if (targetClass == null) return;
        if (LOOK_LIVING.equals(mixinClassName) && LIVING.equals(targetClass.name)) {
            String v;
            try {
                v = inspect(targetClass, "m_20968_", "(Lnet/minecraft/world/entity/Entity;)D", VISIBILITY_CALLS);
            } catch (Throwable t) {
                v = "LivingEntity could not be inspected (" + t + ")";
            }
            livingPriority = mixinInfo == null ? Integer.MIN_VALUE : mixinInfo.getPriority();
            livingVerdict = v;
        } else if (LOOK_HOOKS.equals(mixinClassName) && HOOKS.equals(targetClass.name)) {
            String v;
            try {
                v = inspect(targetClass, "getEntityVisibilityMultiplier",
                        "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;D)D", HOOK_CALLS);
            } catch (Throwable t) {
                v = "ForgeHooks could not be inspected (" + t + ")";
            }
            hooksPriority = mixinInfo == null ? Integer.MIN_VALUE : mixinInfo.getPriority();
            hooksVerdict = v;
        }
    }

    /**
     * "" when the method exists, is not a merged mixin method (overwrite), has no invokedynamic and makes only the listed
     * calls; otherwise the first reason found. Public for the offline proof.
     */
    public static String inspect(ClassNode c, String name, String desc, String[][] allowed) {
        MethodNode m = null;
        for (MethodNode x : c.methods) if (x.name.equals(name) && x.desc.equals(desc)) m = x;
        if (m == null) return c.name + "." + name + desc + " is missing";
        AnnotationNode merged = annotation(m, "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        if (merged != null) return c.name + "." + name + " was replaced by a mixin" + mergedBy(merged);
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof InvokeDynamicInsnNode) return c.name + "." + name + " has an invokedynamic";
            if (i instanceof MethodInsnNode mi) {
                boolean ok = false;
                for (String[] a : allowed) {
                    if (a[0].equals(mi.owner) && a[1].equals(mi.name) && a[2].equals(mi.desc)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    String by = "";
                    if (mi.owner.equals(c.name)) {
                        for (MethodNode h : c.methods) {
                            if (h.name.equals(mi.name) && h.desc.equals(mi.desc)) {
                                AnnotationNode a = annotation(h, "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
                                if (a != null) by = mergedBy(a);
                            }
                        }
                    }
                    return c.name + "." + name + " calls " + mi.owner + "." + mi.name + mi.desc + by;
                }
            }
            if (i.getOpcode() == Opcodes.PUTFIELD || i.getOpcode() == Opcodes.PUTSTATIC) return c.name + "." + name + " writes a field";
        }
        return "";
    }

    private static AnnotationNode annotation(MethodNode m, String desc) {
        for (List<AnnotationNode> list : Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) if (a.desc.equals(desc)) return a;
        }
        return null;
    }

    private static String mergedBy(AnnotationNode a) {
        if (a.values == null) return "";
        for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) return " (merged by the mixin " + a.values.get(i + 1) + ")";
        return "";
    }
}
