package bons.furious.patch.vanilla;

import net.minecraft.server.level.TickingTracker;

/**
 * Bons and Furious switch vanilla_block_ticking_range_memo (Minecraft 1.21.1 with NeoForge 21.1.252; server side). Mojang
 * member names.
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
 *
 * Since 1.0.30 the memo is used only for a tracker of exactly the class TickingTracker. A subclass (C2ME's notickvd module
 * swaps one in on 1.20.1, NoOPTickingMap) can override setLevel and getLevel without calling them, so the version would not
 * move when its levels change; such a tracker is asked every time, as without the switch. The check is one class compare on
 * the tracker object the lookup reads anyway.
 *
 * Ported to 1.21.1: no logic change; the 1.0.30 class check is carried over as is. DistanceManager still creates its
 * tracker with {@code new TickingTracker()} and no class in Minecraft, NeoForge or the pinned target jars extends
 * TickingTracker. C2ME 0.4.0-alpha.0.122's notickvd module no longer swaps in a subclass: by its mixin metadata it changes
 * constants inside TickingTracker.setLevel and getTicketLevelAt (@ModifyConstant) and only reads the level map through an
 * accessor (containsKey), so the tracker stays exactly TickingTracker, every map write still runs between the two version
 * bumps, and the memo stays exact with that module on.
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
        if (!enabled || tracker.getClass() != TickingTracker.class) return t.bons$lookup(chunk);
        int version = t.bons$version();
        if (t.bons$memo() instanceof Memo m && m.chunk() == chunk && m.version() == version) return m.level();
        int level = t.bons$lookup(chunk);
        t.bons$memo(new Memo(chunk, level, version));
        return level;
    }
}
