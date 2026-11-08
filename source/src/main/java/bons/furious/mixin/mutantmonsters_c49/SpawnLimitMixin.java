package bons.furious.mixin.mutantmonsters_c49;

import bons.furious.patch.mutantmonsters_c49.SpawnLimitIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mutantmonsters_spawn_limit_index (Mutant Monsters, AGPL-3.0: no code carried; 1.21.1 tested build: Mutant Monsters
 * v21.1.1 for NeoForge 1.21.1; server; since 1.0.49): the ServerLevel.getAllEntities() call of
 * SpawningPreventionHandler.onEntitySpawn answers the spawning type's entities from the level's TypeCount (SpawnLimitIndex);
 * Mutant Monsters' own stream, filter and count run over them unchanged.
 *
 * Ported to 1.21.1: onEntitySpawn has the same descriptor and body (the limit table now holds the Holder.Reference values of
 * ModEntityTypes: body parts and endersoul fragments 128, creeper minion eggs 32).
 */
@Mixin(targets = "fuzs.mutantmonsters.handler.SpawningPreventionHandler", remap = false)
public abstract class SpawnLimitMixin {
    @WrapOperation(method = "onEntitySpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/MobSpawnType;)Lfuzs/puzzleslib/api/event/v1/core/EventResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getAllEntities()Ljava/lang/Iterable;", remap = false), require = 1, allow = 1, remap = false)
    private static Iterable<?> bons$typedEntities(ServerLevel level, Operation<Iterable<?>> original, @Local(argsOnly = true) Entity spawning) {
        return SpawnLimitIndex.entities(level, spawning, original);
    }
}
