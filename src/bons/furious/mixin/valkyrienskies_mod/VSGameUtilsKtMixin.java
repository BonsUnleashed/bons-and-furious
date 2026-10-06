package bons.furious.mixin.valkyrienskies_mod;

import java.util.ArrayList;
import kotlin.jvm.internal.Intrinsics;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3ic;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.AcVsSweep6;
import org.valkyrienskies.mod.common.util.AcVsSweep7;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * valkyrien_ship_lookups (Valkyrien Skies 2.4.11).
 *
 * The ship lookups in VSGameUtils run for almost every block, entity and particle query. A chunk can only belong to a
 * ship inside the shipyard's claim range, so the chunk lookups first make VS's own range test with plain arithmetic
 * (AcVsSweep6.inShipyardRange) and skip the ship-world queries outside it. getShipsIntersecting scans the ship list
 * once without building the query box (AcVsSweep6.shipsIntersecting, null when VS's own query must run),
 * transformToNearbyShipsAndWorld returns when no ship is near the point, and toDenseVoxelUpdate reads a section's block
 * ids in bulk (AcVsSweep7.denseVoxelUpdate). toWorldCoordinates returns what VS returns (1.0.34: for a point on a ship a
 * new vector again, the caller's dest untouched; 1.0.15-1.0.33 wrote that result into dest).
 */
@Mixin(value = VSGameUtilsKt.class, remap = false)
public abstract class VSGameUtilsKtMixin {
    /**
     * @author BonsUnleashed
     * @reason Skip the ship-world lookups for chunks outside the shipyard claim range.
     */
    @Overwrite
    private static final LoadedShip getShipObjectManagingPosImpl(Level world, int chunkX, int chunkZ) {
        if (!AcVsSweep6.inShipyardRange(chunkX, chunkZ)) {
            return null;
        }
        if (world != null && VSGameUtilsKt.getShipObjectWorld(world).isChunkInShipyard(chunkX, chunkZ, VSGameUtilsKt.getDimensionId(world))) {
            Ship ship = VSGameUtilsKt.getShipObjectWorld(world).getAllShips().getByChunkPos(chunkX, chunkZ, VSGameUtilsKt.getDimensionId(world));
            if (ship != null) {
                return (LoadedShip) VSGameUtilsKt.getShipObjectWorld(world).getLoadedShips().getById(ship.getId());
            }
        }
        return null;
    }

    /**
     * @author BonsUnleashed
     * @reason Answer false for chunks outside the shipyard claim range without asking the ship world.
     */
    @Overwrite
    public static final boolean isChunkInShipyard(Level level, int chunkX, int chunkZ) {
        Intrinsics.checkNotNullParameter(level, "<this>");
        if (!AcVsSweep6.inShipyardRange(chunkX, chunkZ)) {
            return false;
        }
        return VSGameUtilsKt.getShipObjectWorld(level).isChunkInShipyard(chunkX, chunkZ, VSGameUtilsKt.getDimensionId(level));
    }

    /**
     * @author BonsUnleashed
     * @reason Skip the ship-world lookups for chunks outside the shipyard claim range.
     */
    @Overwrite
    private static final Ship getShipManagingPosImpl(Level world, int x, int z) {
        if (!AcVsSweep6.inShipyardRange(x, z)) {
            return null;
        }
        return world != null && VSGameUtilsKt.isChunkInShipyard(world, x, z)
                ? VSGameUtilsKt.getShipObjectWorld(world).getAllShips().getByChunkPos(x, z, VSGameUtilsKt.getDimensionId(world))
                : null;
    }

    /**
     * When no ship manages the point and no ship's world box is within aabbRadius of it, the method would make no
     * callback. The level is then passed on as null, so the method returns at its own null check (VS's
     * "this?.transformToNearbyShipsAndWorld"). This runs right after the method's null check of cb, as in 1.0.19; Mixin
     * passes the method's first five arguments after the variable.
     */
    @ModifyVariable(method = "transformToNearbyShipsAndWorld(Lnet/minecraft/world/level/Level;DDDDLorg/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer;)V",
            at = @At(value = "INVOKE", target = "Lkotlin/jvm/internal/Intrinsics;checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V", ordinal = 0, shift = At.Shift.AFTER),
            argsOnly = true)
    private static Level bons$skipWithoutNearbyShip(Level level, Level levelArgument, double x, double y, double z, double aabbRadius) {
        if (level != null && AcVsSweep6.noShipNearPoint(level, x, y, z, aabbRadius)) {
            return null;
        }
        return level;
    }

    /**
     * @author BonsUnleashed
     * @reason VS's body. 1.0.34: on a ship the result is a new vector and dest is left untouched, exactly as VS's
     * default-argument bridge (toWorldCoordinates$default(ship, x, y, z, null, 8, null)) does; 1.0.15-1.0.33 wrote it into
     * dest, which a caller reusing dest could observe.
     */
    @Overwrite
    public static final Vector3d toWorldCoordinates(Level level, double x, double y, double z, Vector3d dest) {
        Intrinsics.checkNotNullParameter(dest, "dest");
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, x, y, z);
        if (ship != null) {
            Vector3d inWorld = VSGameUtilsKt.toWorldCoordinates(ship, x, y, z, new Vector3d());   // 1.0.34: not dest
            if (inWorld != null) {
                return inWorld;
            }
        }
        Vector3d unchanged = dest.set(x, y, z);
        Intrinsics.checkNotNullExpressionValue(unchanged, "set(...)");
        return unchanged;
    }

    /**
     * @author BonsUnleashed
     * @reason Build the dense terrain update from the section's block ids in bulk.
     */
    @Overwrite
    public static final VsiTerrainUpdate toDenseVoxelUpdate(LevelChunkSection section, Vector3ic chunkPos) {
        Intrinsics.checkNotNullParameter(section, "<this>");
        Intrinsics.checkNotNullParameter(chunkPos, "chunkPos");
        return AcVsSweep7.denseVoxelUpdate(section, chunkPos);
    }

    /**
     * @author BonsUnleashed
     * @reason One pass over the ship list without converting the box first; VS's query runs when the helper declines.
     */
    @Overwrite
    public static final Iterable<Ship> getShipsIntersecting(Level level, AABB aabb) {
        Intrinsics.checkNotNullParameter(level, "<this>");
        Intrinsics.checkNotNullParameter(aabb, "aabb");
        ArrayList<Ship> ships = AcVsSweep6.shipsIntersecting(level, aabb);
        if (ships != null) {
            return ships;
        }
        return VSGameUtilsKt.getShipsIntersecting(level, (AABBdc) VectorConversionsMCKt.toJOML(aabb));
    }

    /**
     * @author BonsUnleashed
     * @reason One pass over the ship list; VS's query runs when the helper declines.
     */
    @Overwrite
    public static final Iterable<Ship> getShipsIntersecting(Level level, AABBdc aabb) {
        Intrinsics.checkNotNullParameter(level, "<this>");
        Intrinsics.checkNotNullParameter(aabb, "aabb");
        ArrayList<Ship> ships = AcVsSweep6.shipsIntersecting(level, aabb);
        if (ships != null) {
            return ships;
        }
        return VSGameUtilsKt.getAllShips(level).getIntersecting(aabb, VSGameUtilsKt.getDimensionId(level));
    }
}
