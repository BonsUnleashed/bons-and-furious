package org.valkyrienskies.mod.common.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import kotlin.Pair;
import kotlin.jvm.internal.Intrinsics;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4dc;
import org.joml.Vector3ic;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.QueryableShipData;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet;
import org.valkyrienskies.core.api.util.functions.IntBinaryConsumer;
import org.valkyrienskies.core.impl.chunk_tracking.ShipActiveChunksSet;
import org.valkyrienskies.core.impl.shadow.DM;
import org.valkyrienskies.core.impl.shadow.ET;
import org.valkyrienskies.core.impl.shadow.In;
import org.valkyrienskies.core.internal.VsiCore;
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;
import org.valkyrienskies.core.util.AABBdUtilKt;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck;

/**
 * Seventh-sweep helpers (Bons Valkyrien Fixes 1.3.0). Every entry point computes the value the VS 2.4.11 code
 * computes: it either skips work whose result is already known exactly, or reads the same data in bulk.
 */
public final class AcVsSweep7 {
    private AcVsSweep7() {}

    // ---- isCollidingWithUnloadedShips: cached chunk box per active-chunk-set version ---------------------------

    /**
     * The x/z extents of getShipyardChunkAABBAround's union for one version of a ship's active-chunk set. The
     * union is componentwise min/max, so its x/z fields do not depend on the y arguments; any = the set was
     * non-empty (otherwise every field keeps new AABBi()'s sentinel).
     */
    static final class Extents {
        final long mods;
        final boolean any;
        final int minX, minZ, maxX, maxZ;

        Extents(long mods, boolean any, int minX, int minZ, int maxX, int maxZ) {
            this.mods = mods;
            this.any = any;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }
    }

    /** toAABBd(chunk box).transform(shipToWorld) for exactly this chunk box and these twelve matrix elements. */
    static final class WorldBox {
        final int bx0, by0, bz0, bx1, by1, bz1;
        final long m00, m01, m02, m10, m11, m12, m20, m21, m22, m30, m31, m32;
        final double minX, minY, minZ, maxX, maxY, maxZ;

        WorldBox(int bx0, int by0, int bz0, int bx1, int by1, int bz1, Matrix4dc m) {
            this.bx0 = bx0;
            this.by0 = by0;
            this.bz0 = bz0;
            this.bx1 = bx1;
            this.by1 = by1;
            this.bz1 = bz1;
            m00 = Double.doubleToRawLongBits(m.m00());
            m01 = Double.doubleToRawLongBits(m.m01());
            m02 = Double.doubleToRawLongBits(m.m02());
            m10 = Double.doubleToRawLongBits(m.m10());
            m11 = Double.doubleToRawLongBits(m.m11());
            m12 = Double.doubleToRawLongBits(m.m12());
            m20 = Double.doubleToRawLongBits(m.m20());
            m21 = Double.doubleToRawLongBits(m.m21());
            m22 = Double.doubleToRawLongBits(m.m22());
            m30 = Double.doubleToRawLongBits(m.m30());
            m31 = Double.doubleToRawLongBits(m.m31());
            m32 = Double.doubleToRawLongBits(m.m32());
            AABBi box = new AABBi();
            box.minX = bx0;
            box.minY = by0;
            box.minZ = bz0;
            box.maxX = bx1;
            box.maxY = by1;
            box.maxZ = bz1;
            // The original's two library calls; AABBd.transform reads exactly m00..m32 (not m03/m13/m23/m33).
            AABBd w = AABBdUtilKt.toAABBd(box, new AABBd()).transform(m);
            minX = w.minX;
            minY = w.minY;
            minZ = w.minZ;
            maxX = w.maxX;
            maxY = w.maxY;
            maxZ = w.maxZ;
        }

        boolean matches(int bx0, int by0, int bz0, int bx1, int by1, int bz1, Matrix4dc m) {
            return this.bx0 == bx0 && this.by0 == by0 && this.bz0 == bz0 && this.bx1 == bx1 && this.by1 == by1 && this.bz1 == bz1
                && m30 == Double.doubleToRawLongBits(m.m30()) && m31 == Double.doubleToRawLongBits(m.m31())
                && m32 == Double.doubleToRawLongBits(m.m32()) && m00 == Double.doubleToRawLongBits(m.m00())
                && m01 == Double.doubleToRawLongBits(m.m01()) && m02 == Double.doubleToRawLongBits(m.m02())
                && m10 == Double.doubleToRawLongBits(m.m10()) && m11 == Double.doubleToRawLongBits(m.m11())
                && m12 == Double.doubleToRawLongBits(m.m12()) && m20 == Double.doubleToRawLongBits(m.m20())
                && m21 == Double.doubleToRawLongBits(m.m21()) && m22 == Double.doubleToRawLongBits(m.m22());
        }
    }

    /** getShipyardChunkAABBAround$lambda$0 on a private box; the y arguments only reach the unused y fields. */
    private static final class Union implements IntBinaryConsumer {
        final AABBi box = new AABBi();
        int minY, maxY;
        boolean any;

        @Override
        public void accept(int x, int z) {
            any = true;
            int minX = SectionPos.m_123223_(x);
            int minZ = SectionPos.m_123223_(z);
            int maxX = SectionPos.m_175554_(x, 15);
            int maxZ = SectionPos.m_175554_(z, 15);
            box.union(minX, minY, minZ).union(maxX, maxY, maxZ);
        }
    }

    private static final class Scratch {
        final AABBd entityBox = new AABBd(), shipBox = new AABBd();
        boolean busy;
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /**
     * EntityShipCollisionUtils.isCollidingWithUnloadedShips after its client-sync and no-ships guards (the
     * sweep-6 loop, which is the original stream unrolled). Same ships in the same order (Eo iterates
     * getIdToShipData().values()), the same getter calls in the same order per ship, the same first failure.
     * What is skipped is pure recomputation: the ship's active-chunk union is reused while the set's modification
     * counter is unchanged (every mutation of the set goes through ShipActiveChunksSet.add/remove, which bump it),
     * and the transformed chunk box is reused while the chunk box and the matrix elements are bit-identical.
     */
    public static boolean collidingWithUnloadedShips(Entity entity, Level level) {
        AABB bb = entity.m_20191_();
        Intrinsics.checkNotNullExpressionValue(bb, "getBoundingBox(...)");
        double x0 = bb.f_82288_, y0 = bb.f_82289_, z0 = bb.f_82290_, x1 = bb.f_82291_, y1 = bb.f_82292_, z1 = bb.f_82293_;
        QueryableShipData<?> all = VSGameUtilsKt.getAllShips(level);
        Iterable<? extends Ship> ships = AcVsSweep6.isEo(all) ? all.getIdToShipData().values() : all;
        for (Ship ship : ships) {
            if (!Intrinsics.areEqual(ship.getChunkClaimDimension(), VSGameUtilsKt.getDimensionId(level))) continue;
            AABBic lower = ship.getShipAABB();
            int minY = (lower != null ? lower.minY() : Mth.m_14107_(ship.getTransform().getPosition().y())) - 16;
            AABBic upper = ship.getShipAABB();
            int maxY = (upper != null ? upper.maxY() : Mth.m_14165_(ship.getTransform().getPosition().y())) + 16;
            WorldBox w = worldBox(ship.getActiveChunksSet(), minY, maxY, ship);
            // AABBd.intersectsAABB(AABBd): six strict comparisons, receiver = the transformed chunk box.
            if (!(w.maxX > x0 && w.maxY > y0 && w.maxZ > z0 && w.minX < x1 && w.minY < y1 && w.minZ < z1)) continue;
            if (entity instanceof PlayerKnownShipsDuck duck && !duck.vs_isKnownShip(ship.getId())) return true;
            Scratch s = SCRATCH.get();
            if (s.busy) s = new Scratch();
            s.busy = true;
            try {
                AABBd box = VectorConversionsMCKt.set(s.entityBox, bb);
                if (!allChunksLoaded(ship, s.shipBox.set(box).transform(ship.getWorldToShip()), level)) return true;
            } finally {
                s.busy = false;
            }
        }
        return false;
    }

    /**
     * The transformed chunk box for one ship. The set's union is recomputed only when its modification counter
     * moved; the chunk box's y fields follow AABBi.union exactly (min/max of minY and maxY once any chunk exists).
     * getShipToWorld() is read after getActiveChunksSet(), as in the original.
     */
    static WorldBox worldBox(IShipActiveChunksSet chunks, int minY, int maxY, Ship ship) {
        if (!(chunks instanceof ShipActiveChunksSet set)) return fresh(chunks, minY, maxY, ship.getShipToWorld());
        long mods = set.acVsMods;
        Extents e = set.acVsExtents instanceof Extents x && x.mods == mods ? x : null;
        if (e == null) {
            Union u = new Union();
            set.forEach(u);
            e = new Extents(mods, u.any, u.box.minX, u.box.minZ, u.box.maxX, u.box.maxZ);
            set.acVsExtents = e;
        }
        int bx0, by0, bz0, bx1, by1, bz1;
        if (e.any) {
            bx0 = e.minX;
            bz0 = e.minZ;
            bx1 = e.maxX;
            bz1 = e.maxZ;
            by0 = minY < maxY ? minY : maxY;
            by1 = minY > maxY ? minY : maxY;
        } else {
            bx0 = by0 = bz0 = Integer.MAX_VALUE;
            bx1 = by1 = bz1 = Integer.MIN_VALUE;
        }
        Matrix4dc m = ship.getShipToWorld();
        if (set.acVsWorld instanceof WorldBox w && w.matches(bx0, by0, bz0, bx1, by1, bz1, m)) return w;
        WorldBox w = new WorldBox(bx0, by0, bz0, bx1, by1, bz1, m);
        set.acVsWorld = w;
        return w;
    }

    /** Any other IShipActiveChunksSet: the original computation, uncached. */
    private static WorldBox fresh(IShipActiveChunksSet chunks, int minY, int maxY, Matrix4dc m) {
        Union u = new Union();
        u.minY = minY;
        u.maxY = maxY;
        chunks.forEach(u);
        AABBi b = u.box;
        return new WorldBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, m);
    }

    /** EntityShipCollisionUtils.areAllChunksLoaded (private there), including Kotlin's inclusive-range loop shape. */
    private static boolean allChunksLoaded(Ship ship, AABBdc box, Level level) {
        int minX = Mth.m_14107_(box.minX() - 1.0E-7) - 1 >> 4;
        int maxX = Mth.m_14107_(box.maxX() + 1.0E-7) + 1 >> 4;
        int minZ = Mth.m_14107_(box.minZ() - 1.0E-7) - 1 >> 4;
        int maxZ = Mth.m_14107_(box.maxZ() + 1.0E-7) + 1 >> 4;
        if (minX <= maxX) {
            for (int x = minX; ; x++) {
                if (minZ <= maxZ) {
                    for (int z = minZ; ; z++) {
                        if (ship.getActiveChunksSet().contains(x, z) && level.m_7925_(x, z) == null) return false;
                        if (z == maxZ) break;
                    }
                }
                if (x == maxX) break;
            }
        }
        return true;
    }

    /** Test hook: the transformed chunk box the collision check uses for this ship (null when not in level's dimension). */
    public static double[] worldBoxOf(Ship ship) {
        AABBic lower = ship.getShipAABB();
        int minY = (lower != null ? lower.minY() : Mth.m_14107_(ship.getTransform().getPosition().y())) - 16;
        AABBic upper = ship.getShipAABB();
        int maxY = (upper != null ? upper.maxY() : Mth.m_14165_(ship.getTransform().getPosition().y())) + 16;
        WorldBox w = worldBox(ship.getActiveChunksSet(), minY, maxY, ship);
        return new double[]{w.minX, w.minY, w.minZ, w.maxX, w.maxY, w.maxZ};
    }

    // ---- toDenseVoxelUpdate: palette ids in bulk ------------------------------------------------------------------

    private static final MethodHandle DATA, STORAGE, PALETTE, DENSE_STORE;

    static {
        MethodHandle data = null, storage = null, palette = null, store = null;
        try {
            Field dataField = only(PalettedContainer.class, "net.minecraft.world.level.chunk.PalettedContainer$Data");
            Class<?> dataClass = dataField.getType();
            Field storageField = only(dataClass, BitStorage.class.getName());
            Field paletteField = only(dataClass, Palette.class.getName());
            var lookup = MethodHandles.lookup();
            dataField.setAccessible(true);
            storageField.setAccessible(true);
            paletteField.setAccessible(true);
            data = lookup.unreflectGetter(dataField).asType(MethodType.methodType(Object.class, PalettedContainer.class));
            storage = lookup.unreflectGetter(storageField).asType(MethodType.methodType(BitStorage.class, Object.class));
            palette = lookup.unreflectGetter(paletteField).asType(MethodType.methodType(Palette.class, Object.class));
        } catch (Throwable t) {
            data = storage = palette = null; // no fast path; every section takes the sweep-5 loop
        }
        try {
            // VS core's dense builder (ET) keeps its voxels in a private In whose public int[] e is the whole update.
            Field in = only(ET.class, In.class.getName());
            in.setAccessible(true);
            store = MethodHandles.lookup().unreflectGetter(in).asType(MethodType.methodType(In.class, ET.class));
        } catch (Throwable t) {
            store = null; // addBlock for every block
        }
        DATA = data;
        STORAGE = storage;
        PALETTE = palette;
        DENSE_STORE = store;
    }

    private static Field only(Class<?> owner, String typeName) {
        Field found = null;
        for (Field f : owner.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !f.getType().getName().equals(typeName)) continue;
            if (found != null) throw new IllegalStateException("two " + typeName + " fields in " + owner);
            found = f;
        }
        if (found == null) throw new IllegalStateException("no " + typeName + " field in " + owner);
        return found;
    }

    /** True when the bulk path is available in this runtime (test hook). */
    public static boolean denseFastPathAvailable() {
        return DATA != null;
    }

    private static final class Dense {
        final int[] ids = new int[4096];
        final VsiBlockType[] types = new VsiBlockType[256];
        boolean busy;
    }

    private static final ThreadLocal<Dense> DENSE = ThreadLocal.withInitial(Dense::new);

    /** AcVsSweep5.denseVoxelUpdate with the bulk fill tried first. */
    public static VsiTerrainUpdate denseVoxelUpdate(LevelChunkSection section, Vector3ic chunkPos) {
        VsiTerrainUpdate.Builder update =
            ValkyrienSkiesMod.getVsCore().newDenseTerrainUpdateBuilder(chunkPos.x(), chunkPos.y(), chunkPos.z());
        if (!fillDenseFast(section, update)) AcVsSweep5.fillDense(section, update);
        return update.build();
    }

    /**
     * The sweep-5 fillDense result (the original per-block addBlock sequence) for a vanilla section whose block
     * storage is a SimpleBitStorage or ZeroBitStorage of 4096 entries and at most 8 bits: getBlockState(x, y, z) is
     * palette.valueFor(storage.get((y << 8) | (z << 4) | x)) of the container's current data, and
     * BitStorage.unpack writes exactly those storage values in index order. Each palette id is resolved at its first
     * occurrence in the original x/y/z visiting order (palette entries are distinct states; resolution is fixed for
     * the call). Returns false before any addBlock call when the section is not of that shape.
     *
     * When the builder is VS core's dense builder (ET), addBlock(x, y, z, type) is exactly
     * In.e[x | z << 4 | y << 8] = ((DM) type).a after a non-null check; the same index is MC's section index, so the
     * value is stored there directly. A resolved type that is not a DM goes through addBlock, which then throws the
     * original ClassCastException.
     */
    public static boolean fillDenseFast(LevelChunkSection section, VsiTerrainUpdate.Builder update) {
        if (DATA == null || section.getClass() != LevelChunkSection.class) return false;
        PalettedContainer<BlockState> states = section.m_63019_();
        if (states == null || states.getClass() != PalettedContainer.class) return false;
        BitStorage storage;
        Palette<BlockState> palette;
        try {
            Object data = (Object) DATA.invokeExact(states);
            storage = (BitStorage) STORAGE.invokeExact(data);
            @SuppressWarnings("unchecked")
            Palette<BlockState> p = (Palette<BlockState>) (Palette<?>) PALETTE.invokeExact(data);
            palette = p;
        } catch (Throwable t) {
            return false;
        }
        Class<?> kind = storage.getClass();
        if (kind != SimpleBitStorage.class && kind != ZeroBitStorage.class) return false;
        int bits = storage.m_144604_();
        if (storage.m_13521_() != 4096 || bits < 0 || bits > 8) return false;
        BlockStateInfo.Cache info = BlockStateInfo.INSTANCE.getCache();
        Dense d = DENSE.get();
        if (d.busy) d = new Dense();
        d.busy = true;
        try {
            int[] ids = d.ids;
            VsiBlockType[] types = d.types;
            storage.m_197970_(ids);
            Arrays.fill(types, 0, 1 << bits, null);
            int[] voxels = null;
            if (DENSE_STORE != null && update.getClass() == ET.class) {
                try {
                    voxels = ((In) DENSE_STORE.invokeExact((ET) update)).e;
                } catch (Throwable t) {
                    voxels = null;
                }
            }
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        int index = (y << 8) | (z << 4) | x;
                        int id = ids[index];
                        VsiBlockType type = types[id];
                        if (type == null) {
                            type = resolve(info, palette.m_5795_(id));
                            types[id] = type;
                        }
                        if (voxels != null && type instanceof DM dm) voxels[index] = dm.a;
                        else update.addBlock(x, y, z, type);
                    }
                }
            }
        } finally {
            d.busy = false;
        }
        return true;
    }

    /** The original per-block expression: info.get(state)?.second ?: vsCore.blockTypes.air (AcVsSweep5.resolve). */
    private static VsiBlockType resolve(BlockStateInfo.Cache info, BlockState state) {
        Pair<Double, VsiBlockType> resolved = info.get(state);
        VsiBlockType type = resolved != null ? resolved.getSecond() : null;
        if (type == null) {
            VsiCore core = ValkyrienSkiesMod.getVsCore();
            type = core.getBlockTypes().getAir();
        }
        return type;
    }
}
