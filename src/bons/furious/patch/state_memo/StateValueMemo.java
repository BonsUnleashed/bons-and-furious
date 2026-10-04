package bons.furious.patch.state_memo;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Shared table of the state_memo group (switches particular_chest_waterlogged_memo and spawn_sealife_waterlogged_memo).
 * No code of any mod or of Minecraft here.
 *
 * A block state's property values never change (StateHolder.values is final; FerriteCore's replacement map reads the
 * state's fixed index), so getValue(WATERLOGGED) of a state is the same value object for the state's lifetime. This table
 * remembers it per state: 1,024 slots keyed by the state's identity (up to 8 slots probed from its home slot), each slot
 * one immutable (state, value) record, so a slot read is always a complete pair. A miss asks getValue as before (which
 * keeps its exception for a state without the property; nothing is remembered then) and stores the answer in the first
 * free slot of the window; when the window is full the table starts over empty (any thread may still read the older
 * table; its entries stay valid). Only the few states of the blocks the two switches serve are ever asked, so in play
 * the table fills once. Callers pass WATERLOGGED only.
 */
public final class StateValueMemo {
    /** Table size (power of two) and probe window. */
    public static final int SLOTS = 1024, PROBES = 8;
    private static Entry[] table = new Entry[SLOTS];

    private StateValueMemo() {
    }

    /** One remembered answer: a state and the value its getValue returned. Immutable. */
    private record Entry(BlockState state, Comparable<?> value) {
    }

    /** The value remembered for the state, or null (getValue never returns null, so null means not remembered). */
    public static Comparable<?> find(BlockState state) {
        Entry[] t = table;
        int home = System.identityHashCode(state);
        for (int p = 0; p < PROBES; p++) {
            Entry e = t[(home + p) & (SLOTS - 1)];
            if (e == null) return null;
            if (e.state == state) return e.value;
        }
        return null;
    }

    /** getValue(property) as before, then remembered for the state. */
    public static Comparable<?> ask(BlockState state, Property<?> property) {
        Comparable<?> value = state.m_61143_(property);    // throws as before for a state without the property
        Entry fresh = new Entry(state, value);
        Entry[] t = table;
        int home = System.identityHashCode(state);
        for (int p = 0; p < PROBES; p++) {
            int slot = (home + p) & (SLOTS - 1);
            if (t[slot] == null) {
                t[slot] = fresh;
                return value;
            }
        }
        Entry[] empty = new Entry[SLOTS];
        empty[home & (SLOTS - 1)] = fresh;
        table = empty;
        return value;
    }
}
