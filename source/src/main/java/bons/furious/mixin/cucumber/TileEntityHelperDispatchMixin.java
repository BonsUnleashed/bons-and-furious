package bons.furious.mixin.cucumber;

import bons.furious.patch.cucumber.CucumberTileDispatch;
import com.blakebr0.cucumber.helper.TileEntityHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * cucumber_tile_dispatch_range_first (Cucumber; 1.21.1 tested build: Cucumber 1.21.1-8.0.16 for NeoForge 1.21.1, MIT;
 * both sides, it runs where block entities tick, the server).
 *
 * TileEntityHelper.dispatchToNearbyPlayers(tile) built tile.getUpdatePacket() (the block entity's whole saved data) first
 * and then sent it to every ServerPlayer of the level within 64 blocks. The body below is Cucumber's, with one question
 * asked first when CucumberTileDispatch.applies(tile): is any ServerPlayer of level.players() in range? If not, the
 * original would have built the packet and sent it to nobody, so it returns. Otherwise the packet is built and sent
 * exactly as before, with the range test answered by CucumberTileDispatch.near, which gives Math.hypot's answer (see that
 * class for why the result is identical and which block entities keep the original order). With the runtime switch off,
 * or for a block entity whose class overrides getUpdatePacket, this is the original method.
 *
 * Ported to 1.21.1: Cucumber 8.0.16's dispatchToNearbyPlayers and isPlayerNearby are the 7.0.16 bodies in Mojang names
 * (level(), players(), connection.send); the overwrite is unchanged apart from the names.
 */
@Mixin(value = TileEntityHelper.class, remap = false)
public abstract class TileEntityHelperDispatchMixin {
    @Shadow
    private static boolean isPlayerNearby(double x1, double z1, double x2, double z2) {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason Build the update packet only when a player is in range (cucumber_tile_dispatch_range_first).
     */
    @Overwrite
    public static void dispatchToNearbyPlayers(BlockEntity tile) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        boolean lean = CucumberTileDispatch.applies(tile);
        if (lean && !CucumberTileDispatch.anyNearby(level.players(), tile.getBlockPos())) {
            return;
        }
        Packet<?> packet = tile.getUpdatePacket();
        if (packet == null) {
            return;
        }
        List<? extends Player> players = level.players();
        BlockPos pos = tile.getBlockPos();
        for (Player player : players) {
            if (player instanceof ServerPlayer mPlayer
                    && (lean ? CucumberTileDispatch.near(mPlayer.getX(), mPlayer.getZ(), (double) pos.getX() + 0.5, (double) pos.getZ() + 0.5)
                             : isPlayerNearby(mPlayer.getX(), mPlayer.getZ(), (double) pos.getX() + 0.5, (double) pos.getZ() + 0.5))) {
                mPlayer.connection.send(packet);
            }
        }
    }
}
