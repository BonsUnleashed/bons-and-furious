package bons.portprobe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Development-only (never shipped), 1.0.50 port: the served path of vanilla_chunk_climate_sampler_reuse
 * (-Dbons_and_furious.samplerTwice50=true). Vanilla 1.21.1 asks a NoiseChunk for its climate sampler once, so world
 * generation alone never reaches the path where the switch hands back the sampler it made before; a mod that asks twice
 * (none of the port's arms has one on by default) would. This fixture asks twice: two NoiseChunks of the overworld's
 * generator for one chunk (the public constructor, as NoiseChunk.forChunk calls it, with the overworld's RandomState,
 * settings and Blender.empty()), the protected cachedClimateSampler called through a private lookup with the router and
 * spawn target the generator uses, twice on the first NoiseChunk and once on the second. Reported: whether the two
 * answers of the first NoiseChunk are the same object (true with the switch, false without), and every climate point of
 * the second answer compared with the fresh NoiseChunk's (inside the chunk and in its four neighbours; must not differ).
 */
final class SamplerTwice50 {
    private SamplerTwice50() {
    }

    static Map<String, Object> run(MinecraftServer server) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            ServerLevel level = server.overworld();
            if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
                out.put("error", "the overworld's generator is not a NoiseBasedChunkGenerator: " + level.getChunkSource().getGenerator().getClass().getName());
                return out;
            }
            RandomState random = level.getChunkSource().randomState();
            NoiseGeneratorSettings settings = generator.generatorSettings().value();
            NoiseSettings noise = settings.noiseSettings();
            Class<?> markerType = Class.forName("net.minecraft.world.level.levelgen.DensityFunctions$BeardifierMarker");
            Object marker = MethodHandles.privateLookupIn(markerType, MethodHandles.lookup()).findStaticGetter(markerType, "INSTANCE", markerType).invoke();
            MethodHandle sampler = MethodHandles.privateLookupIn(NoiseChunk.class, MethodHandles.lookup())
                    .findVirtual(NoiseChunk.class, "cachedClimateSampler", MethodType.methodType(Climate.Sampler.class, NoiseRouter.class, List.class));
            Aquifer.FluidStatus fluid = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
            Aquifer.FluidPicker picker = (x, y, z) -> fluid;
            int chunkX = 20, chunkZ = -22;                     // inside the probe's terrain square
            int cells = 16 / noise.getCellWidth();
            NoiseChunk first = new NoiseChunk(cells, random, chunkX << 4, chunkZ << 4, noise, (DensityFunctions.BeardifierOrMarker) marker, settings, picker,
                    Blender.empty());
            NoiseChunk second = new NoiseChunk(cells, random, chunkX << 4, chunkZ << 4, noise, (DensityFunctions.BeardifierOrMarker) marker, settings, picker,
                    Blender.empty());
            NoiseRouter router = random.router();
            List<Climate.ParameterPoint> spawn = settings.spawnTarget();
            Climate.Sampler a = (Climate.Sampler) sampler.invoke(first, router, spawn);
            Climate.Sampler b = (Climate.Sampler) sampler.invoke(first, router, spawn);
            Climate.Sampler fresh = (Climate.Sampler) sampler.invoke(second, router, spawn);
            int compared = 0, different = 0;
            String firstDifference = null;
            int qx0 = QuartPos.fromBlock((chunkX - 1) << 4), qz0 = QuartPos.fromBlock((chunkZ - 1) << 4);
            for (int qx = qx0; qx < qx0 + 12; qx++) {
                for (int qz = qz0; qz < qz0 + 12; qz++) {
                    for (int y : new int[] {-56, 0, 40, 64, 96, 160}) {
                        int qy = QuartPos.fromBlock(y);
                        Climate.TargetPoint got = b.sample(qx, qy, qz), want = fresh.sample(qx, qy, qz);
                        compared++;
                        if (!got.equals(want)) {
                            different++;
                            if (firstDifference == null) firstDifference = qx + "," + qy + "," + qz + ": " + got + " vs " + want;
                        }
                    }
                }
            }
            out.put("same_object", a == b);
            out.put("fresh_is_other_object", fresh != a);
            out.put("compared", compared);
            out.put("different", different);
            if (firstDifference != null) out.put("first_difference", firstDifference);
        } catch (Throwable t) {
            out.put("error", t.toString());
            t.printStackTrace();
        }
        System.out.println("BONS_PORT_SAMPLER_TWICE50 " + out);
        return out;
    }
}
