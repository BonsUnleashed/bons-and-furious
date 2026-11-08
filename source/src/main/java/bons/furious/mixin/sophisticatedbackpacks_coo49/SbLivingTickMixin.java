package bons.furious.mixin.sophisticatedbackpacks_coo49;

import bons.furious.patch.sophisticatedbackpacks_coo49.SbEntityData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * sophisticatedbackpacks_living_tick_fast_exit (Sophisticated Backpacks 1.21.1-3.26.20.2244, server; kind opt, on by
 * default; no Sophisticated Backpacks code carried): the hasPendingBackpackAddition / hasSpawnedJukeboxUpgrade calls of
 * onLivingUpdate (an EntityTickEvent.Post listener on NeoForge 1.21.1) and the hasSpawnedBackpack call of
 * removeBackpackUuid read the same flag of the entity's persistent data with one key read and no new empty compound for an
 * entity without the "sophisticatedbackpacks" compound. See SbEntityData.
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.common.EntityBackpackAdditionHandler", remap = false)
public abstract class SbLivingTickMixin {
    @WrapOperation(method = "onLivingUpdate(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Post;)V", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedbackpacks/common/EntityBackpackAdditionHandler;hasPendingBackpackAddition(Lnet/minecraft/world/entity/LivingEntity;)Z"),
            require = 1, allow = 1)
    private static boolean bons$sophisticatedbackpacksPendingCheck(LivingEntity entity, Operation<Boolean> original) {
        return SbEntityData.enabled ? SbEntityData.flag(entity, SbEntityData.PENDING) : original.call(entity);
    }

    @WrapOperation(method = "onLivingUpdate(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Post;)V", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedbackpacks/common/EntityBackpackAdditionHandler;hasSpawnedJukeboxUpgrade(Lnet/minecraft/world/entity/LivingEntity;)Z"),
            require = 1, allow = 1)
    private static boolean bons$sophisticatedbackpacksJukeboxCheck(LivingEntity entity, Operation<Boolean> original) {
        return SbEntityData.enabled ? SbEntityData.flag(entity, SbEntityData.JUKEBOX) : original.call(entity);
    }

    @WrapOperation(method = "removeBackpackUuid(Lnet/minecraft/world/entity/monster/Monster;Lnet/minecraft/world/level/Level;)V", at = @At(value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedbackpacks/common/EntityBackpackAdditionHandler;hasSpawnedBackpack(Lnet/minecraft/world/entity/LivingEntity;)Z"),
            require = 1, allow = 1)
    private static boolean bons$sophisticatedbackpacksSpawnedCheck(LivingEntity entity, Operation<Boolean> original) {
        return SbEntityData.enabled ? SbEntityData.flag(entity, SbEntityData.SPAWNED) : original.call(entity);
    }
}
