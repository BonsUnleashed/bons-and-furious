package bons.furious.patch.mahoutsukai_coo49;

import bons.furious.guard.Fingerprint;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

/**
 * Bons and Furious 1.0.49, mahoutsukai_projector_enchant_precheck: the probe that decides, once per class while it is
 * transformed, whether the methods MahouProjector relies on are still as shipped after every other mod's mixins. Called from
 * MahouPlugin.postApply for the group's five probe mixins, which have priority 3000 so Mixin applies them after the others;
 * each compares the transformed methods with the class as the game ships it (read through Mixin's bytecode provider, the
 * read the guard table's fingerprints use). No Minecraft, Forge or Mahou Tsukai code is carried.
 *
 *  EffectUtil (Mahou Tsukai)  hasEnchantment(ItemStack, Enchantment), getEnchantmentLevel(ItemStack, Enchantment)
 *  EnchantmentHelper          getItemEnchantmentLevel (m_44843_), getTagEnchantmentLevel (Forge)
 *  ItemStack                  getTag (m_41783_), getEnchantmentTags (m_41785_), isEmpty (m_41619_), getItem (m_41720_)
 *  IForgeItemStack            getEnchantmentLevel(Enchantment)
 *  IForgeItem                 getEnchantmentLevel(ItemStack, Enchantment)
 *
 * This class touches no game class until a method compares bytecode.
 */
public final class MahouProjectorProbe {
    public static final int PROBE_EFFECT_UTIL = 1, PROBE_HELPER = 2, PROBE_STACK = 4, PROBE_FORGE_STACK = 8, PROBE_FORGE_ITEM = 16;
    public static final int ALL = PROBE_EFFECT_UTIL | PROBE_HELPER | PROBE_STACK | PROBE_FORGE_STACK | PROBE_FORGE_ITEM;
    static final String STACK = "Lnet/minecraft/world/item/ItemStack;", ENCH = "Lnet/minecraft/world/item/enchantment/Enchantment;";
    /** One bit per probed class, set when its methods are as shipped after every mixin; the reason of the last refusal. */
    static volatile int bits;
    /** One bit per probed class whose methods another mod changed (a refusal is final: the class is transformed once). */
    static volatile int refused;
    static volatile String note = "";

    private MahouProjectorProbe() {
    }

    static String why() {
        int b = bits;
        StringBuilder sb = new StringBuilder();
        if ((b & PROBE_EFFECT_UTIL) == 0) sb.append(" EffectUtil");
        if ((b & PROBE_HELPER) == 0) sb.append(" EnchantmentHelper");
        if ((b & PROBE_STACK) == 0) sb.append(" ItemStack");
        if ((b & PROBE_FORGE_STACK) == 0) sb.append(" IForgeItemStack");
        if ((b & PROBE_FORGE_ITEM) == 0) sb.append(" IForgeItem");
        return "not proven as shipped:" + sb + (note.isEmpty() ? "" : "; " + note);
    }

    /** The class as shipped (no mixin), or null. */
    static ClassNode shipped(String internalName) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(internalName.replace('/', '.'));
        } catch (Throwable t) {
            return null;
        }
    }

    static MethodNode method(ClassNode c, String name, String desc) {
        if (c == null) return null;
        for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        return null;
    }

    /** Both have the method and its code is the same (Fingerprint: instructions, operands, jumps, try/catch). */
    static boolean same(ClassNode shipped, ClassNode now, String name, String desc) {
        MethodNode a = method(shipped, name, desc), b = method(now, name, desc);
        return a != null && b != null && Fingerprint.of(a).equals(Fingerprint.of(b));
    }

    /** null when every listed method is as shipped, else the reason. names / descs in pairs. */
    static String check(String label, ClassNode shipped, ClassNode now, String... nameDesc) {
        if (shipped == null) return label + " as shipped could not be read";
        for (int i = 0; i + 1 < nameDesc.length; i += 2)
            if (!same(shipped, now, nameDesc[i], nameDesc[i + 1])) return label + "." + nameDesc[i] + " is changed by another mod";
        return null;
    }

    /** The comparison for one probe mixin (also called by the harness with hand-made class nodes). */
    static String verdict(String mixinSimpleName, ClassNode shipped, ClassNode now) {
        switch (mixinSimpleName) {
            case "MahouEffectUtilProbeMixin":
                return check("EffectUtil", shipped, now, "hasEnchantment", "(" + STACK + ENCH + ")Z", "getEnchantmentLevel", "(" + STACK + ENCH + ")I");
            case "MahouEnchantmentHelperProbeMixin":
                return check("EnchantmentHelper", shipped, now, "m_44843_", "(" + ENCH + STACK + ")I", "getTagEnchantmentLevel", "(" + ENCH + STACK + ")I");
            case "MahouItemStackProbeMixin":
                return check("ItemStack", shipped, now, "m_41783_", "()Lnet/minecraft/nbt/CompoundTag;", "m_41785_", "()Lnet/minecraft/nbt/ListTag;",
                        "m_41619_", "()Z", "m_41720_", "()Lnet/minecraft/world/item/Item;");
            case "MahouForgeItemStackProbeMixin":
                return check("IForgeItemStack", shipped, now, "getEnchantmentLevel", "(" + ENCH + ")I");
            case "MahouForgeItemProbeMixin":
                return check("IForgeItem", shipped, now, "getEnchantmentLevel", "(" + STACK + ENCH + ")I");
            default:
                return "unknown probe " + mixinSimpleName;
        }
    }

    static int bitOf(String mixinSimpleName) {
        switch (mixinSimpleName) {
            case "MahouEffectUtilProbeMixin": return PROBE_EFFECT_UTIL;
            case "MahouEnchantmentHelperProbeMixin": return PROBE_HELPER;
            case "MahouItemStackProbeMixin": return PROBE_STACK;
            case "MahouForgeItemStackProbeMixin": return PROBE_FORGE_STACK;
            case "MahouForgeItemProbeMixin": return PROBE_FORGE_ITEM;
            default: return 0;
        }
    }

    /** MahouPlugin.postApply: one probe mixin applied to its class; record the verdict. */
    public static void record(String mixinSimpleName, ClassNode now) {
        int bit = bitOf(mixinSimpleName);
        if (bit == 0 || now == null) return;
        String why;
        try {
            why = verdict(mixinSimpleName, shipped(now.name), now);
        } catch (Throwable t) {
            why = mixinSimpleName + " could not compare (" + t + ")";
        }
        synchronized (MahouProjectorProbe.class) {
            if (why == null) {
                bits |= bit;
            } else {
                note = why;
                refused |= bit;
            }
        }
    }

    /** MahouPlugin: has a probe already refused (RenderProjectorEnchant then keeps no precheck at all)? */
    static boolean anyRefused() {
        return refused != 0;
    }
}
