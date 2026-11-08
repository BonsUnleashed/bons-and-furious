package bons.furious.mixin.mob_refresh_c49;

import bons.furious.patch.mob_refresh_c49.RefreshSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mob_tick_dimension_refresh_always_skip (Minecraft 1.21.1 on NeoForge 21.1.252, both sides; a trade-off, off by default):
 * inside Entity.refreshDimensions, for Born in Chaos and Critters n' Crawlers mobs whatever the Size listeners
 * (RefreshSkip.alwaysSkip), an unchanged refresh builds the EntityEvent.Size that EventHooks.getEntitySizeForge would post
 * without posting it, and leaves the position re-apply (reapplyPosition) out. The stored size and eye height, the
 * grow-only free-position search and every other refresh are unchanged. Injectors only.
 */
@Mixin(value = Entity.class, priority = 1050, remap = false)
public abstract class RefreshSkipAlwaysMixin {
    @WrapOperation(method = "refreshDimensions()V", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/EventHooks;getEntitySizeForge(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;Lnet/minecraft/world/entity/EntityDimensions;)Lnet/neoforged/neoforge/event/entity/EntityEvent$Size;"),
            require = 1, allow = 1)
    private EntityEvent.Size bons$alwaysSizeEvent(Entity entity, Pose pose, EntityDimensions old, EntityDimensions now,
                                                  Operation<EntityEvent.Size> original, @Share("bons$mob_refresh_c49Always") LocalBooleanRef kept) {
        if (RefreshSkip.alwaysSkip(entity, old, now)) {
            kept.set(true);
            return RefreshSkip.unposted(entity, pose, old, now);
        }
        return original.call(entity, pose, old, now);
    }

    @WrapOperation(method = "refreshDimensions()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;reapplyPosition()V"),
            require = 1, allow = 1)
    private void bons$alwaysReapply(Entity entity, Operation<Void> original, @Share("bons$mob_refresh_c49Always") LocalBooleanRef kept) {
        if (!kept.get()) original.call(entity);
    }
}
