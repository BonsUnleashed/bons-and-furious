package bons.furious.mixin.mutantmonsters_c2;

import bons.furious.patch.mutantmonsters_c2.EmptyShoulderSkip;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import fuzs.mutantmonsters.handler.PlayerEventsHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mutantmonsters_empty_shoulder_skip (Mutant Monsters, AGPL-3.0; 1.21.1 tested build: Mutant Monsters v21.1.1 for
 * NeoForge 1.21.1; both sides).
 *
 * PlayerEventsHandler.onEndPlayerTick calls playShoulderEntitySound(player, shoulder tag) for the left and the right
 * shoulder on every player tick. The condition wraps those two calls: a shoulder tag without an "id" key (an empty
 * shoulder) skips the call, because Mutant Monsters' check can only find nothing there (see EmptyShoulderSkip); every
 * other tag, and a null tag, makes the call exactly as before. Mutant Monsters' methods are unchanged.
 *
 * Ported to 1.21.1: the method selector is onEndPlayerTick (1.20.1: onPlayerTick$End); the two calls, the shoulder
 * getters before them and their order are the same (javap).
 */
@Mixin(value = PlayerEventsHandler.class, remap = false)
public abstract class PlayerEventsHandlerShoulderMixin {
    @WrapWithCondition(method = "onEndPlayerTick", at = @At(value = "INVOKE",
            target = "Lfuzs/mutantmonsters/handler/PlayerEventsHandler;playShoulderEntitySound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/nbt/CompoundTag;)V"))
    private static boolean bons$shoulderMayHoldMinion(Player player, CompoundTag tag) {
        return !EmptyShoulderSkip.nothingToPlay(tag);
    }
}
