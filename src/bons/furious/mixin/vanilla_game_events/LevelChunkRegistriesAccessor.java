package bons.furious.mixin.vanilla_game_events;

import bons.furious.patch.vanilla_game_events.GameEventRegistries;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_game_event_registry_lookup (Minecraft 1.20.1 on Forge 47.4.16, server side): read access to LevelChunk's
 * per-section game-event listener registries (gameEventListenerRegistrySections, f_244451_) for
 * GameEventRegistries.lookup. Reads only; the map is still filled and emptied by LevelChunk's own code.
 */
@Mixin(value = LevelChunk.class, remap = false)
public abstract class LevelChunkRegistriesAccessor implements GameEventRegistries.ChunkRegistries {
    @Shadow
    @Final
    private Int2ObjectMap<GameEventListenerRegistry> f_244451_;

    @Override
    public Int2ObjectMap<GameEventListenerRegistry> bons$listenerRegistries() {
        return this.f_244451_;
    }
}
