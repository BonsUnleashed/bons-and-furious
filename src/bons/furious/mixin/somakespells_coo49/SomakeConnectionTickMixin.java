package bons.furious.mixin.somakespells_coo49;

import bons.furious.patch.somakespells_coo49.SomakeConnections;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * somakespells_connection_tick_precheck (Somake Spells 1.0.9, All Rights Reserved; server; exact): MixinExtras
 * @WrapOperation on the first entity.level() call of ConnectionEventHandler.onLivingTick, whose value only feeds the
 * handler's opening "instanceof ServerLevel" test. When the entity is on a server level and its persistent data holds none of
 * the mod's three connection compounds (SomakeConnectionTransferQueue, SomakeConnectionLink, and for a ServerPlayer
 * SomakeConnectionPendingCast), the value is null: the test fails and the handler returns at once, which is where the
 * original ends after building three empty data objects and writing nothing. Otherwise the original value: the handler runs
 * as shipped. See SomakeConnections. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.somakespells.event.ConnectionEventHandler", remap = false)
public abstract class SomakeConnectionTickMixin {
    @WrapOperation(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_9236_()Lnet/minecraft/world/level/Level;", ordinal = 0),
            require = 1, allow = 1, remap = false)
    private static Level bons$somakespellsCoo49TickLevel(LivingEntity entity, Operation<Level> original) {
        return SomakeConnections.level(entity, original);
    }
}
