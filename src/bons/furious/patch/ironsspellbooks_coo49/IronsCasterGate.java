package bons.furious.patch.ironsspellbooks_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch irons_spellbooks_caster_scan_gate (Iron's Spells 'n Spellbooks 1.20.1-3.16.3, 3.15.6 the same;
 * All Rights Reserved: no Iron's code is carried; client; exact). Ours. Idea: Collections Of Optimizations
 * (vanilla.ironsspellbooksFlatSpellcasterScan), idea text only; COO walks the client's entity list instead (another order,
 * so Iron's shared random is drawn in another order), this one keeps the original search whenever anything could act.
 * Loaded only on a client (its mixin is in the client list).
 *
 * ClientPlayerEvents.onPlayerTick, at the END of the local player's tick, asks the client level for every entity within
 * 64 blocks that is a Player or an IMagicEntity (ClientLevel.getEntities(null, box, predicate): a walk over every entity
 * section of a 128-block box, and Forge's pass over the level's part entities) and loops over them: for an entity that is
 * spin-attacking with Iron's FIRE spin it spawns the burning-dash particles; for one that is casting Ray of Siphoning,
 * Recall or Raise Hell it traces / spawns that spell's particles (drawing on Iron's shared random). Every value the loop
 * tests comes from ClientMagicData.getSyncedSpellData: the synced map for Players, the magic data for IMagicEntity mobs,
 * and for every other entity a fresh default (not casting, spin RIPTIDE). So only Players and IMagicEntity mobs can make it
 * act, and a Player only while it is spin-attacking or casting.
 *
 * search() answers the search with a new empty list, after counting the profiler's "getEntities" line as the call would,
 * only when the loop certainly acts on nothing: vanilla_entity_class_count_layer says the level's entity sections hold no
 * IMagicEntity (asked first: then the gate stands aside at once); every Player in them is an AbstractClientPlayer (the
 * layer's Player count equals its AbstractClientPlayer count), and ClientLevel's tracking callbacks keep every
 * AbstractClientPlayer of the sections in level.players(); no player of level.players() whose bounding box meets the
 * search box is spin-attacking or casting (a superset of the cases the loop acts on: the section walk lists an entity only
 * when its bounding box meets the box, EntitySection.getEntities, guarded); and no part entity of the level is an
 * IMagicEntity (parts are never Players). The player that kept last tick's search is tried first (weakly held), so a
 * standing cast costs one test per tick. Otherwise the original search runs unchanged. Flag:
 * -Dbons_and_furious.ironsSpellbooksCasterScanGate=false; SHADOW MODE (rigs):
 * -Dbons_and_furious.ironsSpellbooksCasterScanGate.shadow=true runs the search where the gate would answer and checks that
 * no listed entity is spin-attacking or casting (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class IronsCasterGate {
    public static final String KEY = "irons_spellbooks_caster_scan_gate";
    static final String IMAGIC = "io.redspace.ironsspellbooks.api.entity.IMagicEntity";
    static final String PLAYER = "net.minecraft.world.entity.player.Player";
    static final String CLIENT_PLAYER = "net.minecraft.client.player.AbstractClientPlayer";

    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ironsSpellbooksCasterScanGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.ironsSpellbooksCasterScanGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** For probes and harnesses (plain field): searches answered empty. */
    public static long answered;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

    private IronsCasterGate() {
    }

    /** The layer's indices, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int MAGIC = EntityPresenceLink.trackedIndex(IMAGIC);
        static final int PLAYERS = EntityPresenceLink.trackedIndex(PLAYER);
        static final int CLIENT_PLAYERS = EntityPresenceLink.trackedIndex(CLIENT_PLAYER);
    }

    private static void announce(int bit, String what) {
        synchronized (IronsCasterGate.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}: {}", KEY, what);
    }

    /** A player whose synced state could make the loop act: spin-attacking, or casting anything. */
    static boolean active(LivingEntity e) {
        if (e.m_21209_()) return true;
        SyncedSpellData data = ClientMagicData.getSyncedSpellData(e);
        return data != null && data.isCasting();
    }

    private static final WeakReference<AbstractClientPlayer> NONE = new WeakReference<>(null);
    /** The player that kept the last search (client thread only; weak: never keeps a level alive). */
    private static WeakReference<AbstractClientPlayer> lastActive = NONE;

    /** A player the caster loop could act on: inside the search box, spin-attacking or casting. */
    static boolean canAct(AbstractClientPlayer p, AABB box) {
        return p.m_20191_().m_82381_(box) && active(p);
    }

    /** True when the caster loop certainly acts on nothing this tick (see the class comment). */
    static boolean nobodyActs(ClientLevel level, AABB box) {
        int magic = Index.MAGIC, players = Index.PLAYERS, clientPlayers = Index.CLIENT_PLAYERS;
        if (magic < 0 || players < 0 || clientPlayers < 0) return false;
        if (!EntityPresenceLink.none(level, magic)) return false;
        int n = EntityPresenceLink.count(level, players);
        if (n < 0 || n != EntityPresenceLink.count(level, clientPlayers)) return false;
        AbstractClientPlayer last = lastActive.get();
        if (last != null && last.m_9236_() == level && !last.m_213877_() && canAct(last, box)) return false;
        for (AbstractClientPlayer p : level.m_6907_()) {
            if (p != last && canAct(p, box)) {
                lastActive = new WeakReference<>(p);
                return false;
            }
        }
        if (last != null) lastActive = NONE;
        for (PartEntity<?> part : level.getPartEntities()) {
            if (part instanceof IMagicEntity) return false;
        }
        return true;
    }

    /** IronsCasterScanMixin: the caster loop's search, answered empty while nothing can act. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List search(ClientLevel level, Entity except, AABB box, Predicate predicate, Operation<List> original) {
        if (!enabled || level == null || box == null || !nobodyActs(level, box)) return original.call(level, except, box, predicate);
        if (SHADOW) {
            announce(0, "SHADOW MODE: the caster searches run in full and are checked");
            List found = original.call(level, except, box, predicate);
            SHADOW_CHECKS.incrementAndGet();
            for (Object o : found) {
                if (o instanceof IMagicEntity || (o instanceof Player p && active(p))) {
                    long k = SHADOW_MISMATCHES.incrementAndGet();
                    if (k <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the gate would answer empty, but the search lists {}", KEY, k, o);
                    break;
                }
            }
            return found;
        }
        level.m_46473_().m_6174_("getEntities");
        answered++;
        if ((announced & 2) == 0) announce(1, "the spell-caster particle search walks no entity sections while nobody nearby casts or spin-attacks");
        return new ArrayList();
    }
}
