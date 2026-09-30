package bons.furious.mixin.valkyrienskies_mod;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.AcVsSweep5;

/**
 * valkyrien_spawn_distance (Valkyrien Skies 2.4.11).
 *
 * VS's server.world.MixinChunkMap wraps the two mob-spawn distance checks (anyPlayerCloseEnoughForSpawning and
 * playerIsCloseEnoughForSpawning, run for every ticking chunk) and converts the chunk's middle block to world space and
 * back, in case a ship manages the chunk. For a chunk no ship manages that round trip returns the chunk itself, so the
 * wrapped check is now called with the chunk directly (AcVsSweep5.spawnChunkOnShip is VS's own ship lookup).
 *
 * These are HEAD injections into VS's two handlers (MixinSquared): a CallbackInfoReturnable per call remains, because
 * no redirect-style injector can skip the rest of the handler; the fast path still skips the ship-space round trip and
 * its temporary positions.
 */
@Mixin(value = ChunkMap.class, priority = 1500, remap = false)
public abstract class ChunkMapSpawnDistanceMixin {
    @Shadow
    @Final
    ServerLevel f_140133_;

    /** The receiver is ChunkMap's package-private DistanceManager subclass, taken here as its public superclass. */
    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.server.world.MixinChunkMap", name = "onHasPlayersNearby")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void bons$hasPlayersNearbyOffShip(@Coerce DistanceManager distanceManager, long chunkKey, Operation<Boolean> original,
            ChunkPos chunkPos, CallbackInfoReturnable<Boolean> cir) {
        if (!AcVsSweep5.spawnChunkOnShip(this.f_140133_, chunkPos)) {
            cir.setReturnValue(original.call(distanceManager, chunkPos.m_45588_()));
        }
    }

    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.server.world.MixinChunkMap", name = "onEuclideanDistanceSquared")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true)
    private void bons$distanceOffShip(ChunkPos chunkPos, Entity entity, Operation<Double> original, CallbackInfoReturnable<Double> cir) {
        if (!AcVsSweep5.spawnChunkOnShip(this.f_140133_, chunkPos)) {
            cir.setReturnValue(original.call(chunkPos, entity));
        }
    }
}
