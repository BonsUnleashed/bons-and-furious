package bons.furious.mixin.goal_presence_gates;

import bons.furious.patch.goal_presence_gates.GoalPresenceGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crittersandcompanions_koi_luck_presence_gate (Critters and Companions 2.3.5, MIT: no code carried; server).
 * PlayerHandler.onPlayerTick searches KoiFishEntity in the player's box +-10 every server tick and gives Luck when it finds
 * three. While the player's level holds no koi (vanilla_entity_class_count_layer), the search is marked EMPTY so that
 * vanilla_entity_walk_skip leaves out its empty section walk; the handler runs unchanged on the same empty list (no Luck, as
 * before). See GoalPresenceGates.siteScan.
 */
@Mixin(targets = "com.github.eterdelta.crittersandcompanions.handler.PlayerHandler", remap = false)
public abstract class CacKoiLuckGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onPlayerTick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$koiScan(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return GoalPresenceGates.siteScan(GoalPresenceGates.crittersandcompanionsKoiLuck, "crittersandcompanions_koi_luck_presence_gate", level, searched, box, predicate, original);
    }
}
