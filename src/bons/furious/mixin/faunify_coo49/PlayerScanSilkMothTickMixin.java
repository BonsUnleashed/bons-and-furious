package bons.furious.mixin.faunify_coo49;

import bons.furious.patch.faunify_coo49.PlayerScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * faunify_player_proximity_scan_lean: the Level.getEntitiesOfClass(Player.class, box) search in the silk moth's tick (Faunify 1.3.3; the 1.1.3 silk moth has no such search, so the group's plugin offers this mixin only when the guard matched 1.3.3) is
 * answered from the level's player list when the switch is on. See PlayerScan.
 */
@Mixin(targets = "com.pepper.faunify.entity.SilkMothEntity", remap = false)
public abstract class PlayerScanSilkMothTickMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = {"m_8119_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$players(Level level, Class<?> type, AABB box, Operation<List> original) {
        return PlayerScan.players(level, type, box, original);
    }
}