package org.valkyrienskies.mod.common.util;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** One solver invocation only. No shared state, polygon cache or retained ships. */
public final class AcVsAxes {
    private final long[] key = new long[18];
    private List<Vector3dc> cached;
    private int consecutiveMisses;
    private boolean disabled;

    private AcVsAxes() {}

    public static AcVsAxes forCandidates(int count) {
        return count < 4 ? null : new AcVsAxes();
    }

    public static List<Vector3dc> axes(Iterable<? extends Vector3dc> normals,
                                       AcVsAxes cache, Vector3dc[] basis) {
        // Only the ordinary three Vector3d normals are memoized. Unknown
        // iterable/vector implementations retain the original call behavior.
        if (cache == null || cache.disabled || basis.length != 3 || !(normals instanceof List<?> list)
                || list.size() != 3) return build(normals, basis, false);
        if (list.getClass() != ArrayList.class
                && !list.getClass().getName().equals("java.util.Arrays$ArrayList"))
            return build(normals, basis, false);
        for (int i = 0; i < 3; i++) {
            if (basis[i].getClass() != Vector3d.class || list.get(i) == null
                    || list.get(i).getClass() != Vector3d.class)
                return build(normals, basis, false);
        }
        boolean same = cache.cached != null;
        for (int i = 0; i < 6; i++) {
            Vector3dc v = i < 3 ? basis[i] : (Vector3dc) list.get(i - 3);
            long x = Double.doubleToRawLongBits(v.x());
            long y = Double.doubleToRawLongBits(v.y());
            long z = Double.doubleToRawLongBits(v.z());
            int at = i * 3;
            same &= cache.key[at] == x && cache.key[at + 1] == y && cache.key[at + 2] == z;
        }
        if (same) {
            cache.consecutiveMisses = 0;
        } else {
            // Highly interleaved orientations can make snapshotting more
            // expensive than rebuilding. Give up for this invocation only.
            if (++cache.consecutiveMisses >= 4) {
                cache.disabled = true;
                return build(normals, basis, false);
            }
            List<Vector3dc> built = build(normals, basis, true);
            for (int i = 0; i < 6; i++) {
                Vector3dc v = i < 3 ? basis[i] : (Vector3dc) list.get(i - 3);
                int at = i * 3;
                cache.key[at] = Double.doubleToRawLongBits(v.x());
                cache.key[at + 1] = Double.doubleToRawLongBits(v.y());
                cache.key[at + 2] = Double.doubleToRawLongBits(v.z());
            }
            cache.cached = built;
        }
        return cache.cached;
    }

    private static List<Vector3dc> build(Iterable<? extends Vector3dc> normals,
                                         Vector3dc[] basis, boolean snapshot) {
        var out = new ArrayList<Vector3dc>();
        for (Vector3dc v : basis) out.add(snapshot ? new Vector3d(v) : v);
        for (Vector3dc normal : normals) {
            out.add(snapshot ? new Vector3d(normal) : normal);
            for (Vector3dc axis : basis) {
                Vector3d cross = normal.cross(axis, new Vector3d()).normalize();
                if (cross.lengthSquared() > 1.0e-6) out.add(cross);
            }
        }
        return out;
    }
}
