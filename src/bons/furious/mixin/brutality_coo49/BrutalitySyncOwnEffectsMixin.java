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
 * brutality_effect_sync_own_effects_only (Brutality 0.8.5, MIT; server; since 1.0.49; change, off by default): MixinExtras
 * @WrapOperation on the packet construction and on the PacketHandler.sendToAllClients call of ForgeEffectSyncHandler's three
 * capability lambdas: the capability sync is built and sent only for Brutality's four own effects; the flag setters before
 * them run as shipped (BrutalitySync).
 */
@Mixin(targets = "net.goo.brutality.event.forge.ForgeEffectSyncHandler", remap = false)
public abstract class BrutalitySyncOwnEffectsMixin {
    @WrapOperation(method = {
            "lambda$onAddEffect$0(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onRemoveEffect$1(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onExpiredEffect$2(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V"},
            at = @At(value = "NEW", target = "(ILnet/minecraft/world/entity/Entity;)Lnet/goo/brutality/network/ClientboundSyncCapabilitiesPacket;"),
            require = 3, allow = 3)
    private static ClientboundSyncCapabilitiesPacket bons$brutalityCoo49OwnBuild(int id, Entity entity, Operation<ClientboundSyncCapabilitiesPacket> original,
                                                                                  MobEffect effect, LivingEntity living, EntityCapabilities.EntityEffectCap cap) {
        return BrutalitySync.ownBuild(id, entity, original, effect);
    }

    @WrapOperation(method = {
            "lambda$onAddEffect$0(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onRemoveEffect$1(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V",
            "lambda$onExpiredEffect$2(Lnet/minecraft/world/effect/MobEffect;Lnet/minecraft/world/entity/LivingEntity;Lnet/goo/brutality/entity/capabilities/EntityCapabilities$EntityEffectCap;)V"},
            at = @At(value = "INVOKE", target = "Lnet/goo/brutality/network/PacketHandler;sendToAllClients(Ljava/lang/Object;)V"),
            require = 3, allow = 3)
    private static void bons$brutalityCoo49OwnSend(Object msg, Operation<Void> original, MobEffect effect, LivingEntity living,
                                                   EntityCapabilities.EntityEffectCap cap) {
        BrutalitySync.sendBuilt(msg, original);
    }
}
