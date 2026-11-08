package bons.furious.patch.companions_coo49;

import dev.xylonity.companions.common.tesla.TeslaNetwork;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch companions_tesla_lazy_distances (Companions 1.3.6 for Minecraft 1.21.1, server).
 *
 * Every change to a dimension's tesla network (TeslaNetwork.registerBlockEntity - called from each tesla block entity's
 * setLevel, i.e. for every coil, pillar, relay and recall platform of a chunk that loads -, unregisterBlockEntity,
 * onConnectionAdded / Removed, indexEntityOutgoing, removeEntityNode) runs recalculateDistances(): every distance reset, the
 * whole dimension's node set rebuilt, a BFS per connected component and, per generator, a scan of the whole incoming map.
 * A base of N tesla blocks loading costs N such rebuilds in one tick.
 *
 * The switch runs the same recalculateDistances() at most once per burst: a mutation marks the network dirty instead
 * (refreshRecallCaches still runs at once, as before), and the rebuild runs before anything can see a distance. A
 * bytecode census of Companions 1.3.6 found the distance field read only in AbstractTeslaBlockEntity.getDistance
 * (WrenchItem's connection check), saveAdditional (the saved "Distance") and getUpdateTag (the client copy), and written
 * only by setDistance (called only by recalculateDistances), the constructor and loadAdditional. Those three readers rebuild
 * first when their network is dirty. The graph inputs a rebuild reads are the network's maps (changed only by the six
 * mutations) and each block entity's outgoing set, which also changes WITHOUT a network call (addOutgoing, removeOutgoing,
 * loadAdditional; every other user of getOutgoing only iterates it): those rebuild first too, so the rebuild always sees the
 * graph as it was at the last mutation, as the original's rebuild did. A rebuild writes only the block entities in the
 * network: one that leaves it (unregisterBlockEntity, or another block entity registered at its position) keeps the distance
 * of the last rebuild before it left, so a pending rebuild runs first there as well. TeslaNetwork.clearAll rebuilds every
 * dirty network before it forgets them. A rebuild reads only those structures (and Companions' chain limit config), builds
 * its own sets and maps fresh each time and writes nothing but the distances, so the deferred rebuild walks the very same
 * maps and sets, in the same iteration order, as the original's last rebuild before the read. (The order can matter: with an
 * outgoing edge that has no incoming entry Companions' result depends on its hash-set walk, so the same graph in another
 * dimension can answer differently; that is Companions' own behaviour and unchanged here.) So every distance anyone reads,
 * saves or sends equals the original's.
 * Not covered (documented): a change of the chain-limit config between a mutation and the next read applies to that
 * pending rebuild. Runtime flag -Dbons_and_furious.companionsTeslaLazyDistances=false (rebuild at every mutation, as
 * before). Shadow mode (.shadow=true): each deferred rebuild also counts how many mutations it absorbed.
 * Companions is GPL-3.0; no code of it is carried (the hooks call its own recalculateDistances).
 */
public final class TeslaDistances {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.companionsTeslaLazyDistances", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.companionsTeslaLazyDistances.shadow");
    public static final AtomicLong DEFERRED = new AtomicLong();
    public static final AtomicLong REBUILDS = new AtomicLong();
    /** TeslaNetwork.NETWORKS, handed over at the end of TeslaNetwork's static initializer (TeslaNetworkAccessor). */
    public static volatile Map<ResourceKey<Level>, TeslaNetwork> networks;
    private static volatile boolean announced;

    private TeslaDistances() {
    }

    /** The network of the level if one exists (never creates one, unlike TeslaNetwork.get). */
    public static TeslaNetwork existing(Level level) {
        Map<ResourceKey<Level>, TeslaNetwork> n = networks;
        return n == null || level == null || level.isClientSide ? null : n.get(level.dimension());
    }

    public static void deferred() {
        DEFERRED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: companions_tesla_lazy_distances: tesla network distances are rebuilt once per burst of changes");
        }
    }

    public static void rebuilt() {
        REBUILDS.incrementAndGet();
    }
}
