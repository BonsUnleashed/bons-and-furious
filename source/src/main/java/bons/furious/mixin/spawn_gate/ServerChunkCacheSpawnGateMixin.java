package bons.furious.mixin.spawn_gate;

import bons.furious.patch.spawn_gate.SpawnGate;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_spawn_gate_visibility_memo (Minecraft 1.21.1 with NeoForge 21.1.252; server thread). In tickChunks the spawning
 * loop (inside the tickRateManager().runsNormally() block) stores each ticking chunk in local slot 14 (levelchunk1) and
 * then calls level.isNaturalSpawningAllowed(levelchunk1.getPos()). The @ModifyVariable keeps that chunk in a field as it
 * is stored (returning it unchanged), and the redirect passes it to SpawnGate.allowed, which answers from the chunk's
 * remembered answer while its region's epoch is unchanged and asks the entity manager otherwise; SpawnGate checks that
 * the position is that chunk's own ChunkPos object, so a different call shape falls back to the original call. Plain
 * injectors, no allocation (MixinExtras' @Local in a redirect would box the local in a LocalRef per call). Slot 14 holds
 * only this one LevelChunk store in the 1.21.1 method (one astore 14, bytecode offset 326; the filtering loop's chunk is
 * slot 9); the guard on tickChunks pins that layout (the fingerprint includes local indices). No Minecraft code.
 *
 * Ported to 1.21.1: the loop chunk is local slot 14 (was 16: tickChunks lost its isDebug flag and LevelData locals and
 * now declares the spawn locals inside the runsNormally() block); method selectors carry their descriptors.
 */
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ServerChunkCacheSpawnGateMixin {
    /** The chunk tickChunks' spawning loop is on (server thread only). */
    @Unique
    private LevelChunk bons$gateChunk;

    @ModifyVariable(method = "tickChunks()V", at = @At("STORE"), index = 14)
    private LevelChunk bons$loopChunk(LevelChunk chunk) {
        this.bons$gateChunk = chunk;
        return chunk;
    }

    @Redirect(method = "tickChunks()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private boolean bons$spawnGate(ServerLevel level, ChunkPos pos) {
        return SpawnGate.allowed(level, pos, this.bons$gateChunk);
    }
}
