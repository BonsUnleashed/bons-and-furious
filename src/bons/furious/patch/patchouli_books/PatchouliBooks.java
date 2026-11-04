package bons.furious.patch.patchouli_books;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;
import vazkii.patchouli.common.item.ItemModBook;

/**
 * Bons and Furious switch patchouli_book_item_prefilter (Patchouli 1.20.1-84.1-FORGE on Minecraft 1.20.1 / Forge 47.4.28;
 * client: the per-frame caller is the client's book overlay; in single player the integrated server's lectern handler
 * shares the same answer). Idea: Collections Of Optimizations (patchouli.cacheBookItemLookup), idea text only. SRG member
 * names. Thread-safe: every snapshot is immutable and published through a volatile field.
 *
 * What Patchouli does. ItemStackUtil.getBookFromStack(stack) answers "which guide book is this stack": a Patchouli book item
 * (ItemModBook) reads its book from the stack; any other stack is compared with every loaded book in
 * BookRegistry.INSTANCE.books.values() order through ItemStack.isSameItem(book.getBookItem(), stack) (m_41656_ =
 * bookItem.is(stack.getItem()): the item only, no tags) and the first match is returned, else null. The book_right_click HUD
 * overlay asks it every frame with the main-hand stack (and tooltips ask it), so with 13 books every frame walks the HashMap,
 * 13 Books, 13 memoizing suppliers and 13 ItemStacks: 0.42% (steady) to 0.54% (flight) self time of the render thread here.
 *
 * What the switch does. The books are kept in arrays in the same order (Book[] and the ItemStack each one's getBookItem()
 * returned), with an index from item to the books whose stack showed that item. A non-empty stack whose item no book stack
 * showed (and that is not a book item) is answered null at once; a stack with candidate books checks only those, in order,
 * with the same ItemStack.is call; an empty stack checks the remembered stacks for an empty one, in order (an emptied book
 * stack reads as air, which matches an empty stack, as in Patchouli's loop).
 *
 * Why the answer is the same. Book.getBookItem() returns the value of a Guava Suppliers.memoize supplier set once in the
 * Book constructor: the same ItemStack object on every call once it was computed. ItemStack.getItem() is the item of the
 * stack's final holder (delegate), or air while the stack is empty, so a book stack can only change between its own item and
 * air: a non-empty query can match only a book whose stack showed the query's item, or one whose stack was empty when the
 * index was made (those are checked on every query, in book order). The first getBookItem() call of a book can have side
 * effects (the stack is created, Forge's capability event fires) or throw (an unknown custom item): until every book's stack
 * is known, the walk calls getBookItem() exactly where Patchouli's loop would (book by book, in map order, stopping at the
 * first match), so each book is computed in the same call as before and an exception leaves from the same point. The arrays
 * are rebuilt when the map's size changes or Patchouli puts a book into it (BookRegistry.loadBook, the only writer of
 * books: booksChanged), so the order is always the map's current iteration order. ItemModBook stacks run Patchouli's code.
 *
 * -Dbons_and_furious.patchouliBookItemPrefilter=false switches it off at run time.
 * -Dbons_and_furious.patchouliBookItemPrefilter.shadow=true (verification runs only): Patchouli's loop answers every call;
 * once every book is known the prefilter's answer is computed beside it, and SHADOW_CHECKS / SHADOW_MISMATCHES count the
 * calls whose answers differ (WARN for the first 20).
 */
public final class PatchouliBooks {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.patchouliBookItemPrefilter", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.patchouliBookItemPrefilter.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final int[] NO_BOOKS = new int[0];
    private static volatile int epoch;
    private static volatile Snapshot snapshot;
    private static volatile boolean announced;

    /**
     * The books in map order with what is known of their stacks: stacks[i] == null means book i's getBookItem() has not been
     * called by the walk yet. index and loose (book indices whose stack was empty when indexed) exist once complete.
     */
    private record Snapshot(Map<?, ?> map, int size, int epoch, Book[] books, ItemStack[] stacks, boolean complete,
                            IdentityHashMap<Item, int[]> index, int[] loose) {}

    private PatchouliBooks() {
    }

    /** BookRegistry.loadBook: Patchouli put a book into the map (a new key or a replaced one). */
    public static void booksChanged() {
        epoch++;
    }

    /** ItemStackUtil.getBookFromStack, wrapped (@WrapMethod). */
    public static Book bookFromStack(ItemStack stack, Operation<Book> original) {
        if (!enabled || SHADOW) {
            Book stock = original.call(stack);
            if (SHADOW && enabled) shadow(stack, stock);
            return stock;
        }
        Item item = stack.m_41720_();                       // Patchouli's first call, made the same way
        if (item instanceof ItemModBook) return original.call(stack);
        Snapshot s = current(BookRegistry.INSTANCE.books);
        if (!s.complete()) return walk(s, stack);
        if (!announced) announce();
        return lookup(s, item);
    }

    private static Snapshot current(Map<?, Book> map) {
        Snapshot s = snapshot;
        int size = map.size(), e = epoch;
        if (s != null && s.map() == map && s.size() == size && s.epoch() == e) return s;
        Book[] books = map.values().toArray(new Book[0]);
        s = books.length == 0 ? new Snapshot(map, 0, e, books, new ItemStack[0], true, new IdentityHashMap<>(), NO_BOOKS)
                : new Snapshot(map, books.length, e, books, new ItemStack[books.length], false, null, NO_BOOKS);
        snapshot = s;
        return s;
    }

    /** A complete snapshot: the candidate books for this item, in book order, checked with Patchouli's own comparison. */
    static Book lookup(Snapshot s, Item item) {
        ItemStack[] stacks = s.stacks();
        if (item == Items.f_41852_) {                         // an empty stack: the first book whose stack reads as air
            for (int i = 0; i < stacks.length; i++) if (stacks[i].m_150930_(item)) return s.books()[i];
            return null;
        }
        int[] cand = s.index().getOrDefault(item, NO_BOOKS), loose = s.loose();
        if (loose.length == 0) {
            for (int i : cand) if (stacks[i].m_150930_(item)) return s.books()[i];
            return null;
        }
        int a = 0, b = 0;                                     // merge the two ascending lists: book order
        while (a < cand.length || b < loose.length) {
            int i = b >= loose.length || a < cand.length && cand[a] < loose[b] ? cand[a++] : loose[b++];
            if (stacks[i].m_150930_(item)) return s.books()[i];
        }
        return null;
    }

    /**
     * Not every stack is known yet: Patchouli's loop, book by book in map order, with getBookItem() called only for the books
     * not seen before (a remembered stack is the object getBookItem() returns) and the same ItemStack.isSameItem call; it
     * stops at the first match, as Patchouli's does. What it learned is kept even when getBookItem() throws.
     */
    private static Book walk(Snapshot s, ItemStack stack) {
        ItemStack[] known = s.stacks().clone();
        try {
            for (int i = 0; i < known.length; i++) {
                ItemStack st = known[i];
                if (st == null) known[i] = st = s.books()[i].getBookItem();
                if (ItemStack.m_41656_(st, stack)) return s.books()[i];
            }
            return null;
        } finally {
            publish(s, known);
        }
    }

    /** A new snapshot of the same books with what the walk learned; complete (with its index) when every stack is known. */
    private static void publish(Snapshot s, ItemStack[] known) {
        if (snapshot != s) return;                            // the books changed meanwhile: the next call starts again
        for (ItemStack st : known) {
            if (st == null) {
                snapshot = new Snapshot(s.map(), s.size(), s.epoch(), s.books(), known, false, null, NO_BOOKS);
                return;
            }
        }
        IdentityHashMap<Item, int[]> index = new IdentityHashMap<>();
        List<Integer> loose = new ArrayList<>();
        for (int i = 0; i < known.length; i++) {
            if (known[i].m_41619_()) {
                loose.add(i);
                continue;
            }
            Item it = known[i].m_41720_();
            int[] old = index.get(it);
            int[] grown = old == null ? new int[1] : Arrays.copyOf(old, old.length + 1);
            grown[grown.length - 1] = i;
            index.put(it, grown);
        }
        snapshot = new Snapshot(s.map(), s.size(), s.epoch(), s.books(), known, true, index, loose.stream().mapToInt(Integer::intValue).toArray());
    }

    /** Shadow mode: Patchouli's loop answered (stock); compare the prefilter's answer once every stack is known. */
    private static void shadow(ItemStack stack, Book stock) {
        Item item = stack.m_41720_();
        if (item instanceof ItemModBook) return;
        Snapshot s = current(BookRegistry.INSTANCE.books);
        if (!s.complete()) {
            // Patchouli's loop has just run: every book before its match (all of them when it found none) is computed, so
            // asking those again is free of side effects; the rest is learned by later calls
            ItemStack[] known = s.stacks().clone();
            for (int i = 0; i < known.length; i++) {
                if (known[i] == null) known[i] = s.books()[i].getBookItem();
                if (s.books()[i] == stock) break;
            }
            publish(s, known);
            return;
        }
        Book ours = lookup(s, item);
        SHADOW_CHECKS.incrementAndGet();
        if (ours != stock) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: patchouli_book_item_prefilter shadow mismatch #{} for {}: Patchouli {}, prefilter {}",
                    n, stack, String.valueOf(stock), String.valueOf(ours));
        }
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: patchouli_book_item_prefilter applies (guide book lookups answered from an index of the loaded books' items)");
    }
}
