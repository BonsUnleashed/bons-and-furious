package bons.furious.patch.tome_of_wonders_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches of group tome_of_wonders_coo49 (Tome of Wonders 1.0.0, MIT; server). Ours; no Tome of Wonders
 * code is carried. Idea: Collections Of Optimizations (tomeofwonders.baitfishSchoolCap,
 * tomeofwonders.safeSquillSchoolWorldgen), idea text only.
 *
 * tome_of_wonders_squill_school_main_thread (fix): the squill is a CREATURE, so vanilla also spawns it while its chunk
 * generates (ChunkStatus SPAWN, on a world generation worker thread). Its finalizeSpawn then calls spawnSchool, which
 * builds 0-19 more squills on the live ServerLevel and adds each with level().addFreshEntity, from that worker: the
 * level's entity manager and UUID set are changed off the server thread while the server thread uses them (Forge posts
 * EntityJoinLevelEvent there too), a crash or a corrupt entity list waiting to happen. The fix hands exactly those adds
 * to the server thread (MinecraftServer.execute: the next task run of the server, also while it waits for chunks), so the
 * same members with the same positions join the level, from the right thread; the answer the original would give is
 * discarded by spawnSchool, ours is true. On the server thread (natural spawns) the original call. A stopping server
 * runs the task at once, as the original would. Flag: -Dbons_and_furious.tomeOfWondersSquillSchoolMainThread=false.
 *
 * tome_of_wonders_baitfish_school_cap (trade-off, off by default): Baitfish.finalizeSpawn (NATURAL and CHUNK_GENERATION
 * spawns, with the mod's baitfishSchoolSpawning option on) adds (int)(72 * nextFloat()) more baitfish directly, past the
 * water-ambient mob cap, and the spawner places them in packs, so one spawn attempt can add a few hundred fish. The switch
 * caps the 72 at the key's value (COO's default 24; 72 = the original). Flag: -Dbons_and_furious.tomeOfWondersBaitfishSchoolCap=false.
 */
public final class TowSchools {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean squillEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tomeOfWondersSquillSchoolMainThread", "true"));
    public static volatile boolean capEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tomeOfWondersBaitfishSchoolCap", "true"));
    /** School members handed to the server thread (for the harness and the in-game check). */
    public static final AtomicLong HANDED_OVER = new AtomicLong();
    private static volatile boolean squillAnnounced, capAnnounced;

    private TowSchools() {
    }

    /** The cap, read on the switch-on path only (first use). */
    private static final class Cap {
        static final int VALUE = PureConfig.intValue("tome_of_wonders_baitfish_school_cap", 24, 0, 72);
    }

    public static int capValue() {
        return Cap.VALUE;
    }

    /** From BaitfishSchoolCapMixin: the school size Baitfish.finalizeSpawn uses. */
    public static int cap(int size) {
        if (!capEnabled) return size;
        if (!capAnnounced) {
            capAnnounced = true;
            LOGGER.info("Bons and Furious: tome_of_wonders_baitfish_school_cap applies (natural baitfish schools at most {} extra fish; off by default)", Cap.VALUE);
        }
        return Math.min(size, Cap.VALUE);
    }

    /** From SquillSchoolThreadMixin: Squill.spawnSchool's level.addFreshEntity(member). */
    public static boolean add(Level level, Entity member, Operation<Boolean> original) {
        if (squillEnabled && level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.m_7654_();
            if (server != null && !server.m_18695_()) {
                HANDED_OVER.incrementAndGet();
                if (!squillAnnounced) {
                    squillAnnounced = true;
                    LOGGER.info("Bons and Furious: tome_of_wonders_squill_school_main_thread applies (a squill school spawned during world generation joins the level on the server thread)");
                }
                server.execute(() -> original.call(level, member));
                return true;
            }
        }
        return original.call(level, member);
    }
}
