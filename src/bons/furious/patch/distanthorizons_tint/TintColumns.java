package bons.furious.patch.distanthorizons_tint;

import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_tint_neighbour_index (Distant Horizons 3.3.2, LGPL-3.0; client). No Distant
 * Horizons code here.
 *
 * For the nearest LODs (data no wider than DH's lodBiomeBlending radius, 3 blocks in this pack), DH's tint getter
 * (AbstractDhTintGetter_forge.tryGetBlockTint) averages the biome colour over the (2r+1)^2 = 49 neighbouring columns of
 * every tinted data point and asks FullDataSourceV2.getDataPointAtBlockPos(x, y, z, levelMinY) for each. Every such call
 * re-derives the data source's geometry: it packs the block into a detail-0 position, allocates a DhBlockPos to find the
 * 64-block section that contains it, decodes and compares four corner coordinates (DhSectionPos.contains, each corner
 * through getCenterBlockPos), decodes the data source's detail level, converts the block to the relative column with
 * getDhSectionRelativePositionForDetailLevel, packs and unpacks that again, and only then scans the column for the data
 * point that holds y. 21% of DH's render-loader samples sit in this method while LODs arrive.
 *
 * The redirected call (TintColumnsMixin) gives the same answer with the geometry that depends only on the data source
 * computed once per tryGetBlockTint call (DH's own DhSectionPos.getMinCornerBlockX/Z, getBlockWidth and getDetailLevel on
 * the data source's position, kept in a Memo shared by this one call and recomputed whenever the position differs) and
 * the per-block part spelled out with DH's exact integer arithmetic: the containing section x = x < 0 ? (x + 1) / 64 - 1
 * : x / 64 (getXOrZSectionPosFromChunkOrBlockPos), cut to DhSectionPos' 28-bit field (x << 4 >> 4), its min corner x * 64
 * (getCenterBlockPos - half the width; the wrap-around of int arithmetic included), DH's comparisons, and DH's relative
 * column: the block's 28-bit x, plus (2^d - 1, at least 1) when negative, divided by 2^d, then % 64 or 63 + % 64 for
 * negative blocks - DH's own rounding, kept even where it is not a floor. The column comes from DH's getColumnAtRelPos and
 * is scanned exactly as DH scans it (size re-read each step, empty points skipped, bottom <= y - levelMinY < bottom +
 * height). Every column is read afresh: nothing from one lookup is reused for another. Data detail levels outside 0..30
 * (where DH throws or 2^d leaves int range) and the runtime switch off go to DH's own method.
 *
 * -Dbons_and_furious.dhTintColumns=false asks DH every time; -Dbons_and_furious.dhTintColumns.shadow=true (verification
 * runs only) asks DH as well and returns DH's answer, counting answers that differ (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class TintColumns {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhTintColumns", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.dhTintColumns.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** The data-source geometry of one tryGetBlockTint call (one per call, @Share). */
    public static final class Memo {
        long pos;
        boolean valid, fallback;
        int minX, maxX, minZ, maxZ;   // DhSectionPos.contains(dataSourcePos, ...) bounds
        int width, offset;            // 2^d and max(1, 2^d - 1) of getDhSectionRelativePositionForDetailLevel
    }

    private TintColumns() {
    }

    /** In place of src.getDataPointAtBlockPos(x, y, z, levelMinY) in AbstractDhTintGetter_forge.tryGetBlockTint. */
    public static long dataPoint(FullDataSourceV2 src, int x, int y, int z, int levelMinY, LocalRef<Memo> ref) {
        if (!enabled) return src.getDataPointAtBlockPos(x, y, z, levelMinY);
        long pos = src.getPos();
        Memo m = ref.get();
        if (m == null) {
            m = new Memo();
            ref.set(m);
        }
        if (!m.valid || m.pos != pos) init(m, pos);
        if (m.fallback) return src.getDataPointAtBlockPos(x, y, z, levelMinY);
        long ours = lookup(src, m, x, y, z, levelMinY);
        if (SHADOW) {
            long dh = src.getDataPointAtBlockPos(x, y, z, levelMinY);
            SHADOW_CHECKS.incrementAndGet();
            if (dh != ours && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: distanthorizons_tint_neighbour_index shadow mismatch at {},{},{} in {}: {} vs DH's {}",
                        x, y, z, DhSectionPos.toString(pos), ours, dh);
            return dh;
        }
        return ours;
    }

    private static void init(Memo m, long pos) {
        m.pos = pos;
        m.valid = true;
        m.minX = DhSectionPos.getMinCornerBlockX(pos);
        m.minZ = DhSectionPos.getMinCornerBlockZ(pos);
        int widthMinusOne = DhSectionPos.getBlockWidth(pos) - 1;
        m.maxX = m.minX + widthMinusOne;
        m.maxZ = m.minZ + widthMinusOne;
        byte d = (byte) (DhSectionPos.getDetailLevel(pos) - 6);
        m.fallback = d < 0 || d > 30;
        m.width = BitShiftUtil.powerOfTwo(d);
        m.offset = Math.max(1, m.width - 1);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_tint_neighbour_index finds LOD tint neighbours without re-deriving the data source's geometry{}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    private static long lookup(FullDataSourceV2 src, Memo m, int x, int y, int z, int levelMinY) {
        // DhSectionPos.contains(pos, encodeContaining(6, block)): the block's 64-block section, its min corner
        int sx = x < 0 ? (x + 1) / 64 - 1 : x / 64;
        int sz = z < 0 ? (z + 1) / 64 - 1 : z / 64;
        int bMinX = (sx << 4 >> 4) * 64;
        int bMinZ = (sz << 4 >> 4) * 64;
        if (!(m.minX <= bMinX && bMinX <= m.maxX && m.minZ <= bMinZ && bMinZ <= m.maxZ)) return 0L;
        // getDhSectionRelativePositionForDetailLevel(encode(0, x, z), d)
        int xd = x << 4 >> 4, zd = z << 4 >> 4;
        int xRel = (xd + (xd < 0 ? m.offset : 0)) / m.width;
        int zRel = (zd + (zd < 0 ? m.offset : 0)) / m.width;
        xRel = xd >= 0 ? xRel % 64 : 63 + xRel % 64;
        zRel = zd >= 0 ? zRel % 64 : 63 + zRel % 64;
        LongArrayList column = src.getColumnAtRelPos(xRel, zRel);
        if (column == null) return 0L;
        int relY = y - levelMinY;
        for (int i = 0; i < column.size(); i++) {
            long point = column.getLong(i);
            if (point != 0L) {
                int bottom = FullDataPointUtil.getBottomY(point);
                int top = bottom + FullDataPointUtil.getHeight(point);
                if (bottom <= relY && relY < top) return point;
            }
        }
        return 0L;
    }
}
