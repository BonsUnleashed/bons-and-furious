package bons.furious.mixin.bomd_c2;

import bons.furious.patch.bomd_c2.BlockCachePresence;
import com.cerbon.bosses_of_mass_destruction.block.custom.LevitationBlockEntity;
import java.util.HashSet;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * bomd_block_cache_presence (Bosses of Mass Destruction 1.1.2, LGPL; both sides, called on the server), part 4 of 4.
 *
 * LevitationBlockEntity.tickFlight runs for every server player every tick: it builds 9 BlockPos, a stream into a set of
 * chunk positions and a second stream over them that looks the player's surroundings up in the level's block cache,
 * giving hasLevitationBlock, and then grants or revokes flight. While the level's cache holds no levitation block at all
 * (BlockCachePresence) hasLevitationBlock is false; this head injection then runs only the method's own
 * "hasLevitationBlock == false" branch (BOMD's code for it, carried here under its LGPL licence, unchanged: a player
 * still in the static flight set loses mayfly and flying unless creative or spectator, gets the abilities packet and
 * leaves the set; BOMD's quirk of never clearing the set for players who log out is kept) and skips the rest.
 * Otherwise the method runs unchanged.
 */
@Mixin(value = LevitationBlockEntity.class, remap = false)
public abstract class LevitationFlightGateMixin {
    @Shadow
    @Final
    private static HashSet<ServerPlayer> flight;

    @Inject(method = "tickFlight", at = @At("HEAD"), cancellable = true)
    private static void bons$noLevitationBlock(ServerPlayer player, CallbackInfo ci) {
        if (!BlockCachePresence.noLevitationBlock(player.m_9236_())) return;
        if (flight.contains(player)) {
            if (!player.m_7500_() && !player.m_5833_()) {
                player.m_150110_().f_35936_ = false;
                player.m_150110_().f_35935_ = false;
                player.f_8906_.m_9829_(new ClientboundPlayerAbilitiesPacket(player.m_150110_()));
            }
            flight.remove(player);
        }
        ci.cancel();
    }
}
