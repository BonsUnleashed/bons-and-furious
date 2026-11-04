package bons.furious.patch.vanilla_cooking_recipe_index;

import bons.furious.guard.Fingerprint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

/**
 * Bons and Furious 1.0.49, vanilla_cooking_recipe_item_index: the probe that decides, once per class while it is transformed,
 * whether the code the index relies on is still as shipped after every other mod's mixins. Called from
 * RecipeIndexPlugin.postApply for the layer's three probe mixins, which have priority 3000 so Mixin applies them after the
 * others; each compares the transformed methods with the class as the game ships it (read through Mixin's bytecode provider,
 * the same read the guard table's fingerprints use). No Minecraft code is carried.
 *
 *  RecipeManager      getRecipeFor (m_44015_), its filter lambda, byType (m_44054_): unchanged, or the bit stays clear;
 *  AbstractCookingRecipe matches (m_5818_): unchanged;
 *  Ingredient         test(ItemStack): unchanged.
 */
public final class RecipeIndexProbe {
    /**
     * The verdict: one bit per probed class (CookingRecipeIndex.PROBE_*), set when it is as shipped after every mixin; the reason
     * of the last refusal. Kept here, not in CookingRecipeIndex: postApply runs while RecipeManager, AbstractCookingRecipe or
     * Ingredient is being transformed, and CookingRecipeIndex's static initializer reaches AbstractCookingRecipe and Ingredient.
     * This class touches no game class until a method compares bytecode.
     */
    static volatile int bits;
    static volatile String note = "";
    static final String MANAGER = "net/minecraft/world/item/crafting/RecipeManager";
    static final String GET_RECIPE_FOR = "m_44015_", GET_RECIPE_FOR_DESC =
            "(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;";
    static final String BY_TYPE = "m_44054_", BY_TYPE_DESC = "(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/Map;";
    static final String MATCHES = "m_5818_", MATCHES_DESC = "(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Z";
    static final String TEST = "test", TEST_DESC = "(Lnet/minecraft/world/item/ItemStack;)Z";
    static final String CIR = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

    private RecipeIndexProbe() {
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

    static boolean same(ClassNode shipped, ClassNode now, String name, String desc) {
        MethodNode a = method(shipped, name, desc), b = method(now, name, desc);
        return a != null && b != null && Fingerprint.of(a).equals(Fingerprint.of(b));
    }

    /** The implementation method of getRecipeFor's filter lambda (name, desc), or null. */
    static String[] lambda(MethodNode getRecipeFor) {
        if (getRecipeFor == null) return null;
        for (AbstractInsnNode n : getRecipeFor.instructions) {
            if (n instanceof InvokeDynamicInsnNode d && d.bsmArgs.length >= 2 && d.bsmArgs[1] instanceof Handle h && h.getOwner().equals(MANAGER))
                return new String[] {h.getName(), h.getDesc()};
        }
        return null;
    }

    /** RecipeManager: getRecipeFor, its lambda and byType as shipped. */
    public static String manager(ClassNode shipped, ClassNode now) {
        if (shipped == null) return "RecipeManager as shipped could not be read";
        if (!same(shipped, now, GET_RECIPE_FOR, GET_RECIPE_FOR_DESC)) return "RecipeManager.getRecipeFor is changed by another mod";
        String[] l = lambda(method(now, GET_RECIPE_FOR, GET_RECIPE_FOR_DESC));
        if (l == null || !same(shipped, now, l[0], l[1])) return "RecipeManager.getRecipeFor's filter is changed by another mod";
        if (!same(shipped, now, BY_TYPE, BY_TYPE_DESC)) return "RecipeManager.byType is changed by another mod";
        return null;
    }

    /** AbstractCookingRecipe.matches as shipped. */
    public static String cooking(ClassNode shipped, ClassNode now) {
        if (shipped == null) return "AbstractCookingRecipe as shipped could not be read";
        return same(shipped, now, MATCHES, MATCHES_DESC) ? null : "AbstractCookingRecipe.matches is changed by another mod";
    }

    /**
     * Ingredient.test as shipped. Any other mod's change refuses, ModernFix's faster_ingredients hook included: it answers a
     * single-tag ingredient from the item's live tags only while no server reload is active and walks the resolved items
     * otherwise, so its answer for one item can change with that reload state, which no per-item memo can follow.
     */
    public static String ingredient(ClassNode shipped, ClassNode now) {
        if (shipped == null) return "Ingredient as shipped could not be read";
        MethodNode a = method(shipped, TEST, TEST_DESC), b = method(now, TEST, TEST_DESC);
        if (a == null || b == null) return "Ingredient.test is missing";
        if (Fingerprint.of(a).equals(Fingerprint.of(b))) return null;
        List<String> by = changedBy(now, b);
        return "Ingredient.test is changed by another mod" + (by.isEmpty() ? "" : " (" + String.join(", ", by) + ")");
    }

    /** The mixins merged into a method or into the methods of its class it calls (for the log line). */
    static List<String> changedBy(ClassNode owner, MethodNode m) {
        List<String> out = new ArrayList<>();
        String self = mergedBy(m);
        if (self != null) out.add(self);
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode mi && mi.owner.equals(owner.name)) {
                MethodNode h = method(owner, mi.name, mi.desc);
                String x = h == null ? null : mergedBy(h);
                if (x != null && !out.contains(x)) out.add(x);
            }
        }
        return out;
    }

    static List<String> ops(MethodNode m) {
        List<String> out = new ArrayList<>();
        for (AbstractInsnNode n : m.instructions) if (n.getOpcode() >= 0) out.add(op(n));
        return out;
    }

    static String op(AbstractInsnNode n) {
        if (n instanceof MethodInsnNode mi) return n.getOpcode() + " " + mi.owner + "." + mi.name + mi.desc;
        return String.valueOf(n.getOpcode());
    }

    /** The mixin a merged method came from (its @MixinMerged), or null. */
    static String mergedBy(MethodNode m) {
        List<AnnotationNode> all = new ArrayList<>();
        if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
        if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
        for (AnnotationNode a : all) {
            if (!a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") || a.values == null) continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) return String.valueOf(a.values.get(i + 1));
        }
        return null;
    }

    /** RecipeIndexPlugin.postApply: one probe mixin applied to its class; record the verdict. */
    public static void record(String mixinSimpleName, ClassNode now) {
        String why;
        int bit;
        try {
            ClassNode shipped = shipped(now.name);
            switch (mixinSimpleName) {
                case "RecipeManagerIndexMixin" -> { bit = CookingRecipeIndex.PROBE_MANAGER; why = manager(shipped, now); }
                case "AbstractCookingRecipeProbeMixin" -> { bit = CookingRecipeIndex.PROBE_COOKING; why = cooking(shipped, now); }
                case "IngredientProbeMixin" -> { bit = CookingRecipeIndex.PROBE_INGREDIENT; why = ingredient(shipped, now); }
                default -> { return; }
            }
        } catch (Throwable t) {
            return;   // no bit: the index stays off
        }
        // PROBE_* are compile-time constants: reading them does not initialize CookingRecipeIndex
        if (why == null) {
            synchronized (RecipeIndexProbe.class) {
                bits |= bit;
            }
        } else {
            note = why;
        }
    }

    /** For dumps: the opcode of a probe-relevant instruction (unused at run time). */
    static boolean isReturn(int op) {
        return op >= Opcodes.IRETURN && op <= Opcodes.RETURN;
    }
}
