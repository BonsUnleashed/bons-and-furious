package bons.furious.patch.farmersdelight;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ToolAction;
import org.slf4j.Logger;

/**
 * Bons and Furious switch farmersdelight_tool_action_items (Farmer's Delight 1.20.1-1.3.4, both sides). No FD code here.
 *
 * Every ToolActionIngredient (the cutting board's "strip with an axe / cut with a knife" ingredient) builds its entries in
 * its constructor: ForgeRegistries.ITEMS.getValues().stream().map(ItemStack::new).filter(s -> s.canPerformAction(action))
 * .map(Ingredient.ItemValue::new), i.e. an ItemStack for every registered item. 625 recipe files in this pack use it with 7
 * distinct actions, so a recipe load scans the item registry 625 times (3 s of the client's world load, 1 s of the server's
 * boot, again on every /reload and when a client decodes the recipe packet from a server).
 *
 * Within one recipe-load pass the first ingredient for an action runs the original pipeline (wrapped so that the items
 * whose stacks passed are recorded, in registry order, once it has run to the end); later ingredients for that action in
 * the same pass get fresh stacks of exactly those items, re-checked with canPerformAction, each wrapped in a fresh
 * ItemValue - the same entries, in the same order, each ingredient owning its own stacks as before. A pass is one
 * RecipeManager.apply call (JSON recipes) or the recipes read from one network buffer (FriendlyByteBuf identity, i.e. one
 * recipe packet), on one thread; registries are frozen and tags are only re-bound after the reload, so the item set and its
 * order cannot change inside a pass.
 *
 * Not identical, documented: the original also built and discarded a stack, and ran canPerformAction, for every
 * non-matching item in each of those ingredients. Audited in this pack (see notes/farmersdelight.md): Forge's ItemStack
 * constructor (registry delegate read, lazy capabilities - no AttachCapabilitiesEvent - and setDamage on damageable items,
 * all on the discarded stack); AzureLib's constructor hook (a random az_id UUID in the discarded stack's tag, from
 * SecureRandom); Relics' hook (RelicStorage.RELICS is filled for every relic item by the first, full scan of each pass, as
 * before; stats rolled with fresh unseeded Randoms into the discarded stack); AllTheLeaks' statistics hook is dev-only and
 * not applied in production. Item-specific canPerformAction / damage overrides of third-party items are assumed to be
 * side-effect free and stable within one pass (scanned, see the notes).
 */
public final class ToolActionItems {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.farmersdelightToolActionItems=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.farmersdelightToolActionItems", "true"));
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Pass> PASS = new ThreadLocal<>();
    private static final ThreadLocal<Object[]> BUFFER_PASS = new ThreadLocal<>();
    private static final Field ITEM_VALUE_STACK = itemValueStack();
    private static volatile boolean announced;
    /** Counters (read by probes and harnesses): full scans recorded, ingredients built from a recording. */
    public static long recorded;
    public static long reused;

    private ToolActionItems() {
    }

    /** The items each action matched so far in one pass. */
    public static final class Pass {
        final IdentityHashMap<ToolAction, List<Item>> matched = new IdentityHashMap<>();
    }

    private static Field itemValueStack() {
        try {
            Field f = Ingredient.ItemValue.class.getDeclaredField("f_43951_");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Bons and Furious: farmersdelight_tool_action_items stands down: cannot read Ingredient.ItemValue's stack ({})", e.toString());
            return null;
        }
    }

    /** RecipeManager.apply: a pass for the JSON recipes of this load; returns the enclosing state for {@link #leave}. */
    public static Pass enterPass() {
        Pass previous = PASS.get();
        PASS.set(enabled && ITEM_VALUE_STACK != null ? new Pass() : null);
        return previous;
    }

    /** ToolActionIngredient.Serializer.parse(FriendlyByteBuf): the pass of that buffer (one recipe packet). */
    public static Pass enterBuffer(Object buffer) {
        Pass previous = PASS.get();
        if (!enabled || ITEM_VALUE_STACK == null || buffer == null) {
            PASS.set(null);
            return previous;
        }
        Object[] slot = BUFFER_PASS.get();
        Pass pass;
        if (slot != null && ((WeakReference<?>) slot[0]).get() == buffer) {
            pass = (Pass) slot[1];
        } else {
            pass = new Pass();
            BUFFER_PASS.set(new Object[]{new WeakReference<>(buffer), pass});
        }
        PASS.set(pass);
        return previous;
    }

    public static void leave(Pass previous) {
        if (previous == null) PASS.remove();
        else PASS.set(previous);
    }

    /** The stream ToolActionIngredient's constructor passes to Ingredient's: original, recording, or rebuilt. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Stream<?> values(ToolAction action, Stream<?> original) {
        Pass pass = PASS.get();
        if (pass == null || action == null || original == null) return original;
        List<Item> items = pass.matched.get(action);
        if (items == null) return StreamSupport.stream(new Recorder((Spliterator) original.spliterator(), pass, action), false);
        reused++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: farmersdelight_tool_action_items builds tool-action ingredients from one registry scan per action and recipe load");
        }
        return items.stream().map(ItemStack::new).filter(s -> s.canPerformAction(action)).map(Ingredient.ItemValue::new);
    }

    /** Passes the original pipeline's values through and records their items; the record counts only if it ran to the end. */
    static final class Recorder implements Spliterator<Object> {
        final Spliterator<Object> inner;
        final Pass pass;
        final ToolAction action;
        final List<Item> seen = new ArrayList<>();
        boolean done;

        Recorder(Spliterator<Object> inner, Pass pass, ToolAction action) {
            this.inner = inner;
            this.pass = pass;
            this.action = action;
        }

        private void note(Object v) {
            try {
                seen.add(((ItemStack) ITEM_VALUE_STACK.get(v)).m_41720_());
            } catch (ReflectiveOperationException | RuntimeException e) {
                done = true;   // never commit an incomplete record
                seen.clear();
            }
        }

        private void commit() {
            if (done) return;
            done = true;
            pass.matched.putIfAbsent(action, List.copyOf(seen));
            recorded++;
        }

        @Override
        public boolean tryAdvance(Consumer<? super Object> action) {
            boolean more = inner.tryAdvance(v -> {
                note(v);
                action.accept(v);
            });
            if (!more) commit();
            return more;
        }

        @Override
        public void forEachRemaining(Consumer<? super Object> action) {
            inner.forEachRemaining(v -> {
                note(v);
                action.accept(v);
            });
            commit();
        }

        @Override
        public Spliterator<Object> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return inner.estimateSize();
        }

        @Override
        public int characteristics() {
            return inner.characteristics() & (Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE);
        }
    }
}
