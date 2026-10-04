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
 * vanilla_spawn_gate_visibility_memo (Minecraft 1.20.1 on Forge 47.4.16; server). The entity manager keeps one long epoch
 * per group of 8x8-chunk regions; updateChunkStatus(ChunkPos, Visibility) (m_157527_, the only writer of chunkVisibility,
 * also reached from the FullChunkStatus overload) bumps the group of its position before it writes, and saveAll (m_157561_)
 * bumps every group when it returns. Both are rare (chunk status changes, saves), so the CallbackInfo does not matter. No
 * Minecraft code is carried.
 */
@Mixin(value = PersistentEntitySectionManager.class, remap = false)
public abstract class EntityManagerEpochsMixin implements VisibilityEpochs {
    @Unique
    private final long[] bons$epochs = SpawnGate.newEpochs();

    @Override
    public long[] bons$visEpochs() {
        return this.bons$epochs;
    }

    @Inject(method = "m_157527_", at = @At("HEAD"))
    private void bons$bumpRegion(ChunkPos pos, Visibility visibility, CallbackInfo ci) {
        SpawnGate.bump(this.bons$epochs, pos.f_45578_, pos.f_45579_);
    }

    @Inject(method = "m_157561_", at = @At("RETURN"))
    private void bons$bumpAllAfterSave(CallbackInfo ci) {
        SpawnGate.bumpAll(this.bons$epochs);
    }
}
