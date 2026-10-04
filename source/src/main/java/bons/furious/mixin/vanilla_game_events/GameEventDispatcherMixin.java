package bons.furious.mixin.vanilla_game_events;

import bons.furious.patch.vanilla_game_events.GameEventRegistries;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_game_event_registry_lookup (Minecraft 1.21.1 with NeoForge 21.1.252, server side incl. the integrated server):
 * GameEventDispatcher.post asks every section in the event's radius for its listener registry through
 * ChunkAccess.getListenerRegistry, which on a LevelChunk creates and keeps an empty registry for every
 * section that has none. The redirected call returns the section's existing registry, or GameEventListenerRegistry.NOOP
 * where there is none, and calls getListenerRegistry itself for anything that is not a plain LevelChunk
 * (GameEventRegistries; why the result is identical is documented there). A @Redirect rather than a @WrapOperation: the
 * call runs 27 times per event and a WrapOperation's per-call Operation object cost about what the lookup saves (measured,
 * see notes/vanilla_game_events.md); no installed mod targets this call. It sits inside post's body, below Valkyrien
 * Skies' @WrapMethod on post. require = 0: a mod that overwrites post leaves the switch out.
 *
 * Ported to 1.21.1: post now takes a Holder<GameEvent> (the redirect selects post by name; the redirected
 * getListenerRegistry(I) call is unchanged). Radium 0.13.1's optional world.game_events.dispatch redirects the same call;
 * the switch yields to it while that option is on (yield in patches/vanilla_game_events.json).
 */
@Mixin(value = GameEventDispatcher.class, remap = false)
public abstract class GameEventDispatcherMixin {
    @Redirect(method = "post", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getListenerRegistry(I)Lnet/minecraft/world/level/gameevent/GameEventListenerRegistry;"))
    private GameEventListenerRegistry bons$existingRegistry(ChunkAccess chunk, int sectionY) {
        return GameEventRegistries.lookup(chunk, sectionY);
    }
}
