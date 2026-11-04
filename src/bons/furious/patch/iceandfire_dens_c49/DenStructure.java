package bons.furious.patch.iceandfire_dens_c49;

import com.github.alexthe666.iceandfire.IafConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * iceandfire_structure_dens (since 1.0.49): the structure of one Ice and Fire dragon cave kind. A start is placed with Ice
 * and Fire's own gates, read at the time and place its feature would have been tried (the chunk's corner): the chance (one
 * roll per structure-set cell, shared by the set's three kinds, accepted with probability min(1, cells / chance)), the
 * horizontal distance from the world spawn, the biome filter (the biome at the chunk corner's lowest block, else on its
 * surface, must hold Ice and Fire's placed feature) and the cave depth (20 blocks below the lowest ocean-floor height of
 * the 20 x 20 square from the corner, at most 40, minus 0..29; at least 20 above the world bottom). The cave's own random
 * source then starts from the structure's random.
 */
public final class DenStructure extends Structure {
    /** The structure set's spacing in chunks (data pack); one attempt per spacing x spacing cell. */
    static final int SPACING = 48;
    static final int CAVE_SALT = 1049021761;

    public static final Codec<DenStructure> CODEC = RecordCodecBuilder.create(i -> i.group(Structure.m_226567_(i),
            DenKind.CODEC.fieldOf("kind").forGetter(s -> s.kind)).apply(i, DenStructure::new));
    public static final StructureType<DenStructure> TYPE = () -> CODEC;

    final DenKind kind;

    DenStructure(Structure.StructureSettings settings, DenKind kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    public StructureType<?> m_213658_() {
        return TYPE;
    }

    @Override
    protected Optional<Structure.GenerationStub> m_214086_(Structure.GenerationContext context) {
        try {
            return find(kind, context);
        } catch (Throwable t) {
            Dens.failOnce("place a dragon cave in chunk " + context.f_226628_(), t);
            return Optional.empty();
        }
    }

    /**
     * Ice and Fire's chance, rolled once per structure-set cell (all three kinds share the roll): accepted with probability
     * min(1, cells / chance), cells = SPACING x SPACING chunks, so a chance of at least that many chunks keeps Ice and Fire's
     * one cave per chance chunks.
     */
    public static boolean chanceAccepts(long seed, int chunkX, int chunkZ, int chance) {
        WorldgenRandom roll = new WorldgenRandom(new LegacyRandomSource(0L));
        roll.m_190058_(seed, chunkX, chunkZ, CAVE_SALT);
        return roll.m_188503_(Math.max(1, chance)) < SPACING * SPACING;
    }

    /** Ice and Fire's spawn distance rule (its isFarEnoughFromSpawn): the horizontal distance is at least the limit. */
    public static boolean farEnough(int x, int z, int spawnX, int spawnZ, double limit) {
        double dx = spawnX - x, dz = spawnZ - z;
        return !(dx * dx + dz * dz < limit * limit);
    }

    static Optional<Structure.GenerationStub> find(DenKind kind, Structure.GenerationContext context) {
        ChunkPos chunk = context.f_226628_();
        long seed = context.f_226627_();
        if (!chanceAccepts(seed, chunk.f_45578_, chunk.f_45579_, IafConfig.generateDragonDenChance)) return Optional.empty();

        ChunkGenerator generator = context.f_226622_();
        RandomState state = context.f_226624_();
        LevelHeightAccessor height = context.f_226629_();
        int x0 = chunk.m_45604_(), z0 = chunk.m_45605_();
        int[] spawn = Dens.spawnXZ();
        if (!farEnough(x0, z0, spawn[0], spawn[1], Dens.caveSpawnDistance())) return Optional.empty();

        int surface = generator.m_214096_(x0, z0, Heightmap.Types.WORLD_SURFACE_WG, height, state);
        Holder<PlacedFeature> placed = context.f_226621_().m_175515_(Registries.f_256988_).m_203636_(kind.placed()).orElse(null);
        if (placed == null) return Optional.empty();
        BiomeSource source = context.f_226623_();
        BiomeManager biomes = new BiomeManager((qx, qy, qz) -> source.m_203407_(qx, qy, qz, state.m_224579_()), BiomeManager.m_47877_(seed));
        boolean inBiome = holds(generator, biomes.m_204214_(new BlockPos(x0, height.m_141937_(), z0)), placed)
                || holds(generator, biomes.m_204214_(new BlockPos(x0, surface, z0)), placed);
        if (!inBiome) return Optional.empty();

        long denSeed = context.f_226626_().m_188505_();
        RandomSource den = new LegacyRandomSource(denSeed);
        int lowest = Integer.MAX_VALUE;
        for (int k = 0; k < 20; k++) {
            for (int l = 0; l < 20; l++) lowest = Math.min(lowest, generator.m_214096_(x0 + k, z0 + l, Heightmap.Types.OCEAN_FLOOR_WG, height, state));
        }
        den.m_188499_();                                   // the gender
        int y = Math.min(lowest, 40) - 20 - den.m_188503_(30);
        if (y < height.m_141937_() + 20) return Optional.empty();
        int age = 75 + den.m_188503_(50);
        int radius = (int) ((float) age * 0.2f) + den.m_188503_(4);
        BlockPos centre = new BlockPos(x0 + 8, y, z0 + 8);
        BoundingBox box = DenPiece.caveBox(centre, radius, height);
        return Optional.of(new Structure.GenerationStub(new BlockPos(x0 + 8, surface, z0 + 8), b -> b.m_142679_(new DenPiece(kind, denSeed, centre, box))));
    }

    private static boolean holds(ChunkGenerator generator, Holder<Biome> biome, Holder<PlacedFeature> placed) {
        return generator.m_223131_(biome).m_186658_(placed.m_203334_());
    }
}
