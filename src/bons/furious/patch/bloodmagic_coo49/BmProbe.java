package bons.furious.patch.bloodmagic_coo49;

import bons.furious.guard.Fingerprint;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

/**
 * Bons and Furious 1.0.49, bloodmagic_arc_recipe_list_memo: the probe that decides, once, while RecipeManager is transformed,
 * whether getAllRecipesFor (m_44013_: List.copyOf(byType(type).values())) and byType (m_44054_: the recipe table's map of
 * the type) are still as shipped after every other mod's mixins. Called from BmPlugin.postApply for BmRecipeManagerProbeMixin
 * (priority 3000: applied after the others); compares the transformed methods with the class as the game ships it, read
 * through Mixin's bytecode provider (the read the guard table's fingerprints use). No Minecraft code is carried. Any other
 * mod's change to either method (an injector that answers per player or per context, a redirect of the copy) leaves the list
 * memo off: every getARC then asks getAllRecipesFor as shipped.
 */
public final class BmProbe {
    static final String MANAGER = "net/minecraft/world/item/crafting/RecipeManager";
    static final String ALL = "m_44013_", ALL_DESC = "(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/List;";
    static final String BY_TYPE = "m_44054_", BY_TYPE_DESC = "(Lnet/minecraft/world/item/crafting/RecipeType;)Ljava/util/Map;";

    /** True once the probe saw both methods as shipped. Not touched by anything that loads game classes. */
    static volatile boolean ok;
    static volatile String note = "its probe did not run (the switch is off or its guard does not match)";

    private BmProbe() {
    }

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

    /** The verdict for RecipeManager as shipped vs as transformed: null when both methods are unchanged, else why not. */
    public static String verdict(ClassNode shipped, ClassNode now) {
        if (shipped == null) return "RecipeManager as shipped could not be read";
        if (!same(shipped, now, ALL, ALL_DESC)) return "RecipeManager.getAllRecipesFor is changed by another mod";
        if (!same(shipped, now, BY_TYPE, BY_TYPE_DESC)) return "RecipeManager.byType is changed by another mod";
        return null;
    }

    /** BmPlugin.postApply: the probe mixin applied to RecipeManager; record the verdict. */
    static void record(ClassNode now) {
        try {
            String why = verdict(shipped(now.name), now);
            if (why == null) ok = true;
            else note = why;
        } catch (Throwable t) {
            note = "the probe failed: " + t;
        }
    }

    /** For the harness: set the verdict as the plugin would. */
    public static void set(boolean value, String why) {
        ok = value;
        note = why == null ? "" : why;
    }

    /** Whether the list memo may run (the probe saw both methods as shipped). */
    public static boolean ok() {
        return ok;
    }

    /** Why the list memo stays off (for its one log line). */
    public static String note() {
        return note;
    }
}
