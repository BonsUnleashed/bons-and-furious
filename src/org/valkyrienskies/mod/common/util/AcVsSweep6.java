package org.valkyrienskies.mod.common.util;

import java.util.ArrayList;
import java.util.Objects;
import kotlin.jvm.internal.Intrinsics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.QueryableShipData;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.util.functions.IntBinaryConsumer;
import org.valkyrienskies.core.util.AABBdUtilKt;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck;

/**
 * Sixth-sweep helpers (Bons Valkyrien Fixes 1.2.0). Each entry point either computes the value the VS 2.4.11
 * code computes, with the same library calls on reused objects, or answers "the original would find no ship"
 * so the caller can skip work whose result is then known. {@link #occludedProbe} is the one exception and
 * documents why its substitution cannot change any world effect.
 */
public final class AcVsSweep6 {
    private AcVsSweep6() {}

    // ---- shipyard range ---------------------------------------------------------------------------------

    /**
     * ChunkAllocator.isChunkInShipyard with its 2.4.11 constants: claim index = floorDiv(chunk, 256),
     * -7000 <= claimX < 7001 and 3000 <= claimZ < 7001. Every 2.4.11 ship world answers isChunkInShipyard
     * through that allocator (the Ev subclasses, via the single DQ provider) or with a constant false (the
     * dummy worlds Ei/Ej), so false here means false there.
     */
    public static boolean inShipyardRange(int chunkX, int chunkZ) {
        int claimX = Math.floorDiv(chunkX, 256), claimZ = Math.floorDiv(chunkZ, 256);
        return (-7000 <= claimX && claimX < 7001) & (3000 <= claimZ && claimZ < 7001);
    }

    // ---- ship queries -----------------------------------------------------------------------------------

    private static final String EO = "org.valkyrienskies.core.impl.shadow.Eo";
    private static Class<?> eoClass; // benign race: at worst resolved by name more than once

    /**
     * VS's own query-data class (the only VsiQueryableShipData implementation in 2.4.11): its getIntersecting
     * scans getIdToShipData().values() and returns a fresh ArrayList of the ships that pass.
     */
    static boolean isEo(Object ships) {
        Class<?> c = ships.getClass(), e = eoClass;
        if (e == null) {
            if (!c.getName().equals(EO)) return false;
            eoClass = e = c;
        }
        return c == e;
    }

    /**
     * Whether Eo.getIntersecting(box, dim) is non-empty for box = [x0, x1] x [y0, y1] x [z0, z1], dim = level's id.
     * Eo keeps a ship when AABBdUtilKt.intersectsAABB(ship.getWorldAABB(), box), i.e.
     * worldAABB.intersectsAABB((AABBd) box), holds and its claim dimension equals dim. For a JOML AABBd world box
     * that is exactly the six strict field comparisons below (NaN compares false, as in the original); any other
     * AABBdc implementation is asked through the same library call. Stops at the first hit; the dimension id is
     * only read for hits (it is a cached field of the level).
     */
    static boolean anyIntersecting(QueryableShipData<?> ships, double x0, double y0, double z0, double x1, double y1, double z1, Level level) {
        AABBd other = null;
        for (Ship ship : ships.getIdToShipData().values()) {
            AABBdc w = ship.getWorldAABB();
            boolean hit;
            if (w.getClass() == AABBd.class) {
                AABBd a = (AABBd) w;
                hit = a.maxX > x0 && a.maxY > y0 && a.maxZ > z0 && a.minX < x1 && a.minY < y1 && a.minZ < z1;
            } else {
                if (other == null) other = new AABBd(x0, y0, z0, x1, y1, z1);
                hit = AABBdUtilKt.intersectsAABB(w, other);
            }
            if (hit && Objects.equals(ship.getChunkClaimDimension(), VSGameUtilsKt.getDimensionId(level))) return true;
        }
        return false;
    }

    /**
     * Eo.getIntersecting(box, dim) reproduced in one pass: a fresh ArrayList of the ships of getIdToShipData().values(),
     * in that order, whose world box meets box (same predicate as anyIntersecting) and whose claim dimension equals
     * the level's id. Null for any other query-data implementation, so the caller runs the original code.
     */
    static ArrayList<Ship> intersecting(QueryableShipData<?> ships, double x0, double y0, double z0, double x1, double y1, double z1, Level level) {
        if (!isEo(ships)) return null;
        var out = new ArrayList<Ship>();
        AABBd other = null;
        String dim = null;
        for (Ship ship : ships.getIdToShipData().values()) {
            AABBdc w = ship.getWorldAABB();
            boolean hit;
            if (w.getClass() == AABBd.class) {
                AABBd a = (AABBd) w;
                hit = a.maxX > x0 && a.maxY > y0 && a.maxZ > z0 && a.minX < x1 && a.minY < y1 && a.minZ < z1;
            } else {
                if (other == null) other = new AABBd(x0, y0, z0, x1, y1, z1);
                hit = AABBdUtilKt.intersectsAABB(w, other);
            }
            if (!hit) continue;
            if (dim == null) dim = VSGameUtilsKt.getDimensionId(level);
            if (Objects.equals(ship.getChunkClaimDimension(), dim)) out.add(ship);
        }
        return out;
    }

    /** VSGameUtilsKt.getShipsIntersecting(level, aabb): toJOML(aabb) then allShips.getIntersecting, without the box. */
    public static ArrayList<Ship> shipsIntersecting(Level level, AABB aabb) {
        return intersecting(VSGameUtilsKt.getAllShips(level), aabb.f_82288_, aabb.f_82289_, aabb.f_82290_, aabb.f_82291_, aabb.f_82292_, aabb.f_82293_, level);
    }

    /** VSGameUtilsKt.getShipsIntersecting(level, box); null (original path) unless box is an AABBd, which Eo casts it to. */
    public static ArrayList<Ship> shipsIntersecting(Level level, AABBdc box) {
        if (!(box instanceof AABBd b)) return null;
        return intersecting(VSGameUtilsKt.getAllShips(level), b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, level);
    }

    /** MixinEntity(entity_collision).getPosStandingOnFromShips would find no ship in the unit box around p. */
    public static boolean noShipAroundStandingPos(Level level, Vector3dc p) {
        if (level == null) return false; // the original throws from getShipsIntersecting's parameter check
        QueryableShipData<?> ships = VSGameUtilsKt.getAllShips(level);
        return isEo(ships) && !anyIntersecting(ships, p.x() - 0.5, p.y() - 0.5, p.z() - 0.5, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, level);
    }

    /**
     * VSGameUtilsKt.transformToNearbyShipsAndWorld(level, x, y, z, r, cb) would make no callback: no ship
     * manages (x, y, z) and no ship's world box meets AABBd(x, y, z, x, y, z).expand(r).
     */
    public static boolean noShipNearPoint(Level level, double x, double y, double z, double r) {
        if (VSGameUtilsKt.getShipManagingPos(level, x, y, z) != null) return false;
        QueryableShipData<?> ships = VSGameUtilsKt.getShipObjectWorld(level).getAllShips();
        return isEo(ships) && !anyIntersecting(ships, x - r, y - r, z - r, x + r, y + r, z + r, level);
    }

    /**
     * EntityShipCollisionUtils.adjustEntityMovementForShipCollisions would get an empty polygon list and
     * return movement: rebuilds getShipPolygonsCollidingWithEntity's loaded-ship query box, i.e.
     * toJOML(bb.inflate(i, i + h/2, i)).extend(toJOML(Vec3(x, y + h/2, z))), with AABB's min/max
     * normalisation and extend's sign rule.
     */
    public static boolean noShipForMovement(Entity entity, Vec3 movement, AABB bb, Level world) {
        QueryableShipData<?> ships = VSGameUtilsKt.getShipObjectWorld(world).getLoadedShips();
        if (!isEo(ships)) return false;
        double inflation = entity instanceof Player ? 0.5 : 0.1;
        double stepHeight = entity != null ? (double) entity.m_274421_() : 0.0;
        double half = stepHeight / (double) 2;
        double mx = movement.m_7096_(), my = movement.m_7098_() + half, mz = movement.m_7094_();
        double iy = inflation + half;
        double ax = bb.f_82288_ - inflation, ay = bb.f_82289_ - iy, az = bb.f_82290_ - inflation;
        double bx = bb.f_82291_ + inflation, by = bb.f_82292_ + iy, bz = bb.f_82293_ + inflation;
        double x0 = Math.min(ax, bx), y0 = Math.min(ay, by), z0 = Math.min(az, bz);
        double x1 = Math.max(ax, bx), y1 = Math.max(ay, by), z1 = Math.max(az, bz);
        if (mx > 0.0) x1 = x1 + mx; else x0 = x0 + mx;
        if (my > 0.0) y1 = y1 + my; else y0 = y0 + my;
        if (mz > 0.0) z1 = z1 + mz; else z0 = z0 + mz;
        return !anyIntersecting(ships, x0, y0, z0, x1, y1, z1, world);
    }

    // ---- isCollidingWithUnloadedShips -----------------------------------------------------------------------

    private static final class Scratch implements IntBinaryConsumer {
        final AABBi chunkBox = new AABBi();
        final AABBd entityBox = new AABBd(), worldBox = new AABBd(), shipBox = new AABBd();
        int minY, maxY;
        boolean busy;

        /** EntityShipCollisionUtils.getShipyardChunkAABBAround$lambda$0 on the reused box. */
        @Override
        public void accept(int x, int z) {
            int minX = SectionPos.m_123223_(x);
            int minZ = SectionPos.m_123223_(z);
            int maxX = SectionPos.m_175554_(x, 15);
            int maxZ = SectionPos.m_175554_(z, 15);
            chunkBox.union(minX, minY, minZ).union(maxX, maxY, maxZ);
        }
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /**
     * EntityShipCollisionUtils.isCollidingWithUnloadedShips after its client-sync and no-ships guards.
     * The original streams getAllShips(level) through a filter (same dimension, then the ship's active-chunk
     * box transformed to world space meets the entity box) into allMatch (the player knows the ship, and
     * every active chunk under the entity box is loaded) and returns the negation. This visits the ships in
     * the same order, makes the same calls in the same order per ship and stops at the same first failure,
     * reusing four boxes instead of allocating two per ship plus the stream pipeline.
     */
    public static boolean collidingWithUnloadedShips(Entity entity, Level level) {
        AABB bb = entity.m_20191_();
        Intrinsics.checkNotNullExpressionValue(bb, "getBoundingBox(...)");
        Scratch s = SCRATCH.get();
        if (s.busy) s = new Scratch();
        s.busy = true;
        try {
            AABBd box = VectorConversionsMCKt.set(s.entityBox, bb);
            for (Ship ship : VSGameUtilsKt.getAllShips(level)) {
                if (!Intrinsics.areEqual(ship.getChunkClaimDimension(), VSGameUtilsKt.getDimensionId(level))) continue;
                if (!AABBdUtilKt.toAABBd(chunkBoxAround(s, ship), s.worldBox).transform(ship.getShipToWorld()).intersectsAABB(box)) continue;
                if (entity instanceof PlayerKnownShipsDuck duck && !duck.vs_isKnownShip(ship.getId())) return true;
                if (!allChunksLoaded(ship, s.shipBox.set(box).transform(ship.getWorldToShip()), level)) return true;
            }
            return false;
        } finally {
            s.busy = false;
        }
    }

    /** EntityShipCollisionUtils.getShipyardChunkAABBAround into the reused AABBi (reset to new AABBi()'s state). */
    private static AABBi chunkBoxAround(Scratch s, Ship ship) {
        AABBi box = s.chunkBox;
        box.minX = Integer.MAX_VALUE;
        box.minY = Integer.MAX_VALUE;
        box.minZ = Integer.MAX_VALUE;
        box.maxX = Integer.MIN_VALUE;
        box.maxY = Integer.MIN_VALUE;
        box.maxZ = Integer.MIN_VALUE;
        AABBic lower = ship.getShipAABB();
        s.minY = (lower != null ? lower.minY() : Mth.m_14107_(ship.getTransform().getPosition().y())) - 16;
        AABBic upper = ship.getShipAABB();
        s.maxY = (upper != null ? upper.maxY() : Mth.m_14165_(ship.getTransform().getPosition().y())) + 16;
        ship.getActiveChunksSet().forEach(s);
        return box;
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

    // ---- world_weather occlusion sentinel -------------------------------------------------------------------

    private static final class Occluded {
        final BlockPos failure, probe;

        Occluded(BlockPos failure, BlockPos probe) {
            this.failure = failure;
            this.probe = probe;
        }
    }

    private static volatile Occluded occluded;

    /**
     * Called by MixinServerLevel(world_weather).occlude immediately before it returns its failure sentinel,
     * BlockPos(0, minBuildHeight - 1, 0), for a ship chunk whose surface block is under world terrain.
     */
    public static BlockPos markOccluded(BlockPos failure, LevelChunk chunk) {
        ChunkPos pos = chunk.m_7697_();
        occluded = new Occluded(failure, new BlockPos(pos.m_45604_() + 8, failure.m_123342_(), pos.m_45605_() + 8));
        return failure;
    }

    /**
     * For exactly that sentinel object, the position whose biome useBiomeAtWorldPos reads instead: the
     * sentinel's height in the middle of the ticking chunk, a loaded chunk, so the lookup is a palette read.
     * Upstream transforms the sentinel with the ship's shipyard-to-world matrix, which lands ~28 million
     * blocks away in a chunk that is never loaded, so every occluded ship-column tick sampled the full
     * climate noise (3.8% of the server thread in the full-pack profile). The biome is not observable there:
     * Forge's isAreaLoaded(sentinel.below(), 1) is false below the world, so shouldFreeze is not called;
     * shouldSnow is false below the world; getPrecipitationAt only chooses the argument of
     * VOID_AIR.handlePrecipitation, Block's empty method. No installed mod hooks those calls or reads the
     * biome local in tickChunk (checked against the pack's mixin index for this release).
     */
    public static BlockPos occludedProbe(BlockPos pos) {
        Occluded o = occluded;
        if (o == null || o.failure != pos) return null;
        occluded = null;
        return o.probe;
    }
}
