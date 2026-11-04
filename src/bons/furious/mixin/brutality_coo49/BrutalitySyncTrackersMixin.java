package bons.furious.mixin.brutality_coo49;

import bons.furious.patch.brutality_coo49.BrutalitySync;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.goo.brutality.entity.capabilities.EntityCapabilities;
import net.goo.brutality.network.ClientboundSyncCapabilitiesPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * brutality_effect_sync_tracking_scope (Brutality 0.8.5, MIT; server; since 1.0.49; exact variant): MixinExtras
 * @WrapOperation on the packet construction and on the PacketHandler.sendToAllClients call of ForgeEffectSyncHandler's three
 * capability lambdas (Added / Remove / Expired): the capability sync goes to the players whose clients hold the entity, and
 * is not built when there is none (BrutalitySync). The flag setters before them run as shipped.
 */
@Mixin(targets = "net.goo.brutality.event.forge.ForgeEffectSyncHandler", remap = false)
public abstract class BrutalitySyncTrackersMixin {
    @WrapOperation(method = {
            "lambda$onAddEffect$0(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onRemoveEffect$1(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onExpiredEffect$2(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V"},
            at = @At(value = "NEW", target = "(ILnet/minecraft/world/entity/Entity;)Lnet/goo/brutality/network/ClientboundSyncCapabilitiesPacket;"),
            require = 3, allow = 3)
    private static ClientboundSyncCapabilitiesPacket bons$brutalityCoo49ScopeBuild(int id, Entity entity, Operation<ClientboundSyncCapabilitiesPacket> original,
                                                                                    MobEffect effect, LivingEntity living, EntityCapabilities.EntityEffectCap cap) {
        return BrutalitySync.scopeBuild(id, entity, original);
    }

    @WrapOperation(method = {
            "lambda$onAddEffect$0(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onRemoveEffect$1(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onExpiredEffect$2(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V"},
            at = @At(value = "INVOKE", target = "Lnet/goo/brutality/network/PacketHandler;sendToAllClients(Ljava/lang/Object;)V"),
            require = 3, allow = 3)
    private static void bons$brutalityCoo49ScopeSend(Object msg, Operation<Void> original, MobEffect effect, LivingEntity living,
                                                     EntityCapabilities.EntityEffectCap cap) {
        BrutalitySync.scopeSend(msg, original, living);
    }
}
