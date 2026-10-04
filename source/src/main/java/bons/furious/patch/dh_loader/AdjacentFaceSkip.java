package bons.furious.patch.dh_loader;

import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnRenderView;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_adjacent_face_skip (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge,
 * tested build DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar, LGPL-3.0; client). No Distant Horizons code here.
 *
 * DH builds the side faces of an LOD box (one render data point of a column, from yMin up to yMax) in
 * ColumnBox.makeAdjVerticalQuad: it starts from one light segment [yMin, yMax) and walks EVERY data point of the adjacent
 * column (plus its void point); for each point it applies the point's light to the range the point covers
 * ([pointMinY, pointMaxY)) and the sky light to the gap above it ([pointMaxY, minY of the point above)) with
 * applyLightToRangeAndPopulateNewSgements, which rebuilds the segment list (a segment that overlaps the range is split,
 * every other segment is copied unchanged). Points lying above the face are skipped by DH itself; points lying below it
 * are not, so each side face of each box copies its segment list twice for every adjacent point beneath it.
 *
 * Every segment starts at or above yMin: the first one does, and a split only creates pieces that start at the old start,
 * at max(old start, range start) or at the range end inside the segment. A range whose end is at or below yMin therefore
 * overlaps no segment (startY >= rangeEnd for all of them) and the call returns a copy of the list. So a point whose maxY
 * is at or below yMin and whose gap above ends at or below yMin (the minY of the point above, 0 for the first point) changes
 * nothing: both of its calls are copies (the second is not even made when the gap is empty). For such a point
 * {@link #point} hands DH an empty data point (0L), which DH's loop skips at once (doesDataPointExist). The point above is
 * read again by DH for the next point (a separate read), the void point is never touched, and the segments that DH turns
 * into faces are identical; only which of DH's two pooled scratch lists holds them differs, and both are cleared at the
 * start of every call.
 *
 * The same loop also walks every empty slot after the adjacent column's last point (a view holds up to the detail level's
 * vertical slice count, 16 at the finest level with HIGH quality, while real columns hold one or two points); its bound
 * now stops after the last non-empty slot ({@link #bound}), see there for why that is the same loop.
 *
 * Ported to 1.21.1: no change. ColumnBox (makeAdjVerticalQuad, applyLightToRangeAndPopulateNewSgements and every other
 * method), ColumnBox$YSegmentUtil, ColumnRenderView (methods and its public data / dataOffset / size fields) and
 * RenderDataPointUtil are byte-identical in DH 3.3.3, so the anchors (GETFIELD size ordinal 1, get(I)J ordinal 1) and the
 * captured locals (segments = 16, adjIndex = 22) are the same as on 3.3.2.
 */
public final class AdjacentFaceSkip {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dhAdjacentFaceSkip=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhAdjacentFaceSkip", "true"));
    /** Shadow mode for rigs: every skip is checked against the actual segment list (both calls must be copies); a failed check is not skipped. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.dhAdjacentFaceSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private AdjacentFaceSkip() {
    }

    /**
     * The value of adjColumnView.get(adjIndex) at the top of makeAdjVerticalQuad's loop ({@code point}, read by DH).
     * {@code yMin} is the face's bottom, {@code segments} DH's current segment list (read only, for the shadow check).
     */
    public static long point(long point, ColumnRenderView view, int index, short yMin, LongArrayList segments) {
        if (point == 0L || !enabled) return point;
        if (RenderDataPointUtil.getYMax(point) <= yMin) {
            int aboveMinY = index > 0 ? RenderDataPointUtil.getYMin(view.get(index - 1)) : 0;
            if (aboveMinY <= yMin) {
                if (SHADOW && !shadow(point, aboveMinY, segments)) return point;
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: distanthorizons_adjacent_face_skip skips adjacent LOD points below a side face");
                }
                return 0L;
            }
        }
        return point;
    }

    /**
     * The value of adjColumnView.size where makeAdjVerticalQuad stores it as its loop bound (adjCount). A column view
     * holds its points from index 0 and empty (0L) slots after them; DH's loop reads every one of those trailing empty
     * slots and skips it at once (doesDataPointExist), and the last real point's below-neighbour, get(i + 1) when
     * i + 1 < adjCount, is 0 there and therefore replaced by getVoid(), which is what it gets when adjCount stops right after
     * it. So the bound becomes one past the last non-empty slot, found by scanning the view's backing array from the end.
     * The scan needs the slots inside the list's size; otherwise the bound stays as it is (and DH's own reads report the
     * bad view as before).
     */
    public static int bound(int size, ColumnRenderView view) {
        if (!enabled || size <= 0) return size;
        LongArrayList data = view.data;
        int base = view.dataOffset;
        if (data == null || base < 0 || base + size > data.size()) return size;
        long[] a = data.elements();
        int n = size;
        while (n > 0 && a[base + n - 1] == 0L) n--;
        if (SHADOW && !shadowBound(view, n, size)) return size;
        return n;
    }

    private static boolean shadowBound(ColumnRenderView view, int bound, int size) {
        SHADOW_CHECKS.incrementAndGet();
        for (int i = bound; i < size; i++) {
            if (view.get(i) != 0L) {
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: distanthorizons_adjacent_face_skip shadow check: slot {} of {} is not empty after the bound {}", i, size, bound);
                return false;
            }
        }
        return true;
    }

    /** True when both of DH's calls for this point would copy every segment unchanged. */
    private static boolean shadow(long point, int aboveMinY, LongArrayList segments) {
        SHADOW_CHECKS.incrementAndGet();
        int minY = RenderDataPointUtil.getYMin(point), maxY = RenderDataPointUtil.getYMax(point);
        boolean secondCall = maxY < aboveMinY;
        for (int i = 0; i < segments.size(); i++) {
            long seg = segments.getLong(i);
            short startY = (short) (seg & 0xFFFFL), endY = (short) (seg >> 16 & 0xFFFFL);
            boolean firstCopies = endY <= minY || startY >= maxY;
            boolean secondCopies = !secondCall || endY <= maxY || startY >= aboveMinY;
            if (!firstCopies || !secondCopies) {
                if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: distanthorizons_adjacent_face_skip shadow check: point [{}..{}) with gap to {} would change segment [{}..{})",
                            minY, maxY, aboveMinY, startY, endY);
                return false;
            }
        }
        return true;
    }
}
