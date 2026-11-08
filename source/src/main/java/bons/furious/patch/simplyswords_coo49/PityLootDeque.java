package bons.furious.patch.simplyswords_coo49;

/**
 * Bons and Furious switch simplyswords_pity_loot_deque_keep (Simply Swords 1.70.2-1.21.1, server; any thread that unpacks
 * loot).
 *
 * Simply Swords' LootableInventoryMixin wraps every RandomizableContainer.unpackLootTable(Player) call (chests, barrels,
 * hoppers and every other loot-capable container run it on each item read and write and on menu open) with
 * PityLootContext.capture(lootTable) at HEAD and PityLootContext.take() at RETURN. take() calls CAPTURED_TABLES.remove()
 * whenever the thread's deque is empty (on an empty take, and after popping the last entry), so the next capture builds a
 * new ArrayDeque and a new ThreadLocal entry through withInitial(ArrayDeque::new).
 *
 * The switch skips those two remove() calls. The deque left in the ThreadLocal is then empty, which is exactly what
 * withInitial would build on the next get(): capture pushes onto an empty deque either way and take reads an empty deque
 * either way, so every returned table key, every pushed and popped entry and every exception path are the same. The deque
 * is private to PityLootContext and reached only through get(). Memory: one empty ArrayDeque stays per thread that ever
 * unpacked loot (bounded by the thread count). Runtime flag: -Dbons_and_furious.simplyswordsPityLootDequeKeep=false calls
 * remove() as before. Simply Swords' licence (Timefall Development License) is not ours: no Simply Swords code is carried.
 */
public final class PityLootDeque {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsPityLootDequeKeep", "true"));

    private PityLootDeque() {
    }
}
