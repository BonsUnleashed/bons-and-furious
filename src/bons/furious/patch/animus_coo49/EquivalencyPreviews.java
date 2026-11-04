package bons.furious.patch.animus_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch animus_equivalency_preview_memo (since 1.0.49; Animus 3.0.35; client side). Helper of
 * EquivalencyPreviewMemoMixin. Animus is MIT licensed; the search below is our own implementation of the same visiting
 * order, written from the mod's bytecode. SRG names.
 *
 * What the mod does. While the Sigil of Equivalency is held and aimed at a block, SigilEquivalencyRenderer runs
 * findMatchingBlocksInRadius(level, center, target block, radius, face) on every frame: a breadth-first search from the
 * aimed block over the plane of the clicked face, within radius (stack NBT "Radius", else the config, default 1, max
 * 32), with a HashSet, a LinkedList and a new BlockPos per neighbour; for every polled position (all of them pass the
 * plane test, only such neighbours are queued) it reads level.getBlockState(pos).getBlock() == target; a match is added
 * to the result and its four plane neighbours not yet seen are queued; it stops when the queue is empty or (2r+1)^2
 * matches were found. The renderer only iterates the result.
 *
 * What the switch does. The search is a deterministic function of the outcomes of those reads, in the order they are
 * made. The switch keeps, for the last search (key: the level, the aimed position, the target block, the radius, the
 * face), every polled position in order with its outcome. On the next frame with the same key it reads those positions
 * again, in the same order: while every outcome is the same, the original would poll exactly these positions (by
 * induction) and return the same matches, so the kept matches are returned (in a new list, as the original returns a new
 * list) with no set, queue or position objects made. At the first outcome that differs, the search continues from that
 * point with the reads it still has to make (the queue and seen set rebuilt from the kept outcomes, no position read
 * twice), and the kept record is replaced. The level is read exactly as by the original: the same positions, in the same
 * order, each once. Difference in objects only: the returned list holds the kept BlockPos objects (equal positions) and
 * the aimed position of the first frame of a key; the renderer reads coordinates only.
 *
 * -Dbons_and_furious.animusEquivalencyPreviewMemo=false runs the original search every frame.
 * SHADOW MODE (verification only): -Dbons_and_furious.animusEquivalencyPreviewMemo.shadow=true runs the original as well
 * and compares the two lists (positions and order); the original's list is returned. SHADOW_CHECKS / SHADOW_MISMATCHES.
 */
public final class EquivalencyPreviews {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.animusEquivalencyPreviewMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.animusEquivalencyPreviewMemo.shadow");
    public static final java.util.concurrent.atomic.AtomicLong SHADOW_CHECKS = new java.util.concurrent.atomic.AtomicLong(),
            SHADOW_MISMATCHES = new java.util.concurrent.atomic.AtomicLong();
    /** Statistics for probes (plain fields): searches answered from the record, searches continued after a change. */
    public static long reused, continued;
    private static volatile boolean announced;
    /** The last search (render thread only; replaced as a whole). */
    private static volatile Memo last;

    private EquivalencyPreviews() {
    }

    /** One search: its key, every polled position with its outcome in polling order, and its matches. */
    private static final class Memo {
        final WeakReference<Level> level;
        final BlockPos center;
        final Block target;
        final int radius;
        final Direction face;
        final BlockPos[] polled;
        final boolean[] matched;
        final int n;
        final BlockPos[] matches;
        final int matchCount;

        Memo(Level level, BlockPos center, Block target, int radius, Direction face, BlockPos[] polled, boolean[] matched, int n,
             BlockPos[] matches, int matchCount) {
            this.level = new WeakReference<>(level);
            this.center = center;
            this.target = target;
            this.radius = radius;
            this.face = face;
            this.polled = polled;
            this.matched = matched;
            this.n = n;
            this.matches = matches;
            this.matchCount = matchCount;
        }

        boolean sameKey(Level l, BlockPos c, Block t, int r, Direction f) {
            return this.level.get() == l && this.target == t && this.radius == r && this.face == f && this.center.equals(c);
        }
    }

    /** SigilEquivalencyRenderer.findMatchingBlocksInRadius(level, center, target, radius, face), wrapped. */
    public static List<BlockPos> find(Level level, BlockPos center, Block target, int radius, Direction face, Operation<List<BlockPos>> original) {
        // positions are kept as packed longs (BlockPos.asLong): only where every reachable position packs uniquely, and for
        // the radii the mod's config allows (an edited stack NBT radius beyond 1,024 runs the original)
        if (!enabled || level == null || center == null || face == null || radius < 0 || radius > 1024
                || Math.abs(center.m_123341_()) > 33_000_000 || Math.abs(center.m_123343_()) > 33_000_000
                || center.m_123342_() < -2000 || center.m_123342_() > 2000) return original.call(level, center, target, radius, face);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: animus_equivalency_preview_memo applies (the Sigil of Equivalency preview re-reads its last search instead of rebuilding it every frame){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        List<BlockPos> ours = search(level, center, target, radius, face);
        if (SHADOW) {
            List<BlockPos> theirs = original.call(level, center, target, radius, face);
            SHADOW_CHECKS.incrementAndGet();
            if (!theirs.equals(ours)) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: animus_equivalency_preview_memo shadow mismatch #{} at {}: {} positions kept, {} by the original",
                        m, center, ours.size(), theirs.size());
            }
            return theirs;
        }
        return ours;
    }

    /** The search with the record (see the class comment). Public for the offline proof. */
    public static List<BlockPos> search(Level level, BlockPos center, Block target, int radius, Direction face) {
        Memo m = last;
        int prefix = 0;
        boolean changedOutcome = false;
        if (m != null && m.sameKey(level, center, target, radius, face)) {
            for (; prefix < m.n; prefix++) {
                boolean hit = level.m_8055_(m.polled[prefix]).m_60734_() == target;
                if (hit != m.matched[prefix]) {
                    changedOutcome = hit;
                    break;
                }
            }
            if (prefix == m.n) {
                reused++;
                List<BlockPos> out = new ArrayList<>(m.matchCount);
                for (int i = 0; i < m.matchCount; i++) out.add(m.matches[i]);
                return out;
            }
            continued++;
        } else {
            m = null;
        }
        return run(level, center, target, radius, face, m, prefix, changedOutcome);
    }

    /** True when pos lies on the face's plane through center, within radius (the mod's isOnSamePlaneAndInRadius). */
    static boolean onPlane(int x, int y, int z, BlockPos c, int radius, Direction face) {
        int dx = Math.abs(x - c.m_123341_()), dy = Math.abs(y - c.m_123342_()), dz = Math.abs(z - c.m_123343_());
        if (face == Direction.UP || face == Direction.DOWN) return y == c.m_123342_() && Math.max(dx, dz) <= radius;
        if (face == Direction.NORTH || face == Direction.SOUTH) return z == c.m_123343_() && Math.max(dx, dy) <= radius;
        return x == c.m_123341_() && Math.max(dy, dz) <= radius;
    }

    /**
     * The breadth-first search in the original's order. Outcomes of polled positions below {@code known} come from {@code m}
     * (already read this frame), the outcome at {@code known} is {@code knownOutcome} (read this frame too); every later
     * polled position is read here. A new record replaces the last one.
     */
    static List<BlockPos> run(Level level, BlockPos center, Block target, int radius, Direction face, Memo m, int known, boolean knownOutcome) {
        int maxBlocks = (radius * 2 + 1) * (radius * 2 + 1);
        int[][] neighbors = face == Direction.UP || face == Direction.DOWN ? new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}
                : face == Direction.NORTH || face == Direction.SOUTH ? new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}}
                : new int[][] {{0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        int cap = Math.max(16, Math.min(maxBlocks, 4096) + 4);
        BlockPos[] queue = new BlockPos[cap];
        int head = 0, tail = 0;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        BlockPos[] polled = new BlockPos[cap];
        boolean[] matched = new boolean[cap];
        int n = 0;
        ArrayList<BlockPos> matches = new ArrayList<>();
        queue[tail++] = center;
        seen.add(center.m_121878_());
        while (head < tail && matches.size() < maxBlocks) {
            BlockPos current = queue[head++];
            if (!onPlane(current.m_123341_(), current.m_123342_(), current.m_123343_(), center, radius, face)) continue;
            boolean hit;
            if (m != null && n < known) hit = m.matched[n];
            else if (m != null && n == known) hit = knownOutcome;
            else hit = level.m_8055_(current).m_60734_() == target;
            if (n == polled.length) {
                polled = java.util.Arrays.copyOf(polled, n * 2);
                matched = java.util.Arrays.copyOf(matched, n * 2);
            }
            polled[n] = current;
            matched[n] = hit;
            n++;
            if (!hit) continue;
            matches.add(current.m_7949_());
            for (int[] o : neighbors) {
                int x = current.m_123341_() + o[0], y = current.m_123342_() + o[1], z = current.m_123343_() + o[2];
                long key = BlockPos.m_121882_(x, y, z);
                if (seen.contains(key) || !onPlane(x, y, z, center, radius, face)) continue;
                seen.add(key);
                if (tail == queue.length) queue = java.util.Arrays.copyOf(queue, queue.length * 2);
                queue[tail++] = new BlockPos(x, y, z);
            }
        }
        BlockPos[] keep = matches.toArray(new BlockPos[0]);
        last = new Memo(level, center, target, radius, face, polled, matched, n, keep, keep.length);
        return matches;
    }
}
