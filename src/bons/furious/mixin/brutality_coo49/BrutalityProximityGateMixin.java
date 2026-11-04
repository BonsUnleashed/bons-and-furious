package bons.furious.mixin.brutality_coo49;

import bons.furious.patch.brutality_coo49.BrutalityProximity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * brutality_proximity_scan_presence_gate (Brutality 0.8.5, MIT; client; since 1.0.49): MixinExtras @WrapOperation on the
 * three ClientLevel.entitiesForRendering (m_104735_) calls of ForgeClientPlayerStateHandler.onClientTick (in bytecode order:
 * black hole walk, Blade of the Ruined King walk, explosion ray walk). The stream, its test and the colour-manager calls
 * run as shipped over what the call answers (BrutalityProximity: an empty list while the level holds none of the walk's
 * entity class, the level's players for the player walk while every Player of the level is among them).
 */
@Mixin(targets = "net.goo.brutality.event.forge.client.ForgeClientPlayerStateHandler", remap = false)
public abstract class BrutalityProximityGateMixin {
    @WrapOperation(method = "onClientTick(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104735_()Ljava/lang/Iterable;", ordinal = 0),
            require = 1, allow = 1)
    private static Iterable<Entity> bons$brutalityCoo49BlackHoleWalk(ClientLevel level, Operation<Iterable<Entity>> original) {
        return BrutalityProximity.typeWalk(level, false, original);
    }

    @WrapOperation(method = "onClientTick(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104735_()Ljava/lang/Iterable;", ordinal = 1),
            require = 1, allow = 1)
    private static Iterable<Entity> bons$brutalityCoo49BorkWalk(ClientLevel level, Operation<Iterable<Entity>> original) {
        return BrutalityProximity.playerWalk(level, original);
    }

    @WrapOperation(method = "onClientTick(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104735_()Ljava/lang/Iterable;", ordinal = 2),
            require = 1, allow = 1)
    private static Iterable<Entity> bons$brutalityCoo49RayWalk(ClientLevel level, Operation<Iterable<Entity>> original) {
        return BrutalityProximity.typeWalk(level, true, original);
    }
}
