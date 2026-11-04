package bons.furious.patch.somakespells_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.somakespells.capabilities.ConnectionDataHelper;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch somakespells_connection_tick_precheck (Somake Spells 1.0.9, All Rights Reserved; server; exact).
 * Ours; no Somake Spells code is carried. Idea: Collections Of Optimizations (somakespells.skipUnlinkedConnectionTick), idea
 * text only.
 *
 * What the mod does. ConnectionEventHandler.onLivingTick runs for every living entity on every tick. On a client level it
 * returns at once. On a server level it asks ConnectionDataHelper for the entity's pending cast (players), transfer queue and
 * link data: each getter makes a new data object and reads getPersistentData().getCompound(tag), a new empty CompoundTag when
 * the tag is absent, and fills the object only from a non-empty compound. An empty pending cast has no target, an empty queue
 * has no transfers (processPendingTransfers returns before it writes) and empty link data has no link (the handler returns).
 * So for an entity whose persistent data holds none of the three tags the whole call only creates objects and writes nothing;
 * getPersistentData() itself creates Forge's empty ForgeData compound the first time, as stock does.
 *
 * What the switch does (SomakeConnectionTickMixin): on a server level the entity's persistent data is fetched (the same
 * getPersistentData() call the getters make first, so ForgeData appears exactly as before) and checked for the tags with
 * CompoundTag.contains; when SomakeConnectionTransferQueue and SomakeConnectionLink are absent, and the entity is not a
 * ServerPlayer or SomakeConnectionPendingCast is absent, the handler's level value is null and it returns. Any of those tags
 * present (of any tag type), a client level or the flag off: the original value, and the handler runs as shipped.
 *
 * Runtime flag: -Dbons_and_furious.somakespellsConnectionTickPrecheck=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.somakespellsConnectionTickPrecheck.shadow=true never skips; where the switch would, it asks the mod's
 * own getters and counts a mismatch if any of them holds something (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class SomakeConnections {
    public static final String KEY = "somakespells_connection_tick_precheck";
    public static final String PENDING = "SomakeConnectionPendingCast", LINK = "SomakeConnectionLink", QUEUE = "SomakeConnectionTransferQueue";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.somakespellsConnectionTickPrecheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.somakespellsConnectionTickPrecheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): handler calls that returned at once, calls that ran as shipped on a server level. */
    public static long skipped, ran;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private SomakeConnections() {
    }

    /** True when the handler can do nothing but build empty data objects for this entity (see the class comment). */
    public static boolean unlinked(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.m_128441_(QUEUE) || data.m_128441_(LINK)) return false;
        return !(entity instanceof ServerPlayer) || !data.m_128441_(PENDING);
    }

    /** The handler's entity.level() value, wrapped (SomakeConnectionTickMixin). */
    public static Level level(LivingEntity entity, Operation<Level> original) {
        Level level = original.call(entity);
        if (!enabled || !(level instanceof ServerLevel)) return level;
        if (!unlinked(entity)) {
            ran++;
            return level;
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            boolean holds = ConnectionDataHelper.getTransferQueueData(entity).hasPendingTransfers() || ConnectionDataHelper.getLinkData(entity).hasLink()
                    || entity instanceof ServerPlayer p && ConnectionDataHelper.getPendingCastData(p).hasPendingTarget();
            if (holds && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH: {} has no connection tag but the mod's data says it is connected", KEY, entity);
            return level;
        }
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Somake Spells' connection tick returns at once for entities without connection data)", KEY);
        }
        return null;
    }
}
