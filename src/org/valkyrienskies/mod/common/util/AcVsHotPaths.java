package org.valkyrienskies.mod.common.util;
import net.minecraft.world.level.Level;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
/** No cross-tick cache: ship creation/loading is visible on the next call. */
public final class AcVsHotPaths {
    private AcVsHotPaths() {}
    public static boolean noShips(Level level) {
        return level != null && VSGameUtilsKt.getAllShips(level).isEmpty();
    }
    public static boolean noLoadedShips(Level level) {
        return level != null && VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().isEmpty();
    }
    public static boolean skipSealedCheck(Level level) {
        return level != null && (!ValkyrienSkies.isConnectivityEnabled(level.f_46443_) || noShips(level));
    }
    public static org.joml.Vector3d normalizeOrZero(org.joml.Vector3d vector) {
        return vector.x == 0.0 && vector.y == 0.0 && vector.z == 0.0 ? vector : vector.normalize();
    }
    public static net.minecraft.world.phys.Vec3 sculkEventPosition(
        net.minecraft.world.level.gameevent.vibrations.VibrationInfo info,
        net.minecraft.server.level.ServerLevel level) {
        return VSGameUtilsKt.toWorldCoordinates(level, info.f_243906_());
    }
    public static net.minecraft.world.level.gameevent.PositionSource sculkPositionSource(
        net.minecraft.world.level.gameevent.vibrations.VibrationSystem.User user,
        net.minecraft.server.level.ServerLevel level) {
        var source = user.m_280010_();
        var pos = source.m_142502_(level);
        if (pos.isEmpty() || VSGameUtilsKt.getShipManagingPos(level, pos.get()) == null) return source;
        return new net.minecraft.world.level.gameevent.BlockPositionSource(
            net.minecraft.core.BlockPos.m_274446_(VSGameUtilsKt.toWorldCoordinates(level, pos.get())));
    }
}
