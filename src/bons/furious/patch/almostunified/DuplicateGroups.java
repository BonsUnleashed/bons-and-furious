package bons.furious.patch.almostunified;

import bons.furious.mixin.almostunified.CompareSettingsAccessor;
import bons.furious.mixin.almostunified.RecipeLinkAccessor;
import com.almostreliable.unified.config.DuplicationConfig;
import com.almostreliable.unified.recipe.RecipeLink;
import com.almostreliable.unified.utils.JsonCompare;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch almostunified_duplicate_groups (Almost Unified 0.11.0, LGPL-3.0, both sides).
 *
 * After unifying, Almost Unified looks for duplicate recipes: RecipeTransformer.handleDuplicate(cur, recipes) compares one
 * unified recipe with EVERY recipe of its type (type check, ignore check, then RecipeLink.handleDuplicate), so a data load
 * of this pack's 43,500 recipes makes millions of comparisons (6.3% of the dedicated server's boot main thread, 1.4% of
 * the client's world load; AU logs 2.3 s / 1.7 s).
 *
 * A comparison can only succeed when JsonCompare.matches succeeds, and matches needs (1) the same compared fields: the
 * JSON keys minus the type's ignored fields (it compares the key counts and then looks every field of cur up in the
 * other), (2) for every compared field, equal values (raw JsonElement equality when the type's shouldSanitize is false);
 * crafting types (shaped/shapeless) also need (3) the same output item first. Every member of a duplicate link joined it
 * through such a successful comparison, so all members (and the link's master) share (1)-(3). Hence for a recipe that
 * differs from cur in (1), (2) or (3), RecipeLink.handleDuplicate(other) returns false in every one of its four branches
 * without side effects (no link is created or moved, no exception: the master shares cur's fields), PROVIDED both output
 * items are already cached (getCraftingRecipeOutput caches on first use and logs a warning when it cannot parse one).
 *
 * So, per recipe list (one per type): at the first recipe that is not ignored, the outputs are computed exactly in the
 * order the original would compute them (that recipe first, then every other non-ignored recipe in list order, and only
 * if there is at least one), then every non-ignored recipe gets a 64-bit key over (1), (2) (only when shouldSanitize is
 * false; numbers all hash alike because JSON number equality is numeric) and (3), a function of equality classes: equal
 * fields, values and outputs give equal keys, so DIFFERENT keys prove a mismatch (equal keys are just compared as usual).
 * handleDuplicate then calls RecipeLink.handleDuplicate only for the recipes with cur's key, in list order: the same
 * links are formed in the same order and the same boolean is returned. The ignore checks, the compare context and the
 * outputs are AU's own methods. Lists with a null entry or mixed types (where the original throws) run the original.
 */
public final class DuplicateGroups {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.almostunifiedDuplicateGroups=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.almostunifiedDuplicateGroups", "true"));
    /** Shadow mode for rigs: every skipped recipe is re-checked field by field (keys, values, output) against cur. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.almostunifiedDuplicateGroups.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): lists indexed, comparisons made, comparisons skipped. */
    public static final AtomicLong LISTS = new AtomicLong(), COMPARED = new AtomicLong(), SKIPPED = new AtomicLong();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    private static volatile Index last;

    private DuplicateGroups() {
    }

    /** RecipeTransformer.transformRecipes returned: the lists of this transform are done. */
    public static void release() {
        last = null;
    }

    /** RecipeTransformer.handleDuplicate(cur, recipes) with the original as the fallback. */
    public static boolean handle(DuplicationConfig config, RecipeLink cur, List<RecipeLink> recipes, Operation<Boolean> original) {
        Index idx = last;
        if (idx == null || idx.list != recipes || idx.links.length != recipes.size() || idx.config != config) {
            idx = Index.prepare(config, recipes);
            if (idx == null) return original.call(cur, recipes);
            last = idx;
        }
        Integer pos = idx.position.get(cur);
        if (pos == null || !cur.getType().equals(idx.type)) return original.call(cur, recipes);
        if (config.shouldIgnoreRecipe(cur)) return false;
        JsonCompare.CompareContext ctx = config.getCompareContext(cur);
        if (idx.groupOf == null) return idx.firstPass(cur, pos, ctx);
        int[] group = idx.groupOf[pos];
        if (SHADOW) idx.shadow(pos, group);
        boolean found = false;
        RecipeLink[] links = idx.links;
        for (int i : group) {
            RecipeLink other = links[i];
            if (other == cur) continue;
            found |= cur.handleDuplicate(other, ctx);
        }
        COMPARED.addAndGet(group.length - 1);
        SKIPPED.addAndGet(idx.live - group.length);
        return found;
    }

    static final class Index {
        final List<RecipeLink> list;
        final DuplicationConfig config;
        final RecipeLink[] links;
        final IdentityHashMap<RecipeLink, Integer> position;
        final ResourceLocation type;
        boolean[] ignored;
        int[][] groupOf;          // per position: the positions (ascending) of the non-ignored recipes with the same key
        long[] keys;
        boolean crafting, sanitize, hasIgnored;
        Set<String> ignoredFields;
        int live;

        private Index(List<RecipeLink> list, DuplicationConfig config, RecipeLink[] links, IdentityHashMap<RecipeLink, Integer> position, ResourceLocation type) {
            this.list = list;
            this.config = config;
            this.links = links;
            this.position = position;
            this.type = type;
        }

        /**
         * Null when the list has a null entry, recipes of different types or of different crafting kinds (the original then
         * runs and throws or compares as before).
         */
        static Index prepare(DuplicationConfig config, List<RecipeLink> list) {
            RecipeLink[] links = list.toArray(new RecipeLink[0]);
            if (links.length == 0 || links[0] == null) return null;
            ResourceLocation type = links[0].getType();
            boolean crafting = ((RecipeLinkAccessor) (Object) links[0]).bons$isCraftingRecipe();
            IdentityHashMap<RecipeLink, Integer> position = new IdentityHashMap<>(links.length * 2);
            for (int i = 0; i < links.length; i++) {
                RecipeLink l = links[i];
                if (l == null || !type.equals(l.getType()) || ((RecipeLinkAccessor) (Object) l).bons$isCraftingRecipe() != crafting) return null;
                if (position.put(l, i) != null) return null;   // the same link twice: not a list AU builds
            }
            Index idx = new Index(list, config, links, position, type);
            idx.crafting = crafting;
            return idx;
        }

        /**
         * The call for the first recipe of the list that is not ignored (cur0). For crafting types the outputs are computed
         * exactly as the original computes them during this call: cur0's at the first other non-ignored recipe, then each
         * other non-ignored recipe's in list order, each right before that recipe would be compared, so even an exception
         * from a comparison leaves the same outputs computed and the same warnings logged. Afterwards every non-ignored
         * recipe has its key and the groups are built for the later calls.
         */
        boolean firstPass(RecipeLink cur0, int pos0, JsonCompare.CompareContext ctx) {
            int n = links.length;
            boolean[] ign = new boolean[n];
            int liveCount = 0;
            for (int i = 0; i < n; i++) {
                ign[i] = config.shouldIgnoreRecipe(links[i]);
                if (!ign[i]) liveCount++;
            }
            JsonCompare.CompareSettings settings = config.getCompareSettings(type);
            ignoredFields = settings.getIgnoredFields();
            hasIgnored = settings.hasIgnoredFields();
            sanitize = ((CompareSettingsAccessor) (Object) settings).bons$shouldSanitize();
            ignored = ign;
            live = liveCount;
            long[] k = new long[n];
            boolean found = false;
            int compared = 0;
            if (crafting) {
                boolean started = false;
                long k0 = 0;
                for (int j = 0; j < n; j++) {
                    if (ign[j] || j == pos0) continue;
                    if (!started) {
                        output(cur0);
                        k0 = keyOf(cur0);
                        started = true;
                    }
                    output(links[j]);
                    k[j] = keyOf(links[j]);
                    if (k[j] == k0) {
                        compared++;
                        found |= cur0.handleDuplicate(links[j], ctx);
                    }
                }
                k[pos0] = started ? k0 : 0;
            } else {
                for (int j = 0; j < n; j++) if (!ign[j]) k[j] = keyOf(links[j]);
                for (int j = 0; j < n; j++) {
                    if (ign[j] || j == pos0 || k[j] != k[pos0]) continue;
                    compared++;
                    found |= cur0.handleDuplicate(links[j], ctx);
                }
            }
            HashMap<Long, List<Integer>> byKey = new HashMap<>();
            for (int i = 0; i < n; i++) if (!ign[i]) byKey.computeIfAbsent(k[i], x -> new ArrayList<>()).add(i);
            int[][] groups = new int[n][];
            for (List<Integer> members : byKey.values()) {
                int[] arr = new int[members.size()];
                for (int j = 0; j < arr.length; j++) arr[j] = members.get(j);
                for (int m : arr) groups[m] = arr;
            }
            keys = k;
            groupOf = groups;
            if (SHADOW) shadow(pos0, groups[pos0]);
            LISTS.incrementAndGet();
            COMPARED.addAndGet(compared);
            SKIPPED.addAndGet(liveCount - 1 - compared);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: almostunified_duplicate_groups compares each unified recipe only with recipes that can be its duplicates");
            }
            return found;
        }

        private Object output(RecipeLink link) {
            return ((RecipeLinkAccessor) (Object) link).bons$craftingRecipeOutput();
        }

        /** Hash of (compared field names, their values unless shouldSanitize, the output for crafting types): see the class comment. */
        private long keyOf(RecipeLink link) {
            JsonObject actual = link.getActual();
            long h = 0;
            int count = 0;
            for (Map.Entry<String, JsonElement> e : actual.entrySet()) {
                String f = e.getKey();
                if (hasIgnored && ignoredFields.contains(f)) continue;
                count++;
                long fh = mix(f.hashCode() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
                h += mix(fh ^ (sanitize ? 0L : valueHash(e.getValue()) * 0xC2B2AE3D27D4EB4FL));
            }
            h = mix(h + count * 0x165667B19E3779F9L);
            if (crafting) h = mix(h ^ (System.identityHashCode(output(link)) * 0xD6E8FEB86659FD93L));
            return h;
        }

        /** Shadow: every non-ignored recipe outside cur's group really differs from cur in fields, values or output. */
        void shadow(int pos, int[] group) {
            SHADOW_CHECKS.incrementAndGet();
            boolean[] inGroup = new boolean[links.length];
            for (int i : group) inGroup[i] = true;
            RecipeLink cur = links[pos];
            for (int i = 0; i < links.length; i++) {
                if (ignored[i] || inGroup[i]) continue;
                if (sameCompared(cur, links[i]) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: almostunified_duplicate_groups shadow check: {} and {} have different keys but equal compared fields",
                            cur.getId(), links[i].getId());
            }
        }

        private boolean sameCompared(RecipeLink a, RecipeLink b) {
            if (crafting && output(a) != output(b)) return false;
            JsonObject x = a.getActual(), y = b.getActual();
            int nx = 0, ny = 0;
            for (Map.Entry<String, JsonElement> e : x.entrySet()) {
                if (hasIgnored && ignoredFields.contains(e.getKey())) continue;
                nx++;
                JsonElement other = y.get(e.getKey());
                if (other == null) return false;
                if (!sanitize && !Objects.equals(e.getValue(), other)) return false;
            }
            for (String f : y.keySet()) if (!(hasIgnored && ignoredFields.contains(f))) ny++;
            return nx == ny;
        }
    }

    /** A hash of a JSON value that equal values (JsonElement.equals) always share; all numbers hash alike. */
    static long valueHash(JsonElement e) {
        if (e == null) return 0x3C6EF372FE94F82BL;
        if (e.isJsonNull()) return 0x1F83D9ABFB41BD6BL;
        if (e instanceof JsonPrimitive p) {
            if (p.isBoolean()) return p.getAsBoolean() ? 0x5BE0CD19137E2179L : 0x510E527FADE682D1L;
            if (p.isNumber()) return 0x6A09E667F3BCC908L;
            if (p.isString()) return mix(p.getAsString().hashCode() * 0x9E3779B97F4A7C15L + 0xBB67AE8584CAA73BL);
            return 0x7137449123EF65CDL;
        }
        if (e instanceof JsonArray a) {
            long h = 0xA54FF53A5F1D36F1L;
            for (JsonElement x : a) h = mix(h * 31 + valueHash(x));
            return mix(h + a.size());
        }
        if (e instanceof JsonObject o) {
            long h = 0x9B05688C2B3E6C1FL;
            for (Map.Entry<String, JsonElement> x : o.entrySet())
                h += mix(mix(x.getKey().hashCode() * 0x9E3779B97F4A7C15L) ^ valueHash(x.getValue()));
            return mix(h + o.size());
        }
        return 0x2B992DDFA23249D6L;
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
