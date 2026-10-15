package bons.furious.patch.vanilla_spawn_preload;

import java.util.Comparator;
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
 * vanilla_spawn_area_preload (Minecraft 1.21.1, NeoForge 21.1.252): while a server starts (NeoForge's handleServerStarting, just
 * before ServerStartingEvent is posted, so logins are still closed), the overworld chunks a first login can need are
 * generated and kept loaded at FULL status without ticking: the spawn search area of ServerPlayer.fudgeSpawnLocation (the
 * same rule: sky-lit dimension, not adventure mode, spawn radius capped by the world border, at least 1), at most 64
 * blocks, plus 32 blocks for the two chunks around the player that the entity-ready check needs and one block for boundary
 * collision checks. The tickets live only in this server's memory (never in forced-chunk save data) and end with it.
 * <p>
 * The retained vanilla spawn area depends on spawnChunkRadius; the switch also covers servers that
 * remove them (ModernFix's mixin.perf.remove_spawn_chunks), where otherwise the first login after a start waits for spawn
 * terrain to generate. Formerly the separate mod Bons Welcome Mat (ac_spawn_preparation 1.0.3).
 */
public final class SpawnAreaPreload {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final int MAX_RADIUS = 64;
    /** Radius zero = ticket level 33: FULL, without block or entity ticking. */
    private static final TicketType<ChunkPos> SPAWN = TicketType.create("bons_and_furious_spawn", Comparator.comparingLong(ChunkPos::toLong));

    private SpawnAreaPreload() {
    }

    public static void prepare(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int radius = 0;
        if (level.dimensionType().hasSkyLight() && server.getWorldData().getGameType() != GameType.ADVENTURE) {
            radius = Math.max(0, server.getSpawnRadius(level));
            int border = Mth.floor(level.getWorldBorder().getDistanceToBorder(spawn.getX(), spawn.getZ()));
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
        int minX = (spawn.getX() - margin) >> 4;
        int maxX = (spawn.getX() + margin) >> 4;
        int minZ = (spawn.getZ() - margin) >> 4;
        int maxZ = (spawn.getZ() + margin) >> 4;
        long start = System.nanoTime();
        int count = (maxX - minX + 1) * (maxZ - minZ + 1);
        LOGGER.info("Bons and Furious: spawn preparation: preparing {} spawn chunks before accepting players", count);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                level.getChunkSource().addRegionTicket(SPAWN, pos, 0, pos);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.getChunk(x, z);
            }
        }
        LOGGER.info("Bons and Furious: spawn preparation complete: {} chunks retained without ticking in {} ms; startup may now finish",
                count, (System.nanoTime() - start) / 1_000_000L);
    }
}
