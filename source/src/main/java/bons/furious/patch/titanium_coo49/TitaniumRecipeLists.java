package bons.furious.patch.titanium_coo49;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch titanium_recipe_list_snapshot (since 1.0.49; Titanium 4.0.52 for NeoForge 1.21.1, both sides;
 * helper of RecipeUtilSnapshotMixin; no Titanium code is carried).
 *
 * What Titanium does. RecipeUtil.getRecipes(level, type) reads the recipe manager's table RecipeManager.byType (made public
 * by Titanium's access transformer), takes the type's recipe holders from it and copies every holder's recipe into a new
 * list with a stream (map(RecipeHolder::value), Collectors.toList: an ArrayList); no table -> a new empty ArrayList.
 * Industrial Foregoing asks for it per tick (Stonework Factory), three times per changed notification (Resourceful
 * Furnace), and for the Dissolution Chamber, the Fluid Extractor and the laser drills; Titanium's own smelting lookups go
 * through it.
 *
 * What the switch does (exact). The table is read through a VarHandle on the same field (the value Titanium's field read
 * answers; a null level or manager fails with the same NullPointerException). When the table is one of Guava's immutable
 * multimaps (what the server's recipe load and the client's recipe sync both build: RecipeManager.apply and
 * replaceRecipes), the type's recipes are taken once into a template list, kept per recipe type and per side (client or
 * server level) together with that table object, and each call answers a new ArrayList copied from it: the same recipes in
 * the same order, a fresh mutable list for every caller as before. The template is used only while the manager's table is
 * the very same object (asked on every call), so a reload or a new recipe sync gives a new one. Any other table (a mutable
 * one another mod might set), a missing table and a null type run Titanium's own method. Runtime flag
 * bons_and_furious.titaniumRecipeListSnapshot.
 */
public final class TitaniumRecipeLists {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.titaniumRecipeListSnapshot", "true"));
    private static final VarHandle BY_TYPE;
    /** False when the recipe table cannot be read through the handle (the original then runs). */
    public static final boolean READY;
    private static volatile boolean announced;
    /** Per recipe type, one map per side: the immutable table a template was taken from, and the template. */
    private static final ConcurrentHashMap<Object, Snapshot> SERVER = new ConcurrentHashMap<>(), CLIENT = new ConcurrentHashMap<>();
    /** Statistics for probes and the harness (plain fields): answers from a template, templates taken. */
    public static long answered, taken;

    static {
        VarHandle v = null;
        try {
            v = MethodHandles.privateLookupIn(RecipeManager.class, MethodHandles.lookup()).findVarHandle(RecipeManager.class, "byType", Multimap.class);
        } catch (Throwable t) {
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: titanium_recipe_list_snapshot cannot read the recipe table ({}); Titanium's recipe lists are built as shipped", t.toString());
        }
        BY_TYPE = v;
        READY = v != null;
    }

    private TitaniumRecipeLists() {
    }

    /** The table a template came from and the template (never handed out, only copied). */
    static final class Snapshot {
        final Object table;
        final ArrayList<Object> template;

        Snapshot(Object table, ArrayList<Object> template) {
            this.table = table;
            this.template = template;
        }
    }

    /** RecipeUtil.getRecipes(world, type)'s answer, or null when Titanium's own method is to answer. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List recipes(Level world, Object type) {
        RecipeManager manager = world.getRecipeManager();
        Multimap<Object, Object> table = (Multimap<Object, Object>) BY_TYPE.get(manager);
        if (type == null || !(table instanceof ImmutableMultimap)) return null;
        ConcurrentHashMap<Object, Snapshot> snapshots = world.isClientSide() ? CLIENT : SERVER;
        Snapshot s = snapshots.get(type);
        if (s == null || s.table != table) {
            s = new Snapshot(table, values(table.get(type)));
            snapshots.put(type, s);
            taken++;
        }
        answered++;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: titanium_recipe_list_snapshot applies (Titanium's recipe lists are copied from a kept list per recipe type)");
        }
        return new ArrayList<Object>(s.template);
    }

    /** The recipes of the holders, in their order: what Titanium's map(h -> h.value()) collects. */
    private static ArrayList<Object> values(Collection<?> holders) {
        ArrayList<Object> out = new ArrayList<>(holders.size());
        for (Object h : holders) out.add(((RecipeHolder<?>) h).value());
        return out;
    }
}
