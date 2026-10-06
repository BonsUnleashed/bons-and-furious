package bons.furious.patch.jei_startup;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.library.plugins.vanilla.brewing.JeiBrewingRecipe;
import net.minecraft.resources.ResourceLocation;

/** A private index travelling with JEI's own per-build HashSet. No process-wide recipe state.
 * The HashSet still owns membership and iteration order. Only exact JEI recipes with non-null immutable
 * UIDs are indexed: their guarded equals/hashCode use that UID. Any unfamiliar value retires the index.
 * Removing/replacing a recipe updates the index only after the actual set mutation succeeds.
 */
public final class BrewingLookup {
    public static volatile boolean enabled = !Boolean.getBoolean("bons_and_furious.jeiBrewingLookup.off");
    public static final LongAdder LOOKUPS = new LongAdder(), FALLBACKS = new LongAdder();
    private BrewingLookup() {}

    public static final class Recipes extends HashSet<IJeiBrewingRecipe> {
        private final Map<ResourceLocation, IJeiBrewingRecipe> byUid = new HashMap<>();
        private boolean indexed = true;

        private static ResourceLocation key(Object recipe) {
            return recipe != null && recipe.getClass() == JeiBrewingRecipe.class
                    ? ((JeiBrewingRecipe) recipe).getUid() : null;
        }

        private void retire() {
            indexed = false;
            byUid.clear();
        }

        @Override public boolean add(IJeiBrewingRecipe recipe) {
            boolean changed = super.add(recipe);
            if (indexed) {
                ResourceLocation uid = key(recipe);
                if (uid == null) retire();
                else if (changed) byUid.put(uid, recipe);
            }
            return changed;
        }

        @Override public boolean remove(Object recipe) {
            boolean changed = super.remove(recipe);
            if (indexed) {
                ResourceLocation uid = key(recipe);
                if (uid == null) retire();
                else if (changed) byUid.remove(uid);
            }
            return changed;
        }

        @Override public void clear() { super.clear(); byUid.clear(); }

        // A caller can mutate the Set after JEI returns it. Such an iterator must never leave a stale index.
        @Override public Iterator<IJeiBrewingRecipe> iterator() {
            Iterator<IJeiBrewingRecipe> delegate = super.iterator();
            return new Iterator<>() {
                public boolean hasNext() { return delegate.hasNext(); }
                public IJeiBrewingRecipe next() { return delegate.next(); }
                public void remove() { delegate.remove(); retire(); }
            };
        }

        // HashSet.clone would shallow-copy the side index. Return an ordinary set instead.
        @Override public Object clone() { return new HashSet<>(this); }

        /** Null means decline; an empty stream is a proven miss, not a request to fall back. */
        public Stream<IJeiBrewingRecipe> candidates(IJeiBrewingRecipe recipe) {
            ResourceLocation uid;
            if (!enabled || !indexed || (uid = key(recipe)) == null) {
                FALLBACKS.increment();
                return null;
            }
            LOOKUPS.increment();
            return Stream.ofNullable(byUid.get(uid));
        }
    }
}
