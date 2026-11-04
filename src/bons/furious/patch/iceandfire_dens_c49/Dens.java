package bons.furious.patch.iceandfire_dens_c49;

import bons.furious.guard.Guards;
import bons.pure.config.PureConfig;
import com.github.alexthe666.iceandfire.IafConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.LevelData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch iceandfire_structure_dens (Ice and Fire 2.1.13-1.20.1-beta-5; server world generation; since
 * 1.0.49; a trade-off, off by default) and its distance switch iceandfire_structure_dens_cave_spawn_distance (off by
 * default, value 800). Idea: Collections Of Optimizations (iafdragonfix.structureDens, iafdragonfix.caveSpawnDistance),
 * idea text only. Ice and Fire is LGPL-3.0: this group calls its cave generator and restates its cave dragon's set-up.
 *
 * Ice and Fire builds its dragon caves (Fire / Ice / Lightning) as plain features. A feature may write only within one
 * chunk of the chunk being decorated, but a cave reaches up to about 50 blocks from its centre, so every write farther
 * away is dropped (the log's "Detected setBlock in a far chunk" lines): caves come out cut off at chunk borders, the big
 * ones missing up to two fifths of their blocks.
 *
 * With the switch on, the three cave features generate nothing (DragonCaveDenMixin) and a structure of our own takes their
 * place (DenRegistration: one structure type, one piece type and a built-in data pack with three structures in one
 * structure set). A structure start is placed with Ice and Fire's own gates (DenStructure: its chance, its distance from the
 * world spawn, its biome filter through the biome's own feature list, the cave depth); its one piece covers the whole cave.
 * When the first chunk of a cave is decorated, the cave is generated ONCE by Ice and Fire's own generator, from the piece's
 * seed, against a view that reads every block the cave has not written as plain stone (DenLevel; the writes stay in the
 * view); every chunk of the cave then writes its own part of that result (DenResult), the dragon is added by the chunk that
 * holds it and each chest gets its loot table there. Ice and Fire's generator reads the ground only to leave block entities
 * and unbreakable blocks alone (its shell and hollow) and to find its treasure's floor; the chunk that writes a part keeps
 * the block entities and unbreakable blocks it holds, so the cave does not depend on how far world generation has got
 * around it, and a cave generated again (it left the cache) is the same cave. The separation between caves is the structure
 * set's spacing (48 chunks, at least 32 apart) instead of Ice and Fire's "last generated" list. Dragon roosts stay Ice and
 * Fire's feature (a roost is shaped by the surface it stands on across five to six chunks, terrain that does not exist yet
 * while the first of those chunks is decorated).
 *
 * What changes (the trade-off): caves are complete across chunk borders and at structure positions (one attempt per 48 x 48
 * chunk cell, accepted with probability min(1, 48 x 48 / chance): one cave per chance chunks as in Ice and Fire while the
 * chance is at least 2,304, at most one per cell below that); a cave's ores, stalactites and treasure are laid out as if the
 * ground around it were stone (Ice and Fire skips a random draw for each block it leaves alone, so in its own generation a
 * chest or spawner in a cave's way changes the rest of that cave's layout); a block entity or unbreakable block a cave
 * overlaps always stays (Ice and Fire's stalactites and treasure could replace one that its shell had kept); the world then
 * has structure starts of bons_and_furious (they are dropped with a log line if the switch is turned off later; blocks
 * already placed stay). Off: nothing is registered and the features are Ice and Fire's.
 *
 * -Dbons_and_furious.iceandfireStructureDens=false (read at start-up) leaves Ice and Fire's features alone.
 */
public final class Dens {
    public static final String KEY = "iceandfire_structure_dens";
    public static final String CAVE_DISTANCE_KEY = "iceandfire_structure_dens_cave_spawn_distance";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** The runtime flag, read when the structures are registered; the harness sets it before the first use. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireStructureDens", "true"));
    /** Makes the dragons (the harness records them instead). */
    public static volatile DragonMaker dragons = DragonMaker.REAL;
    private static volatile Boolean active;
    private static volatile boolean failed;
    /** Set by DenRegistration: both types registered, then the data pack added (only then are the features replaced). */
    static volatile boolean structureTypeRegistered, pieceTypeRegistered, packAdded;

    private Dens() {
    }

    /** True when the structure caves replace Ice and Fire's three cave features (the switch applies and the flag is on). */
    public static boolean active() {
        Boolean a = active;
        if (a == null) {
            synchronized (Dens.class) {
                a = active;
                if (a == null) active = a = enabled && Guards.decide(KEY).state() == Guards.State.APPLY;
            }
        }
        return a;
    }

    /**
     * True when Ice and Fire's cave features stand down: the structure caves are in place (both types registered and the
     * data pack added). A registration that failed leaves the features as they are.
     */
    public static boolean suppressing() {
        return packAdded;
    }

    /** Harness only: forget the cached decision (the flag or the guard table changed). */
    static void reset() {
        active = null;
    }

    /** One WARN for a cave that could not be placed or generated; the chunk then gets no cave. */
    static void failOnce(String what, Throwable t) {
        if (failed) return;
        failed = true;
        LOGGER.warn("Bons and Furious: {} could not {} ({}); that dragon cave is left out", KEY, what, t.toString());
    }

    /** The smallest horizontal distance of a dragon cave from the world spawn. */
    static double caveSpawnDistance() {
        return PureConfig.isEnabled(CAVE_DISTANCE_KEY) ? CaveDistance.VALUE : IafConfig.dangerousWorldGenDistanceLimit;
    }

    private static final class CaveDistance {
        static final int VALUE = PureConfig.intValue("iceandfire_structure_dens_cave_spawn_distance", 800, 0, 30000000);
    }

    /** The world spawn's x and z (Ice and Fire reads the level data's spawn; 0, 0 before the server has one). */
    static int[] spawnXZ() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel overworld = server == null ? null : server.m_129783_();
        if (overworld == null) return new int[] {0, 0};
        LevelData data = overworld.m_6106_();
        return new int[] {data.m_6789_(), data.m_6527_()};
    }

    /** The wrapped WorldGenDragonCave.place: nothing while the structure caves are in place. */
    public static boolean stockCave(FeaturePlaceContext<NoneFeatureConfiguration> context, Operation<Boolean> original) {
        if (suppressing()) return false;
        return original.call(context);
    }
}
