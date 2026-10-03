package bons.furious.patch.vanilla;

/**
 * Bons and Furious switch vanilla_block_ticking_range_memo (vanilla 1.20.1 server). SRG member names.
 *
 * Level.tickBlockEntities asks shouldTickBlocksAt for every ticking block entity, and ServerLevel answers through
 * DistanceManager.inBlockTickingRange, which looks the chunk up in TickingTracker's Long2ByteMap of ticking levels
 * (inEntityTickingRange does the same lookup for entity checks). Block entities of one chunk sit next to each other in the
 * ticker list, so the same chunk is looked up again and again with nothing changed in between.
 *
 * TickingTracker.setLevel (setLevel) is the only code that writes that map, and it now advances a version counter before
 * and after each write. Each tracker remembers its last lookup as one immutable (chunk, level, version) object; a lookup of
 * the same chunk with an unchanged version returns the remembered level, anything else reads the map. The version moves
 * on both sides of a write, so a lookup that overlapped a write can never be reused afterwards.
 */
public final class TickingLevels {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.tickingRangeMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tickingRangeMemo", "true"));

    /** Implemented by TickingTracker through the mixin. */
    public interface Tracker {
        int bons$version();

        Object bons$memo();

        void bons$memo(Object memo);

        /** TickingTracker.getLevel(long), the map lookup. */
        int bons$lookup(long chunk);
    }

    record Memo(long chunk, int level, int version) {
    }

    private TickingLevels() {
    }

    /** In place of TickingTracker.getLevel inside DistanceManager.inBlockTickingRange / inEntityTickingRange. */
    public static int level(Object tracker, long chunk) {
        Tracker t = (Tracker) tracker;
        if (!enabled) return t.bons$lookup(chunk);
        int version = t.bons$version();
        if (t.bons$memo() instanceof Memo m && m.chunk() == chunk && m.version() == version) return m.level();
        int level = t.bons$lookup(chunk);
        t.bons$memo(new Memo(chunk, level, version));
        return level;
    }
}
