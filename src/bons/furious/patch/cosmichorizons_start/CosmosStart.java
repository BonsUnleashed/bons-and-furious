package bons.furious.patch.cosmichorizons_start;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.LongAdder;
import net.lointain.cosmos.network.CosmosModVariables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Bons and Furious switches cosmichorizons_sync_without_players_skip and cosmichorizons_reload_unchanged_skip
 * (Cosmic Horizons 0.0.7.3). SRG member names.
 *
 * 1. WorldVariables.syncData(level) marks the saved data dirty and sends the whole variable set to every player in
 *    that dimension: SimpleChannel.send(PacketDistributor.DIMENSION.with(level::dimension), message), which encodes
 *    the message (all planet data as NBT) and hands the packet to PlayerList.broadcastAll(packet, dimension). Cosmic
 *    Horizons calls it about ten times per initialisation of a dimension. {@link #nobodyThere} is that broadcast's own
 *    test (a player whose level's dimension key is this one); when nobody passes it the send is left out: nothing was
 *    going to receive it, and encoding has no other effect.
 *
 * 2. TestRestartProcedure.DatapackReloadProcedure.reloadJsonObjects(server) gives every loaded dimension a new
 *    datapack list (the same content for all of them), syncs and runs InitialiseregistryProcedure.execute(level), which
 *    rebuilds the dimension's variables from that list alone. Cosmic Horizons runs it on a level's load when that
 *    level has no data yet (in a new world: on every level load, for all levels loaded so far) and once more when the
 *    server has started. While the server is starting (MinecraftServer.isReady() is still false), a dimension whose
 *    datapack list already equals the new one was initialised from equal data, so the three steps are left out for it.
 *    The first three such dimensions of every server start (all of them with -Dbons_and_furious.cosmosStart.verify=true)
 *    run the original steps anyway and their saved variables are compared before and after; a difference switches
 *    this off for the session and is logged.
 *
 * -Dbons_and_furious.cosmosStart=false keeps both as shipped; .metrics=true prints a summary when the JVM exits.
 */
public final class CosmosStart {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean ACTIVE = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cosmosStart", "true"));
    static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.cosmosStart.verify");
    static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.cosmosStart.metrics");
    public static final LongAdder SYNC_SKIPPED = new LongAdder(), SYNC_SENT = new LongAdder(), INIT_SKIPPED = new LongAdder(), INIT_RUN = new LongAdder(),
            COMPARED = new LongAdder(), MISMATCHED = new LongAdder();

    // reloadJsonObjects is static synchronized: these are only touched inside it
    private static int mode;                       // 0 original, 1 leave out, 2 run and compare
    private static CompoundTag before;
    private static volatile boolean reloadOff;
    private static MinecraftServer canaryServer;
    private static int canaryLeft;

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious Cosmic Horizons start: syncs without a receiver left out "
                    + SYNC_SKIPPED.sum() + ", syncs sent " + SYNC_SENT.sum() + ", unchanged initialisations left out " + INIT_SKIPPED.sum() + ", initialisations run "
                    + INIT_RUN.sum() + ", compared " + COMPARED.sum() + ", differences " + MISMATCHED.sum()), "Bons and Furious Cosmic Horizons summary"));
        }
    }

    private CosmosStart() {
    }

    /** True when PlayerList.broadcastAll(packet, level.dimension()) would reach no player. */
    public static boolean nobodyThere(LevelAccessor world) {
        if (!ACTIVE || !(world instanceof ServerLevel level)) return false;
        try {
            MinecraftServer server = level.m_7654_();
            if (server == null) return false;
            Object key = level.m_46472_();
            for (ServerPlayer p : server.m_6846_().m_11314_()) {
                if (p.m_9236_().m_46472_() == key) {
                    if (METRICS) SYNC_SENT.increment();
                    return false;
                }
            }
        } catch (ConcurrentModificationException e) {
            return false;                           // the list changed under us: do what the original does
        }
        if (METRICS) SYNC_SKIPPED.increment();
        return true;
    }

    private static boolean starting() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // m_129920_ = isReady() (field f_129717_, set after the first tick). NOT m_130010_ = isRunning(), which is true for the
        // whole start: dev1 to dev3 used that one, and the switch never acted (rig p50g_metrics: 0 left out, 629 run).
        if (server == null || server.m_129920_()) return false;
        if (server != canaryServer) {
            canaryServer = server;
            canaryLeft = 3;
        }
        return true;
    }

    /** reloadJsonObjects: WorldVariables.get(level).datapack = list. */
    public static void assign(CosmosModVariables.WorldVariables vars, ListTag list, Operation<Void> original) {
        mode = 0;
        ListTag current = vars.datapack;
        if (ACTIVE && !reloadOff && current != null && !current.isEmpty() && current.equals(list) && starting()) {
            if (VERIFY || canaryLeft > 0) {
                canaryLeft--;
                before = vars.m_7176_(new CompoundTag());
                mode = 2;
            } else {
                mode = 1;
                if (METRICS) INIT_SKIPPED.increment();
                return;                             // the dimension keeps its equal list
            }
        }
        original.call(vars, list);
    }

    /** reloadJsonObjects: WorldVariables.get(level).syncData(level). */
    public static void sync(CosmosModVariables.WorldVariables vars, LevelAccessor level, Operation<Void> original) {
        if (mode != 1) original.call(vars, level);
    }

    /** reloadJsonObjects: InitialiseregistryProcedure.execute(level). */
    public static void init(LevelAccessor level, Operation<Void> original) {
        int m = mode;
        mode = 0;
        if (m == 1) return;
        original.call(level);
        if (METRICS) INIT_RUN.increment();
        if (m == 2) {
            CompoundTag was = before;
            before = null;
            CompoundTag now = CosmosModVariables.WorldVariables.get(level).m_7176_(new CompoundTag());
            COMPARED.increment();
            if (!now.equals(was)) {
                MISMATCHED.increment();
                reloadOff = true;
                LOGGER.warn("Bons and Furious: cosmichorizons_reload_unchanged_skip switched off for this session: initialising a dimension again from "
                        + "equal data changed its saved variables");
            }
        }
    }
}
