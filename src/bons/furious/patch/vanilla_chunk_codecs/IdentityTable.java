package bons.furious.patch.vanilla_chunk_codecs;

/**
 * Group vanilla_chunk_codecs: a small identity-keyed table for values kept per Minecraft object (block, block state,
 * biome holder). Open addressing on System.identityHashCode; reads take no lock (a volatile array of immutable Entry
 * records; a reader sees a slot either empty or holding a complete entry, by final-field semantics), writes take the
 * table's lock (one reference store into an empty slot, or a new array published through the volatile field when the
 * table is half full). Keys are compared by identity, so equals/hashCode overrides of a key's class do not matter.
 * Keys are kept strongly: they are registry objects that live as long as the game.
 */
final class IdentityTable {
    record Entry(Object key, Object value) {
    }

    private volatile Entry[] table = new Entry[256];
    private int size;

    Object get(Object key) {
        Entry[] t = this.table;
        int mask = t.length - 1;
        for (int i = System.identityHashCode(key) & mask; ; i = (i + 1) & mask) {
            Entry e = t[i];
            if (e == null) return null;
            if (e.key() == key) return e.value();
        }
    }

    synchronized void put(Object key, Object value) {
        Entry entry = new Entry(key, value);
        Entry[] t = this.table;
        int mask = t.length - 1;
        for (int i = System.identityHashCode(key) & mask; ; i = (i + 1) & mask) {
            Entry e = t[i];
            if (e == null) {
                if ((this.size + 1) * 2 > t.length) break;
                t[i] = entry;
                this.size++;
                return;
            }
            if (e.key() == key) {
                t[i] = entry;
                return;
            }
        }
        Entry[] grown = new Entry[t.length * 2];
        int gm = grown.length - 1;
        int n = 0;
        for (Entry e : t) {
            if (e == null || e.key() == key) continue;
            int i = System.identityHashCode(e.key()) & gm;
            while (grown[i] != null) i = (i + 1) & gm;
            grown[i] = e;
            n++;
        }
        int i = System.identityHashCode(key) & gm;
        while (grown[i] != null) i = (i + 1) & gm;
        grown[i] = entry;
        this.size = n + 1;
        this.table = grown;
    }

    synchronized int size() {
        return this.size;
    }
}
