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
 * cucumber_tile_dispatch_range_first (Cucumber 1.20.1-7.0.16, MIT, both sides; it runs where block entities tick, the
 * server).
 *
 * TileEntityHelper.dispatchToNearbyPlayers(tile) built tile.getUpdatePacket() (the block entity's whole NBT) first and then
 * sent it to every ServerPlayer of the level within 64 blocks. The body below is Cucumber's, with one question asked
 * first when CucumberTileDispatch.applies(tile): is any ServerPlayer of level.players() in range? If not, the original would
 * have built the packet and sent it to nobody, so it returns. Otherwise the packet is built and sent exactly as before,
 * with the range test answered by CucumberTileDispatch.near, which gives Math.hypot's answer (see that class for why the
 * result is identical and which block entities keep the original order). With the runtime switch off, or for a block entity
 * whose class overrides getUpdatePacket, this is the original method.
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
        Level level = tile.m_58904_();
        if (level == null) {
            return;
        }
        boolean lean = CucumberTileDispatch.applies(tile);
        if (lean && !CucumberTileDispatch.anyNearby(level.m_6907_(), tile.m_58899_())) {
            return;
        }
        Packet<?> packet = tile.m_58483_();
        if (packet == null) {
            return;
        }
        List<? extends Player> players = level.m_6907_();
        BlockPos pos = tile.m_58899_();
        for (Player player : players) {
            if (player instanceof ServerPlayer mPlayer
                    && (lean ? CucumberTileDispatch.near(mPlayer.m_20185_(), mPlayer.m_20189_(), (double) pos.m_123341_() + 0.5, (double) pos.m_123343_() + 0.5)
                             : isPlayerNearby(mPlayer.m_20185_(), mPlayer.m_20189_(), (double) pos.m_123341_() + 0.5, (double) pos.m_123343_() + 0.5))) {
                mPlayer.f_8906_.m_9829_(packet);
            }
        }
    }
}
