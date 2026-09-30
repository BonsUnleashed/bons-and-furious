package bons.furious.mixin.valkyrienskies_mod;

import bons.pure.valkyrien.ChunkSetGate;
import java.util.ArrayList;
import java.util.List;
import kotlin.Pair;
import kotlin.jvm.JvmStatic;
import kotlin.jvm.internal.Intrinsics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.internal.collision.VsiConvexPolygonc;
import org.valkyrienskies.core.internal.collision.VsiEntityPolygonCollider;
import org.valkyrienskies.core.util.AABBdUtilKt;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.AcVsHotPaths;
import org.valkyrienskies.mod.common.util.AcVsSweep2;
import org.valkyrienskies.mod.common.util.AcVsSweep6;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.util.BugFixUtil;

/**
 * valkyrien_entity_ship_collision (Valkyrien Skies 2.4.11).
 *
 * VS checks every entity move against ships. isCollidingWithUnloadedShips streamed all ships through two lambdas and
 * two new boxes per ship; it now returns false at once when the level has no ships and otherwise runs the same checks
 * in the same order without the stream (ChunkSetGate picks the loop that reuses each ship's chunk box while the ship's
 * active-chunk set is unchanged). adjustEntityMovementForShipCollisions returns the movement unchanged when no loaded
 * ship is inside the query box VS would build (AcVsSweep6.noShipForMovement). getShipPolygonsCollidingWithEntity
 * computes the entity box in ship space directly (AcVsSweep2.transformedBounds: same corners, same transform, same
 * min/max as VS's polygon plus getEnclosingAABB), converts it once, and creates the per-box callback once per ship.
 */
@Mixin(value = EntityShipCollisionUtils.class, remap = false)
public abstract class EntityShipCollisionUtilsMixin {
    @Shadow
    @Final
    private static VsiEntityPolygonCollider collider;

    /** VS's per-box callback (Kotlin lambda body of getShipPolygonsCollidingWithEntity), unchanged. */
    @Shadow
    private static void getShipPolygonsCollidingWithEntity$lambda$6$lambda$5(ShipTransform shipTransform, LoadedShip shipObject,
            List<VsiConvexPolygonc> collidingPolygons, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason No ships in the level: nothing can be unloaded under the entity. Otherwise the stream-free loop.
     */
    @Overwrite
    @JvmStatic // VS's method carries it (runtime-visible); an @Overwrite keeps only the annotations declared here
    public static final boolean isCollidingWithUnloadedShips(Entity entity) {
        Intrinsics.checkNotNullParameter(entity, "entity");
        Level level = entity.m_9236_();
        if (level instanceof ServerLevel || level.f_46443_ && level instanceof ClientLevel) {
            if (level.f_46443_ && level instanceof ClientLevel && !VSGameUtilsKt.getShipObjectWorld((ClientLevel) level).isSyncedWithServer()) {
                return true;
            }
            if (AcVsHotPaths.noShips(level)) {
                return false;
            }
            return ChunkSetGate.collidingWithUnloadedShips(entity, level);
        }
        return false;
    }

    /**
     * @author BonsUnleashed
     * @reason Return the movement at once when no loaded ship is inside VS's own query box; otherwise unchanged.
     */
    @Overwrite
    public final Vec3 adjustEntityMovementForShipCollisions(Entity entity, Vec3 movement, AABB entityBoundingBox,
            Level world) {
        Intrinsics.checkNotNullParameter(movement, "movement");
        Intrinsics.checkNotNullParameter(entityBoundingBox, "entityBoundingBox");
        Intrinsics.checkNotNullParameter(world, "world");
        if (AcVsSweep6.noShipForMovement(entity, movement, entityBoundingBox, world)) {
            return movement;
        }
        double inflation = entity instanceof Player ? 0.5 : 0.1;
        double stepHeight = entity != null ? (double) entity.m_274421_() : 0.0;
        Vec3 movementWithStep = new Vec3(movement.m_7096_(), movement.m_7098_() + stepHeight / 2, movement.m_7094_());
        AABB inflatedBox = entityBoundingBox.m_82377_(inflation, inflation + stepHeight / 2, inflation);
        Intrinsics.checkNotNullExpressionValue(inflatedBox, "inflate(...)");
        List<VsiConvexPolygonc> collidingShipPolygons = this.getShipPolygonsCollidingWithEntity(entity, movementWithStep, inflatedBox, world);
        if (collidingShipPolygons.isEmpty()) {
            return movement;
        }
        // 0.00390625 is VS's PARTICLE_COLLISION_BOX_EXPANSION
        AABB collisionBoundingBox = entity == null ? entityBoundingBox.m_82400_(0.00390625) : entityBoundingBox;
        Vector3dc movementInWorld = VectorConversionsMCKt.toJOML(movement);
        Intrinsics.checkNotNull(collisionBoundingBox);
        Pair<Vector3dc, Long> collision = collider.adjustEntityMovementForPolygonCollisions(movementInWorld,
                VectorConversionsMCKt.toJOML(collisionBoundingBox), stepHeight, collidingShipPolygons);
        Vector3dc newMovement = collision.component1();
        Long shipCollidingWith = collision.component2();
        if (entity != null) {
            Level level = entity.m_9236_();
            BlockPos onPos = entity.m_20097_();
            Intrinsics.checkNotNullExpressionValue(onPos, "getOnPos(...)");
            LoadedShip standingOnShip = VSGameUtilsKt.getLoadedShipManagingPos(level, (Vec3i) onPos);
            if (shipCollidingWith != null && standingOnShip != null && standingOnShip.getId() == shipCollidingWith) {
                ((IEntityDraggingInformationProvider) entity).getDraggingInformation().setLastShipStoodOn(shipCollidingWith);
                for (Entity entityRiding : entity.m_146897_()) {
                    Intrinsics.checkNotNull(entityRiding, "null cannot be cast to non-null type org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider");
                    ((IEntityDraggingInformationProvider) entityRiding).getDraggingInformation().setLastShipStoodOn(shipCollidingWith);
                }
            }
        }
        return VectorConversionsMCKt.toMinecraft(newMovement);
    }

    /**
     * @author BonsUnleashed
     * @reason Entity box in ship space without the intermediate polygon; box converted and callback created once per ship.
     */
    @Overwrite
    public final List<VsiConvexPolygonc> getShipPolygonsCollidingWithEntity(Entity entity, Vec3 movement,
            AABB entityBoundingBox, Level world) {
        Intrinsics.checkNotNullParameter(movement, "movement");
        Intrinsics.checkNotNullParameter(entityBoundingBox, "entityBoundingBox");
        Intrinsics.checkNotNullParameter(world, "world");
        AABB entityBoxWithMovement = entityBoundingBox.m_82369_(movement);
        List<VsiConvexPolygonc> collidingPolygons = new ArrayList<>();
        AABBd entityBoundingBoxExtended = AABBdUtilKt.extend(VectorConversionsMCKt.toJOML(entityBoundingBox), VectorConversionsMCKt.toJOML(movement));
        for (LoadedShip shipObject : VSGameUtilsKt.getShipObjectWorld(world).getLoadedShips()
                .getIntersecting(entityBoundingBoxExtended, VSGameUtilsKt.getDimensionId(world))) {
            ShipTransform shipTransform = shipObject.getTransform();
            AABBd entityBoxInShip = AcVsSweep2.transformedBounds(entityBoxWithMovement, shipTransform.getWorldToShip());
            AABB shipCollisionBox = VectorConversionsMCKt.toMinecraft(entityBoxInShip);
            if (BugFixUtil.INSTANCE.isCollisionBoxTooBig(shipCollisionBox)) {
                continue;
            }
            Iterable<VoxelShape> shipBlockCollisions = world.m_186434_(entity, shipCollisionBox);
            Shapes.DoubleLineConsumer addShipPolygon = (minX, minY, minZ, maxX, maxY, maxZ) -> getShipPolygonsCollidingWithEntity$lambda$6$lambda$5(
                    shipTransform, shipObject, collidingPolygons, minX, minY, minZ, maxX, maxY, maxZ);
            Intrinsics.checkNotNull(shipBlockCollisions);
            for (VoxelShape voxelShape : shipBlockCollisions) {
                voxelShape.m_83286_(addShipPolygon);
            }
        }
        return collidingPolygons;
    }
}
