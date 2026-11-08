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
 * crittersandcompanions271_koi_luck_presence_gate (Critters and Companions 2.7.0 for Minecraft 1.21.1, package
 * io.github.bonsaistudi0s, all rights reserved: no code carried; server). PlayerHandler.onPlayerTick (called for every player
 * tick by CrittersAndCompanions) draws bubble particles on the client; on the server it searches KoiFishEntity in the
 * player's box +-10 (EntitySelector.ENTITY_STILL_ALIVE) every tick and gives Luck when it finds three. While the player's
 * level holds no koi (vanilla_entity_class_count_layer), the search is marked EMPTY so that vanilla_entity_walk_skip leaves
 * out its empty section walk; the handler runs unchanged on the same empty list (no Luck, as before). See
 * GoalPresenceGates.siteScan.
 */
@Mixin(targets = "io.github.bonsaistudi0s.crittersandcompanions.common.handler.PlayerHandler", remap = false)
public abstract class CacKoiLuck271GateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onPlayerTick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$koiScan(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return GoalPresenceGates.siteScan(GoalPresenceGates.crittersandcompanions271KoiLuck, "crittersandcompanions271_koi_luck_presence_gate", level, searched, box, predicate,
                original);
    }
}
