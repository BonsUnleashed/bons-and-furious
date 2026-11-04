package bons.furious.patch.undergarden_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group undergarden_coo49 (The Undergarden 0.8.14, MIT; since 1.0.49): the helper of five switches on
 * three Undergarden mobs. Every hook below sits in the mob's own aiStep (m_8107_), after its super call.
 *
 * <ul>
 * <li>undergarden_scintling_goo_server_only (fix, on by default; matters on the client): Scintling.aiStep asks Forge's
 * mob griefing event and then puts goo blocks into the up to four air positions under it, with no side check (vanilla's
 * snow golem guards the same trail with !level.isClientSide). On a client the event runs with the client's own game rules
 * (mobGriefing is never sent to clients, so it is the default, true) and the client's interpolated position, so the
 * client places goo the server may never place (with mobGriefing off, or where the positions differ): ghost blocks with
 * a collision box that hold players, until the chunk is resent. The switch answers the event false on a client level,
 * without posting it, so the client places nothing and the goo appears when the server's block update arrives, as for
 * every block a mob places.</li>
 * <li>undergarden_scintling_idle_goo_interval (trade-off, off by default): while a scintling stands exactly where it was
 * at its last goo attempt, the event is posted (and the four positions tried) only every N ticks; any movement attempts at
 * once. Noticeable: goo mined from under a motionless scintling grows back up to N - 1 ticks later, and event listeners see
 * fewer EntityMobGriefingEvent posts. Value undergarden_scintling_idle_goo_interval.value (default 10).</li>
 * <li>undergarden_muncher_mob_griefing (gameplay fix, off by default): Muncher.aiStep eats blocks when it is aggressive and
 * (bumped into a wall, or bumped into a floor or ceiling and the mob griefing event allows it): the game rule never
 * guards the wall case. With the switch the wall case asks the same event (one post per aiStep at most, as before for the
 * floor and ceiling), so mobGriefing off, or a claim mod that denies the event, also keeps munchers out of walls.</li>
 * <li>undergarden_rotspawn_repel_player_radius (trade-off, off by default): every 40 ticks each rotspawn reads the block
 * state of all 9,801 positions of a 33 x 9 x 33 box, looking for hoglin repellents to flee from. With the switch the
 * search is left out (as if nothing were found) while no player is within R blocks. Noticeable: rotspawns with no player
 * near do not run from warped fungi, respawn anchors or nether portals. Value ...player_radius.value (default 48).</li>
 * <li>undergarden_rotspawn_repel_stagger (trade-off, off by default): the search runs on ticks where tickCount % 40 is 0,
 * so a group spawned together searches on one tick; the switch adds a fixed per-mob offset (from the entity id) to that
 * test: the same number of searches per mob per 40 ticks, spread over the ticks. Noticeable: each rotspawn checks for
 * repellents up to 39 ticks earlier or later than it would.</li>
 * </ul>
 *
 * Runtime flags (emergency off, the original on every call): -Dbons_and_furious.undergardenScintlingGooServerOnly=false,
 * ...undergardenScintlingIdleGooInterval, ...undergardenMuncherMobGriefing, ...undergardenRotspawnRepelPlayerRadius,
 * ...undergardenRotspawnRepelStagger. The Undergarden is MIT; no code of it is carried.
 */
public final class UndergardenScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    public static volatile boolean gooServerOnly = flag("undergardenScintlingGooServerOnly");
    public static volatile boolean idleGoo = flag("undergardenScintlingIdleGooInterval");
    public static volatile boolean muncherGriefing = flag("undergardenMuncherMobGriefing");
    public static volatile boolean repelRadius = flag("undergardenRotspawnRepelPlayerRadius");
    public static volatile boolean repelStagger = flag("undergardenRotspawnRepelStagger");

    private static volatile boolean announcedGoo, announcedIdle, announcedMuncher, announcedRadius, announcedStagger;

    private UndergardenScans() {
    }

    private static boolean flag(String camel) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
    }

    /** Ticks between goo attempts of a motionless scintling (read on first use, only while the switch is on). */
    public static final class IdleGoo {
        public static final int N = PureConfig.intValue("undergarden_scintling_idle_goo_interval", 10, 1, 200);

        private IdleGoo() {
        }
    }

    /** Blocks within which a player keeps a rotspawn's repellent search running (read on first use, switch on only). */
    public static final class RepelRadius {
        public static final int R = PureConfig.intValue("undergarden_rotspawn_repel_player_radius", 48, 1, 256);

        private RepelRadius() {
        }
    }

    /** undergarden_scintling_goo_server_only: the mob griefing call in Scintling.aiStep; false on a client level, unposted. */
    public static boolean gooGriefing(Level level, Entity scintling, Operation<Boolean> original) {
        if (gooServerOnly && level.m_5776_()) {
            if (!announcedGoo) announce(1);
            return false;
        }
        return original.call(level, scintling);
    }

    /**
     * undergarden_scintling_idle_goo_interval: the same call; while the scintling stands exactly where its last attempt was
     * and fewer than N ticks have passed since, false without the event (the original then returns before the goo loop).
     */
    public static boolean idleGriefing(ScintlingGooState state, Level level, Entity scintling, Operation<Boolean> original) {
        if (!idleGoo || IdleGoo.N <= 1) return original.call(level, scintling);
        double x = scintling.m_20185_(), y = scintling.m_20186_(), z = scintling.m_20189_();
        int tick = scintling.f_19797_;
        if (state.bons$undergardenCoo49GooTried() && x == state.bons$undergardenCoo49GooX() && y == state.bons$undergardenCoo49GooY()
                && z == state.bons$undergardenCoo49GooZ()) {
            int since = tick - state.bons$undergardenCoo49GooTick();
            if (since >= 0 && since < IdleGoo.N) return false;
        }
        state.bons$undergardenCoo49GooAttempt(x, y, z, tick);
        if (!announcedIdle) announce(2);
        return original.call(level, scintling);
    }

    /**
     * undergarden_muncher_mob_griefing: the isAggressive() result in Muncher.aiStep. Aggressive and against a wall: the mob
     * griefing event decides (the original then reads the wall flag, true, and eats only when this answered true);
     * otherwise the original value (the floor and ceiling case posts the event as before).
     */
    public static boolean muncherAggressive(boolean aggressive, Entity muncher) {
        if (!muncherGriefing || !aggressive || !muncher.f_19862_) return aggressive;
        if (!announcedMuncher) announce(3);
        return ForgeEventFactory.getMobGriefingEvent(muncher.m_9236_(), muncher);
    }

    /** undergarden_rotspawn_repel_player_radius: the BlockPos.findClosestMatch call; empty without a player within R. */
    public static Optional<BlockPos> repelSearch(Entity rotspawn, BlockPos center, int width, int height, Predicate<BlockPos> test,
                                                 Operation<Optional<BlockPos>> original) {
        if (repelRadius && !playerWithin(rotspawn, RepelRadius.R)) {
            if (!announcedRadius) announce(4);
            return Optional.empty();
        }
        return original.call(center, width, height, test);
    }

    /** True when any player of the mob's level (any game mode) is within r blocks of it. */
    public static boolean playerWithin(Entity mob, int r) {
        double max = (double) r * r;
        List<? extends Player> players = mob.m_9236_().m_6907_();
        for (int i = 0, n = players.size(); i < n; i++)
            if (players.get(i).m_20280_(mob) <= max) return true;
        return false;
    }

    /** undergarden_rotspawn_repel_stagger: the tickCount read of the "tickCount % 40 == 0" test, shifted per mob. */
    public static int staggeredTick(int tickCount, Entity rotspawn) {
        if (!repelStagger) return tickCount;
        if (!announcedStagger) announce(5);
        return tickCount + Math.floorMod(rotspawn.m_19879_() * 31, 40);
    }

    private static synchronized void announce(int which) {
        switch (which) {
            case 1 -> {
                if (announcedGoo) return;
                announcedGoo = true;
                LOGGER.info("Bons and Furious: undergarden_scintling_goo_server_only: scintlings lay their goo trail on the server only");
            }
            case 2 -> {
                if (announcedIdle) return;
                announcedIdle = true;
                LOGGER.info("Bons and Furious: undergarden_scintling_idle_goo_interval: a motionless scintling tries its goo every {} ticks", IdleGoo.N);
            }
            case 3 -> {
                if (announcedMuncher) return;
                announcedMuncher = true;
                LOGGER.info("Bons and Furious: undergarden_muncher_mob_griefing: munchers ask the mob griefing rule before eating walls");
            }
            case 4 -> {
                if (announcedRadius) return;
                announcedRadius = true;
                LOGGER.info("Bons and Furious: undergarden_rotspawn_repel_player_radius: rotspawns search for repellents only with a player within {} blocks", RepelRadius.R);
            }
            default -> {
                if (announcedStagger) return;
                announcedStagger = true;
                LOGGER.info("Bons and Furious: undergarden_rotspawn_repel_stagger: rotspawn repellent searches are spread over the 40-tick cycle");
            }
        }
    }
}
