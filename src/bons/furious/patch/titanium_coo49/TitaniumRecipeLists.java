package bons.furious.patch.titanium_coo49;

import com.google.common.collect.ImmutableMap;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch titanium_recipe_list_snapshot (since 1.0.49; Titanium 3.8.35, both sides; helper of
 * RecipeUtilSnapshotMixin; no Titanium code is carried).
 *
 * What Titanium does. RecipeUtil.getRecipes(level, type) reads the recipe manager's private recipe table through Forge's
 * ObfuscationReflectionHelper (a field lookup by reflection on every call), takes the type's recipe map and copies every
 * recipe of it into a new list with a stream (Collectors.toList: an ArrayList); no map -> a new empty ArrayList. Industrial
 * Foregoing asks for it per tick (Stonework Factory), three times per changed notification (Resourceful Furnace), and for
 * the Dissolution Chamber, the Fluid Extractor and the laser drills; Titanium's own smelting lookups go through it.
 *
 * What the switch does (exact). The table is read through a VarHandle on the same field (RecipeManager.f_44007_), the same
 * value the reflective read answers (a null manager fails with the same NullPointerException). When the type's recipe map is
 * one of Guava's immutable maps (what the server's recipe load builds), its values are taken once into a template list,
 * kept per recipe type together with that map object, and each call answers a new ArrayList copied from it: the same
 * recipes in the same order, a fresh mutable list for every caller as before. The template is used only while the type's
 * map is the very same object (asked on every call), so a reload, a replaced table or a replaced type map gives a new one.
 * Any other map (the client's recipe sync builds HashMaps) is copied with the same stream as Titanium's. A missing table
 * or type answers a new empty ArrayList as before. Runtime flag bons_and_furious.titaniumRecipeListSnapshot.
 */
public final class TitaniumRecipeLists {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.titaniumRecipeListSnapshot", "true"));
    private static final VarHandle RECIPES;
    /** False when the recipe table cannot be read through the handle (the original then runs). */
    public static final boolean READY;
    private static volatile boolean announced;
    /** Per recipe type: the immutable type map a template was taken from, and the template. */
    private static final ConcurrentHashMap<Object, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    /** Statistics for probes and the harness (plain fields): answers from a template, templates taken. */
    public static long answered, taken;

    static {
        VarHandle v = null;
        try {
            v = MethodHandles.privateLookupIn(RecipeManager.class, MethodHandles.lookup()).findVarHandle(RecipeManager.class, "f_44007_", Map.class);
        } catch (Throwable t) {
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: titanium_recipe_list_snapshot cannot read the recipe table ({}); Titanium's recipe lists are built as shipped", t.toString());
        }
        RECIPES = v;
        READY = v != null;
    }

    private TitaniumRecipeLists() {
    }

    /** The type map a template came from and the template (never handed out, only copied). */
    static final class Snapshot {
        final Object typeMap;
        final ArrayList<Object> template;

        Snapshot(Object typeMap, ArrayList<Object> template) {
            this.typeMap = typeMap;
            this.template = template;
        }
    }

    /** RecipeUtil.getRecipes(world, type)'s answer. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List recipes(Level world, Object type) {
        RecipeManager manager = world.m_7465_();
        Map recipes = (Map) RECIPES.get(manager);
        Map typed;
        if (recipes == null || (typed = (Map) recipes.get(type)) == null) return new ArrayList();
        if (!(typed instanceof ImmutableMap)) return (List) typed.values().stream().collect(Collectors.toList());
        Snapshot s = type == null ? null : SNAPSHOTS.get(type);
        if (s == null || s.typeMap != typed) {
            s = new Snapshot(typed, new ArrayList<Object>(typed.values()));
            if (type != null) SNAPSHOTS.put(type, s);
            taken++;
        }
        answered++;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: titanium_recipe_list_snapshot applies (Titanium's recipe lists are copied from a kept list per recipe type)");
        }
        return new ArrayList<Object>(s.template);
    }
}
