package bons.furious.patch.enigmaticaddons_coo49;

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
 * Mixin config plugin of the enigmaticaddons_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines; the delegation pattern of other groups), plus two looks for
 * enigmaticaddons_idle_frost_skip: when its empty look mixins (EaFrostLivingLookMixin on LivingEntity,
 * EaFrostEntityLookMixin on Entity, priority 999999) have been applied, postApply reads the class as it now is, with every
 * mixin of a lower priority merged, and records whether canFreeze (m_142079_) is the Minecraft body plus only handlers of
 * the reviewed read-only mixins (Curios' MixinLivingEntity and Enigmatic Addons' own MixinLivingEntity on LivingEntity,
 * Blast from the Past's EntityMixin on Entity) and the callback plumbing of their injectors. The verdicts are plain strings
 * ("" = only those); no game class is loaded here. FrostSkip also stands down when a mixin of another mod with priority
 * 999999 or more is on either class.
 */
public final class EaPlugin implements IMixinConfigPlugin {
    static final String LOOK_LIVING = "bons.furious.mixin.enigmaticaddons_coo49.EaFrostLivingLookMixin";
    static final String LOOK_ENTITY = "bons.furious.mixin.enigmaticaddons_coo49.EaFrostEntityLookMixin";
    static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    static final String ENTITY = "net/minecraft/world/entity/Entity";
    static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    /** Null until the look ran; then "" (only reviewed handlers) or why not. */
    public static volatile String livingVerdict;
    public static volatile String entityVerdict;
    public static volatile int livingPriority = Integer.MIN_VALUE;
    public static volatile int entityPriority = Integer.MIN_VALUE;
    private final PureMixinPlugin delegate = new PureMixinPlugin();

    /** Mixins whose handlers merged into LivingEntity.canFreeze were reviewed: they only read (with no Curios update pending). */
    static final String[] LIVING_HANDLERS = {"top.theillusivec4.curios.mixin.core.MixinLivingEntity", "auviotre.enigmatic.addon.mixin.MixinLivingEntity"};
    /** Mixins whose handlers merged into Entity.canFreeze were reviewed: they only read. */
    static final String[] ENTITY_HANDLERS = {"team.recrafted.blastfromthepast.mixin.EntityMixin"};
    /** The calls of Minecraft's LivingEntity.canFreeze (owner, name, descriptor). */
    static final String[][] LIVING_CALLS = {
            {LIVING, "m_5833_", "()Z"}, {ENTITY, "m_5833_", "()Z"},
            {LIVING, "m_6844_", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"},
            {"net/minecraft/world/item/ItemStack", "m_204117_", "(Lnet/minecraft/tags/TagKey;)Z"},
            {ENTITY, "m_142079_", "()Z"}};
    /** The calls of Minecraft's Entity.canFreeze. */
    static final String[][] ENTITY_CALLS = {
            {ENTITY, "m_6095_", "()Lnet/minecraft/world/entity/EntityType;"},
            {"net/minecraft/world/entity/EntityType", "m_204039_", "(Lnet/minecraft/tags/TagKey;)Z"}};

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
                v = inspect(targetClass, LIVING_CALLS, LIVING_HANDLERS);
            } catch (Throwable t) {
                v = "LivingEntity could not be inspected (" + t + ")";
            }
            livingPriority = mixinInfo == null ? Integer.MIN_VALUE : mixinInfo.getPriority();
            livingVerdict = v;
        } else if (LOOK_ENTITY.equals(mixinClassName) && ENTITY.equals(targetClass.name)) {
            String v;
            try {
                v = inspect(targetClass, ENTITY_CALLS, ENTITY_HANDLERS);
            } catch (Throwable t) {
                v = "Entity could not be inspected (" + t + ")";
            }
            entityPriority = mixinInfo == null ? Integer.MIN_VALUE : mixinInfo.getPriority();
            entityVerdict = v;
        }
    }

    /**
     * "" when canFreeze (m_142079_ ()Z) of {@code c} is not a merged mixin method and makes only the listed calls, calls of
     * the injectors' callback objects, boxing, and calls of merged handler methods of the listed mixins; otherwise the
     * first reason found. Public for the offline proof.
     */
    public static String inspect(ClassNode c, String[][] calls, String[] handlers) {
        MethodNode m = method(c, "m_142079_", "()Z");
        if (m == null) return c.name + ".canFreeze is missing";
        AnnotationNode merged = annotation(m, MERGED);
        if (merged != null) return c.name + ".canFreeze was replaced by a mixin" + mergedBy(merged);
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof InvokeDynamicInsnNode) return c.name + ".canFreeze has an invokedynamic";
            if (i.getOpcode() == Opcodes.PUTFIELD || i.getOpcode() == Opcodes.PUTSTATIC) return c.name + ".canFreeze writes a field";
            if (!(i instanceof MethodInsnNode mi)) continue;
            if (listed(mi, calls)) continue;
            if (mi.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable")
                    || mi.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")) continue;
            if (mi.owner.equals("java/lang/Boolean") && (mi.name.equals("valueOf") || mi.name.equals("booleanValue"))) continue;
            if (mi.owner.equals(c.name)) {
                MethodNode h = method(c, mi.name, mi.desc);
                AnnotationNode a = h == null ? null : annotation(h, MERGED);
                String by = a == null ? null : mixinOf(a);
                if (by != null && Arrays.asList(handlers).contains(by)) continue;
                return c.name + ".canFreeze calls " + mi.name + mi.desc + (by == null ? "" : " (merged by the mixin " + by + ")");
            }
            return c.name + ".canFreeze calls " + mi.owner + "." + mi.name + mi.desc;
        }
        return "";
    }

    private static boolean listed(MethodInsnNode mi, String[][] calls) {
        for (String[] a : calls) if (a[0].equals(mi.owner) && a[1].equals(mi.name) && a[2].equals(mi.desc)) return true;
        return false;
    }

    private static MethodNode method(ClassNode c, String name, String desc) {
        for (MethodNode x : c.methods) if (x.name.equals(name) && x.desc.equals(desc)) return x;
        return null;
    }

    private static AnnotationNode annotation(MethodNode m, String desc) {
        for (List<AnnotationNode> list : Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (list == null) continue;
            for (AnnotationNode a : list) if (a.desc.equals(desc)) return a;
        }
        return null;
    }

    private static String mixinOf(AnnotationNode a) {
        if (a.values == null) return null;
        for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) return String.valueOf(a.values.get(i + 1));
        return null;
    }

    private static String mergedBy(AnnotationNode a) {
        String m = mixinOf(a);
        return m == null ? "" : " (merged by the mixin " + m + ")";
    }
}
