package bons.furious.mixin.spawn_gate;

import bons.furious.patch.spawn_gate.SpawnGate;
import bons.furious.patch.spawn_gate.VisibilityEpochs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_spawn_gate_visibility_memo (Minecraft 1.21.1 with NeoForge 21.1.252; server). The entity manager keeps one long
 * epoch per group of 8x8-chunk regions; updateChunkStatus(ChunkPos, Visibility) (the only writer of chunkVisibility, also
 * reached from the FullChunkStatus overload, which ServerLevel hands to the chunk map as its status listener) bumps the
 * group of its position before it writes, and saveAll bumps every group when it returns. Both are rare (chunk status
 * changes, saves), so the CallbackInfo does not matter. No Minecraft code is carried.
 *
 * Ported to 1.21.1: updateChunkStatus is selected by its full descriptor (under Mojang names the two overloads share the
 * name; a bare name would select the FullChunkStatus overload first); the rest is unchanged.
 */
@Mixin(value = PersistentEntitySectionManager.class, remap = false)
public abstract class EntityManagerEpochsMixin implements VisibilityEpochs {
    @Unique
    private final long[] bons$epochs = SpawnGate.newEpochs();

    @Override
    public long[] bons$visEpochs() {
        return this.bons$epochs;
    }

    @Inject(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V", at = @At("HEAD"))
    private void bons$bumpRegion(ChunkPos pos, Visibility visibility, CallbackInfo ci) {
        SpawnGate.bump(this.bons$epochs, pos.x, pos.z);
    }

    @Inject(method = "saveAll()V", at = @At("RETURN"))
    private void bons$bumpAllAfterSave(CallbackInfo ci) {
        SpawnGate.bumpAll(this.bons$epochs);
    }
}
