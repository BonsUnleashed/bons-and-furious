package bons.furious.patch.cucumber;

import com.blakebr0.cucumber.tileentity.BaseTileEntity;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cucumber_tile_dispatch_range_first (Cucumber 1.20.1-7.0.16, server side in practice). No
 * Cucumber code here.
 *
 * Cucumber's TileEntityHelper.dispatchToNearbyPlayers(tile) built the block entity's full update packet (the whole
 * saveWithoutMetadata NBT: inventory, energy, progress) and only then sent it to each ServerPlayer of the level within 64
 * blocks horizontally (Math.hypot(dx, dz) < 64). Mystical Agriculture's machines call it at the end of every tick in which
 * they changed, which for a running machine is every tick, so an unattended machine serialised its NBT every tick for
 * nobody. TileEntityHelperDispatchMixin asks the same question first: when no ServerPlayer is in range the packet would
 * have been sent to no one, so it is not built.
 *
 * Why the result is identical: the range test is Cucumber's own predicate. withinRange decides Math.hypot(dx, dz) < 64
 * from dx*dx + dz*dz when the squared distance is more than a relative 1e-9 away from 4096 (far beyond the rounding of
 * both computations: Math.hypot is within 1 ulp of the exact value, the squared sum within 3 ulps) and calls Math.hypot
 * itself in that band, for NaN and for infinite values. The packet is still built, and sent to the same players in the
 * same order, whenever at least one player is in range. The skip applies only to block entities whose getUpdatePacket is
 * Cucumber's BaseTileEntity one (saveWithoutMetadata, the code that writes the block entity into the world save, without
 * side effects) or vanilla's default (null); a class that overrides getUpdatePacket keeps the original order. One error
 * case differs by design: a block entity whose save code throws now throws only while a player is in range (it fails
 * every world save as well).
 *
 * -Dbons_and_furious.cucumberTileDispatchRangeFirst.shadow=true (verification runs only) also evaluates Math.hypot for
 * every range test and counts disagreements in SHADOW_CHECKS / SHADOW_MISMATCHES; the fast answer is the one used.
 */
public final class CucumberTileDispatch {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.cucumberTileDispatchRangeFirst=false runs Cucumber's original order. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cucumberTileDispatchRangeFirst", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cucumberTileDispatchRangeFirst.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** 64 * 64 and the relative band around it in which Math.hypot decides. */
    private static final double RANGE_SQ = 64.0 * 64.0, BELOW = RANGE_SQ * (1.0 - 1.0E-9), ABOVE = RANGE_SQ * (1.0 + 1.0E-9);
    private static volatile boolean announced;

    /**
     * True when the class builds its update packet with Cucumber's BaseTileEntity code or vanilla's default (null).
     * BlockEntity.getUpdatePacket is m_58483_; BaseTileEntity declares getUpdatePacket() with the covariant return type
     * ClientboundBlockEntityDataPacket plus the bridge m_58483_ that calls it, so both must still be BaseTileEntity's.
     */
    private static final ClassValue<Boolean> PLAIN_UPDATE_PACKET = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Class<?> owner = type.getMethod("m_58483_").getDeclaringClass();
                boolean plain = owner == BlockEntity.class
                        || owner == BaseTileEntity.class && type.getMethod("getUpdatePacket").getDeclaringClass() == BaseTileEntity.class;
                if (!plain) LOGGER.info("Bons and Furious: cucumber_tile_dispatch_range_first leaves {} to its own getUpdatePacket and builds its packet first, as before",
                        type.getName());
                return plain;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private CucumberTileDispatch() {
    }

    /** The lean order applies to this block entity: the switch is on and its getUpdatePacket has no side effects. */
    public static boolean applies(BlockEntity tile) {
        if (!enabled || !PLAIN_UPDATE_PACKET.get(tile.getClass())) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cucumber_tile_dispatch_range_first applies (Cucumber's block entity updates are built only when a player is in range){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return true;
    }

    /** Cucumber's isPlayerNearby(x1, z1, x2, z2): Math.hypot(x1 - x2, z1 - z2) < 64, decided without hypot away from the boundary. */
    public static boolean near(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2, dz = z1 - z2;
        double sq = dx * dx + dz * dz;
        boolean in;
        if (sq < BELOW) in = true;
        else if (sq > ABOVE) in = false;
        else in = Math.hypot(dx, dz) < 64.0;   // the band around 64 blocks, NaN, and infinite or overflowing values
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            boolean hypot = Math.hypot(dx, dz) < 64.0;
            if (hypot != in && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: cucumber_tile_dispatch_range_first shadow mismatch for dx={} dz={} (fast {}, hypot {})", dx, dz, in, hypot);
        }
        return in;
    }

    /** True when a ServerPlayer in the list is within Cucumber's range of the block entity's centre. */
    public static boolean anyNearby(List<? extends Player> players, BlockPos pos) {
        double x = (double) pos.m_123341_() + 0.5, z = (double) pos.m_123343_() + 0.5;
        for (Player player : players) {
            if (player instanceof ServerPlayer p && near(p.m_20185_(), p.m_20189_(), x, z)) return true;
        }
        return false;
    }
}
