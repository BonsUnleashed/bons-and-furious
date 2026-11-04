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
 * crittersandcompanions271_koi_luck_presence_gate (Critters and Companions 2.7.1, new package io.github.bonsaistudi0s, all
 * rights reserved: no code carried; server). The same koi luck scan as 2.3.5 (PlayerHandler.onPlayerTick, KoiFishEntity in the
 * player's box +-10 every server tick, Luck at three; the 2.7.1 handler draws bubble particles on the client first) and the
 * same gate: while the level holds no koi the search is marked EMPTY. A key of its own, so the build that lacks this class is
 * ABSENT. See CacKoiLuckGateMixin.
 */
@Mixin(targets = "io.github.bonsaistudi0s.crittersandcompanions.common.handler.PlayerHandler", remap = false)
public abstract class CacKoiLuck271GateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "onPlayerTick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$koiScan(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return GoalPresenceGates.siteScan(GoalPresenceGates.crittersandcompanions271KoiLuck, "crittersandcompanions271_koi_luck_presence_gate", level, searched, box, predicate,
                original);
    }
}
