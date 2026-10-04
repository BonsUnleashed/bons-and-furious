package bons.furious.mixin.spawn_gate;

import bons.furious.patch.spawn_gate.ChunkVisibilityMemo;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_spawn_gate_visibility_memo (Minecraft 1.21.1 with NeoForge 21.1.252; server): three fields on LevelChunk, the
 * chunk's last spawning-gate answer, the epoch group of its position and that group's epoch when the answer was taken
 * (SpawnGate.allowed). No Minecraft code.
 *
 * Ported to 1.21.1: unchanged (fields and interface methods only; no injector, no selector).
 */
@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkVisibilityMixin implements ChunkVisibilityMemo {
    @Unique
    private long bons$visStamp;
    @Unique
    private int bons$visGroup;
    @Unique
    private boolean bons$visTicking;

    @Override
    public long bons$visStamp() {
        return this.bons$visStamp;
    }

    @Override
    public int bons$visGroup() {
        return this.bons$visGroup;
    }

    @Override
    public boolean bons$visTicking() {
        return this.bons$visTicking;
    }

    @Override
    public void bons$visRemember(long stamp, int group, boolean ticking) {
        this.bons$visTicking = ticking;
        this.bons$visGroup = group;
        this.bons$visStamp = stamp;
    }
}
