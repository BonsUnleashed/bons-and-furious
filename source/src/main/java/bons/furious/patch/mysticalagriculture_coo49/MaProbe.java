package bons.furious.patch.mysticalagriculture_coo49;

import bons.furious.guard.Fingerprint;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;

/**
 * Bons and Furious 1.0.49, mysticalagriculture_soul_extractor_idle_skip: the probe that decides, once per class while it is
 * transformed, whether the code the switch relies on is still as shipped after every other mod's mixins. Called from
 * MysticalAgriculturePlugin.postApply for the switch's three probe mixins, which have priority 3000 so Mixin applies them
 * after the others; each compares the transformed methods with the class as the game ships it (read through Mixin's
 * bytecode provider, the same read the guard table's fingerprints use). No game or mod code is carried.
 *
 *  RecipeManager          getRecipeFor(RecipeType, RecipeInput, Level), the RecipeHolder overload it calls, that overload's
 *                         filter lambda and byType(RecipeType): unchanged (Minecraft 1.21.1);
 *  Ingredient             test(ItemStack), getItems() and isEmpty(): unchanged (another mod's change, an ingredient
 *                         speed-up included, refuses);
 *  SoulExtractionRecipe   matches(CraftingInput, Level), its matches(RecipeInput, Level) bridge and getIngredients():
 *                         unchanged.
 */
public final class MaProbe {
    /** One bit per probed class, set when it is as shipped after every mixin; the reason of the last refusal. */
    static volatile int bits;
    static volatile String note = "";
    public static final int PROBE_MANAGER = 1, PROBE_INGREDIENT = 2, PROBE_SOUL_RECIPE = 4, PROBE_ALL = 7;
    static final String MANAGER = "net/minecraft/world/item/crafting/RecipeManager";
    static final String GET_RECIPE_FOR = "getRecipeFor", GET_RECIPE_FOR_DESC =
            "(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;";
    static final String GET_RECIPE_FOR_HOLDER_DESC =
            "(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;";
    static final String BY_TYPE = "byType", BY_TYPE_DESC = "(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/Collection;";
    static final String TEST = "test", TEST_DESC = "(Lnet/minecraft/world/item/ItemStack;)Z";
    static final String GET_ITEMS = "getItems", GET_ITEMS_DESC = "()[Lnet/minecraft/world/item/ItemStack;";
    static final String IS_EMPTY = "isEmpty", IS_EMPTY_DESC = "()Z";
    static final String MATCHES = "matches", MATCHES_DESC = "(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z";
    static final String MATCHES_BRIDGE_DESC = "(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Z";
    static final String INGREDIENTS = "getIngredients", INGREDIENTS_DESC = "()Lnet/minecraft/core/NonNullList;";

    private MaProbe() {
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

    /** The implementation method of a RecipeManager method's lambda (name, desc), or null. */
    static String[] lambda(MethodNode owner) {
        if (owner == null) return null;
        for (AbstractInsnNode n : owner.instructions) {
            if (n instanceof InvokeDynamicInsnNode d && d.bsmArgs.length >= 2 && d.bsmArgs[1] instanceof Handle h && h.getOwner().equals(MANAGER))
                return new String[] {h.getName(), h.getDesc()};
        }
        return null;
    }

    /** RecipeManager: both getRecipeFor overloads of the look-up, the filter lambda and byType as shipped (null = yes). */
    public static String manager(ClassNode shipped, ClassNode now) {
        if (shipped == null) return "RecipeManager as shipped could not be read";
        if (!same(shipped, now, GET_RECIPE_FOR, GET_RECIPE_FOR_DESC)) return "RecipeManager.getRecipeFor is changed by another mod" + by(now, GET_RECIPE_FOR, GET_RECIPE_FOR_DESC);
        if (!same(shipped, now, GET_RECIPE_FOR, GET_RECIPE_FOR_HOLDER_DESC))
            return "RecipeManager.getRecipeFor(RecipeType, RecipeInput, Level, RecipeHolder) is changed by another mod" + by(now, GET_RECIPE_FOR, GET_RECIPE_FOR_HOLDER_DESC);
        String[] l = lambda(method(now, GET_RECIPE_FOR, GET_RECIPE_FOR_HOLDER_DESC));
        if (l == null || !same(shipped, now, l[0], l[1])) return "RecipeManager.getRecipeFor's filter is changed by another mod";
        if (!same(shipped, now, BY_TYPE, BY_TYPE_DESC)) return "RecipeManager.byType is changed by another mod" + by(now, BY_TYPE, BY_TYPE_DESC);
        return null;
    }

    /** Ingredient.test, getItems and isEmpty as shipped (null = yes). */
    public static String ingredient(ClassNode shipped, ClassNode now) {
        if (shipped == null) return "Ingredient as shipped could not be read";
        if (!same(shipped, now, TEST, TEST_DESC)) return "Ingredient.test is changed by another mod" + by(now, TEST, TEST_DESC);
        if (!same(shipped, now, GET_ITEMS, GET_ITEMS_DESC)) return "Ingredient.getItems is changed by another mod" + by(now, GET_ITEMS, GET_ITEMS_DESC);
        if (!same(shipped, now, IS_EMPTY, IS_EMPTY_DESC)) return "Ingredient.isEmpty is changed by another mod" + by(now, IS_EMPTY, IS_EMPTY_DESC);
        return null;
    }

    /** SoulExtractionRecipe's matches, its bridge and getIngredients as shipped (null = yes). */
    public static String soulRecipe(ClassNode shipped, ClassNode now) {
        if (shipped == null) return "SoulExtractionRecipe as shipped could not be read";
        if (!same(shipped, now, MATCHES, MATCHES_DESC)) return "SoulExtractionRecipe.matches(CraftingInput, Level) is changed by another mod" + by(now, MATCHES, MATCHES_DESC);
        if (!same(shipped, now, MATCHES, MATCHES_BRIDGE_DESC)) return "SoulExtractionRecipe.matches(RecipeInput, Level) is changed by another mod" + by(now, MATCHES, MATCHES_BRIDGE_DESC);
        if (!same(shipped, now, INGREDIENTS, INGREDIENTS_DESC)) return "SoulExtractionRecipe.getIngredients is changed by another mod" + by(now, INGREDIENTS, INGREDIENTS_DESC);
        return null;
    }

    /** " (mixin, mixin)" for the log line: the mixins merged into a method or into the methods of its class it calls. */
    static String by(ClassNode owner, String name, String desc) {
        MethodNode m = method(owner, name, desc);
        if (m == null) return " (the method is missing)";
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
        return out.isEmpty() ? "" : " (" + String.join(", ", out) + ")";
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

    /** MysticalAgriculturePlugin.postApply: one probe mixin applied to its class; record the verdict. */
    public static void record(String mixinSimpleName, ClassNode now) {
        String why;
        int bit;
        try {
            ClassNode shipped = shipped(now.name);
            switch (mixinSimpleName) {
                case "RecipeManagerProbeMixin" -> { bit = PROBE_MANAGER; why = manager(shipped, now); }
                case "IngredientProbeMixin" -> { bit = PROBE_INGREDIENT; why = ingredient(shipped, now); }
                case "SoulRecipeProbeMixin" -> { bit = PROBE_SOUL_RECIPE; why = soulRecipe(shipped, now); }
                default -> { return; }
            }
        } catch (Throwable t) {
            return;   // no bit: the switch stays off
        }
        if (why == null) {
            synchronized (MaProbe.class) {
                bits |= bit;
            }
        } else {
            note = why;
        }
    }

    /** Why the switch steps aside (for its one INFO line). */
    static String why() {
        int b = bits;
        if (b == 0) return "its probe did not run";
        List<String> why = new ArrayList<>();
        if ((b & PROBE_MANAGER) == 0) why.add("RecipeManager.getRecipeFor is changed by another mod");
        if ((b & PROBE_INGREDIENT) == 0) why.add("Ingredient.test is changed by another mod");
        if ((b & PROBE_SOUL_RECIPE) == 0) why.add("SoulExtractionRecipe.matches is changed by another mod");
        String n = note;
        return String.join("; ", why) + (n.isEmpty() ? "" : " (" + n + ")");
    }

    /** For the harness: the verdict as the plugin would set it. */
    public static void probe(int newBits, String newNote) {
        bits = newBits;
        note = newNote == null ? "" : newNote;
    }

    public static int bits() {
        return bits;
    }
}
