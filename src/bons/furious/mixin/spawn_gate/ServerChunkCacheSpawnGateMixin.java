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
 * vanilla_spawn_gate_visibility_memo (Minecraft 1.20.1 on Forge 47.4.16; server thread). In tickChunks (m_8490_) the
 * second loop stores each ticking chunk in local slot 16 (levelchunk1) and then calls
 * level.isNaturalSpawningAllowed(levelchunk1.getPos()) (m_201916_). The @ModifyVariable keeps that chunk in a field as it
 * is stored (returning it unchanged), and the redirect passes it to SpawnGate.allowed, which answers from the chunk's
 * remembered answer while its region's epoch is unchanged and asks the entity manager otherwise; SpawnGate checks that
 * the position is that chunk's own ChunkPos object, so a different call shape falls back to the original call. Plain
 * injectors, no allocation (MixinExtras' @Local in a redirect would box the local in a LocalRef per call). Radium's two
 * redirects in this method target other calls; no other mixin targets this call or local. No Minecraft code.
 */
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class ServerChunkCacheSpawnGateMixin {
    /** The chunk tickChunks' second loop is on (server thread only). */
    @Unique
    private LevelChunk bons$gateChunk;

    @ModifyVariable(method = "m_8490_", at = @At("STORE"), index = 16)
    private LevelChunk bons$loopChunk(LevelChunk chunk) {
        this.bons$gateChunk = chunk;
        return chunk;
    }

    @Redirect(method = "m_8490_", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_201916_(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private boolean bons$spawnGate(ServerLevel level, ChunkPos pos) {
        return SpawnGate.allowed(level, pos, this.bons$gateChunk);
    }
}
