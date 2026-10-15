package bons.portprobe;

import bons.furious.patch.vanilla_spawn_preload.SpawnAreaPreload;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;

/** Checks residency and forced-save isolation without modifying real worlds. */
public final class SpawnOracle42 {
    private static net.minecraft.world.entity.animal.Cow sentinel;
    private static net.minecraft.core.BlockPos isolatedSpawn;

    public static void beginNonTicking(MinecraftServer server) {
        var level = server.overworld();
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, server);
        isolatedSpawn = new net.minecraft.core.BlockPos(16000, 300, 16000);
        level.setDefaultSpawnPos(isolatedSpawn, 0);
        SpawnAreaPreload.prepare(server);
        sentinel = net.minecraft.world.entity.EntityType.COW.create(level);
        if (sentinel == null) throw new AssertionError("No non-ticking sentinel");
        sentinel.moveTo(16000.5, 300, 16000.5, 0, 0);
        if (!level.addFreshEntity(sentinel)) throw new AssertionError("Sentinel insertion failed");
    }

    public static Map<String, Object> finishNonTicking(MinecraftServer server) {
        if (sentinel == null || sentinel.isRemoved()) throw new AssertionError("Missing non-ticking sentinel");
        int ticks = sentinel.tickCount;
        boolean resident = server.overworld().getChunkSource().getChunkNow(isolatedSpawn.getX() >> 4, isolatedSpawn.getZ() >> 4) != null;
        sentinel.discard();
        sentinel = null;
        if (!resident || ticks != 0) throw new AssertionError("Preload residency/ticking: resident=" + resident + " ticks=" + ticks);
        return Map.of("passed", true, "entity_ticks", ticks, "resident", resident, "vanilla_spawn_radius", 0);
    }

    public static Map<String, Object> run(MinecraftServer server) {
        var level = server.overworld();
        var spawn = level.getSharedSpawnPos();
        var forced = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(level.getForcedChunks());
        int radius = 0;
        if (level.dimensionType().hasSkyLight() && server.getWorldData().getGameType() != GameType.ADVENTURE) {
            int border = Mth.floor(level.getWorldBorder().getDistanceToBorder(spawn.getX(), spawn.getZ()));
            radius = border <= 1 ? 1 : Math.min(Math.max(0, server.getSpawnRadius(level)), border);
        }
        int margin = Math.min(64, radius) + 33;
        int count = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int x = (spawn.getX() - margin) >> 4; x <= (spawn.getX() + margin) >> 4; x++)
                for (int z = (spawn.getZ() - margin) >> 4; z <= (spawn.getZ() + margin) >> 4; z++) {
                    if (level.getChunkSource().getChunkNow(x, z) == null)
                        throw new AssertionError("Spawn chunk not resident " + x + "," + z);
                    count++;
                }
            if (!forced.equals(level.getForcedChunks())) throw new AssertionError("Preload changed forced chunks");
            if (pass == 0) SpawnAreaPreload.prepare(server);
        }
        return Map.of("passed", true, "resident_checks", count, "repeat_prepare", true,
                "forced_chunks_unchanged", true);
    }
}