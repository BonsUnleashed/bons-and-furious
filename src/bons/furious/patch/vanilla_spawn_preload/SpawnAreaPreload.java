package bons.furious.patch.vanilla_spawn_preload;

import java.lang.reflect.Field;
import java.util.Comparator;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * vanilla_spawn_area_preload (Minecraft 1.20.1, Forge 47.4.16): while a server starts (Forge's handleServerStarting, just
 * before ServerStartingEvent is posted, so logins are still closed), the overworld chunks a first login can need are
 * generated and kept loaded at FULL status without ticking: the spawn search area of ServerPlayer.fudgeSpawnLocation (the
 * same rule: sky-lit dimension, not adventure mode, spawn radius capped by the world border, at least 1), at most 64
 * blocks, plus 32 blocks for the two chunks around the player that the entity-ready check needs and one block for boundary
 * collision checks. The tickets live only in this server's memory (never in forced-chunk save data) and end with it.
 * <p>
 * Vanilla keeps 11 spawn chunks around spawn loaded anyway, which already holds this area; the switch is for servers that
 * remove them (ModernFix's mixin.perf.remove_spawn_chunks), where otherwise the first login after a start waits for spawn
 * terrain to generate. Formerly the separate mod Bons Welcome Mat (ac_spawn_preparation 1.0.3).
 * <p>
 * Since 1.0.49 the wait keeps the dedicated server's watchdog informed, as vanilla's own spawn preparation does
 * (MinecraftServer.prepareLevels sets nextTickTime to the clock every 10 ms while it waits). DedicatedServer.initServer
 * starts the ServerWatchdog before handleServerStarting, and the watchdog stops the server when the clock passes
 * nextTickTime by more than max-tick-time (60 s by default): a spawn area that took longer to generate (a big pack, a slow
 * host, spawn chunks removed) stopped the server at its first start, before those chunks were saved. The chunk waits stay
 * as they were; while they run, a helper thread sets nextTickTime to the clock once a second (for at most 15 minutes, so a
 * preparation that really hangs still ends with the watchdog's crash report). nextTickTime is set again by runServer right
 * after initServer returns, so the tick schedule is unchanged; a single player world has no watchdog and nothing changes.
 */
public final class SpawnAreaPreload {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final int MAX_RADIUS = 64;
    /** Radius zero = ticket level 33: FULL, without block or entity ticking. */
    private static final TicketType<ChunkPos> SPAWN = TicketType.m_9462_("bons_and_furious_spawn", Comparator.comparingLong(ChunkPos::m_45588_));
    /** Since 1.0.49: MinecraftServer.nextTickTime (f_129726_, read by ServerWatchdog through getNextTickTime); null when unreadable. */
    private static final Field NEXT_TICK_TIME = nextTickTime();

    private static Field nextTickTime() {
        try {
            Field f = MinecraftServer.class.getDeclaredField("f_129726_");
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: spawn preparation cannot reach the server's next tick time ({}); a long preparation may trip the watchdog", t.toString());
            return null;
        }
    }

    /** What vanilla's prepareLevels does while it waits for spawn chunks: tell the watchdog the server is not stuck. */
    private static void keepWatchdogInformed(MinecraftServer server) {
        if (NEXT_TICK_TIME == null) return;
        try {
            NEXT_TICK_TIME.setLong(server, Util.m_137550_());   // Util.getMillis
        } catch (Throwable t) {
            // a failed write leaves the watchdog as it was: the behaviour before 1.0.49
        }
    }

    private static final long KEEPER_LIMIT_NANOS = 15L * 60L * 1_000_000_000L;

    /** Since 1.0.49: informs the watchdog once a second until interrupted (the preparation ended) or 15 minutes passed. */
    private static Thread startKeeper(MinecraftServer server) {
        if (NEXT_TICK_TIME == null) return null;
        Thread keeper = new Thread(() -> {
            long end = System.nanoTime() + KEEPER_LIMIT_NANOS;
            while (System.nanoTime() < end) {
                keepWatchdogInformed(server);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Bons and Furious spawn preparation");
        keeper.setDaemon(true);
        keeper.start();
        return keeper;
    }

    private static void stopKeeper(Thread keeper) {
        if (keeper == null) return;
        keeper.interrupt();
        try {
            keeper.join(2000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private SpawnAreaPreload() {
    }

    public static void prepare(MinecraftServer server) {
        ServerLevel level = server.m_129783_();
        if (level == null) {
            return;
        }
        BlockPos spawn = level.m_220360_();
        int radius = 0;
        if (level.m_6042_().f_223549_() && server.m_129910_().m_5464_() != GameType.ADVENTURE) {
            radius = Math.max(0, server.m_129803_(level));
            int border = Mth.m_14107_(level.m_6857_().m_61941_(spawn.m_123341_(), spawn.m_123343_()));
            radius = border <= 1 ? 1 : Math.min(radius, border);
        }
        if (radius > MAX_RADIUS) {
            LOGGER.warn("Bons and Furious: spawn preparation: spawn radius {} exceeds the {}-block preparation limit; distant spawn "
                    + "candidates may still need terrain generation", radius, MAX_RADIUS);
            radius = MAX_RADIUS;
        }
        // ChunkMap's entity-ready future needs FULL chunks two chunks around the player's chunk; one more block for
        // boundary collision checks.
        int margin = radius + 32 + 1;
        int minX = (spawn.m_123341_() - margin) >> 4;
        int maxX = (spawn.m_123341_() + margin) >> 4;
        int minZ = (spawn.m_123343_() - margin) >> 4;
        int maxZ = (spawn.m_123343_() + margin) >> 4;
        long start = System.nanoTime();
        int count = (maxX - minX + 1) * (maxZ - minZ + 1);
        LOGGER.info("Bons and Furious: spawn preparation: preparing {} spawn chunks before accepting players", count);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                level.m_7726_().m_8387_(SPAWN, pos, 0, pos);
            }
        }
        Thread keeper = startKeeper(server);
        try {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.m_6325_(x, z);
                }
            }
        } finally {
            stopKeeper(keeper);
            keepWatchdogInformed(server);
        }
        LOGGER.info("Bons and Furious: spawn preparation complete: {} chunks retained without ticking in {} ms; startup may now finish",
                count, (System.nanoTime() - start) / 1_000_000L);
    }
}
