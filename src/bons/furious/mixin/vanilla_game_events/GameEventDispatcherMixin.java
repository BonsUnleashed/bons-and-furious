package bons.furious.mixin.vanilla_game_events;

import bons.furious.patch.vanilla_game_events.GameEventRegistries;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_game_event_registry_lookup (Minecraft 1.20.1 on Forge 47.4.16, server side incl. the integrated server):
 * GameEventDispatcher.post (m_245905_) asks every section in the event's radius for its listener registry through
 * ChunkAccess.getListenerRegistry (m_246686_), which on a LevelChunk creates and keeps an empty registry for every
 * section that has none. The redirected call returns the section's existing registry, or GameEventListenerRegistry.NOOP
 * where there is none, and calls getListenerRegistry itself for anything that is not a plain LevelChunk
 * (GameEventRegistries; why the result is identical is documented there). A @Redirect rather than a @WrapOperation: the
 * call runs 27 times per event and a WrapOperation's per-call Operation object cost about what the lookup saves (measured,
 * see notes/vanilla_game_events.md); no installed mod targets this call. It sits inside post's body, below Valkyrien
 * Skies' @WrapMethod on post. require = 0: a mod that overwrites post leaves the switch out.
 */
@Mixin(value = GameEventDispatcher.class, remap = false)
public abstract class GameEventDispatcherMixin {
    @Redirect(method = "m_245905_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;m_246686_(I)Lnet/minecraft/world/level/gameevent/GameEventListenerRegistry;"))
    private GameEventListenerRegistry bons$existingRegistry(ChunkAccess chunk, int sectionY) {
        return GameEventRegistries.lookup(chunk, sectionY);
    }
}
