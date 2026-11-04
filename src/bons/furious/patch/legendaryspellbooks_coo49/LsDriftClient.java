package bons.furious.patch.legendaryspellbooks_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.higurashi.legendary_spellbooks.common.spells.annihilation.FlamebornDriftSpell;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, the two client switches of group legendaryspellbooks_coo49 on Legendary Spellbooks 0.4.1 (LGPL-3.0;
 * needs Iron's Spellbooks). Ours; no Legendary Spellbooks or Iron's Spellbooks code is carried. Loaded only on a client (its
 * mixins are in the client list).
 *
 * FlamebornDriftSpellHandler.onPlayerTick runs at the END of every player tick the client makes (the local player and
 * every other player it ticks): it asks the client level for every LivingEntity within 64 blocks of that player
 * (ClientLevel.getEntities(null, box, instanceof LivingEntity): a walk over every entity section of a 128-block box) and,
 * for each one that is spin-attacking (isAutoSpinAttack) with Iron's synced spin type FLAMEBORN, spawns the drift
 * particles (drawing on that entity's random).
 *
 * legendary_spellbooks_drift_scan_gate (exact; idea: Collections Of Optimizations, legendaryspellbooks.spinningOnlyDriftScan,
 * idea text only; COO walks the entity list instead, in another order). Only Players and Iron's IMagicEntity mobs can have
 * a FLAMEBORN spin type: ClientMagicData.getSyncedSpellData answers a fresh default (spin RIPTIDE) for every other entity.
 * driftSearch() answers the search with a new empty list, after counting the profiler's "getEntities" line as the call
 * would, only when the loop certainly acts on nothing: vanilla_entity_class_count_layer says the level's entity sections
 * hold no IMagicEntity (asked first: then the gate stands aside at once); every Player in them is an AbstractClientPlayer
 * (the layer's Player count equals its AbstractClientPlayer count), and ClientLevel's tracking callbacks keep every
 * AbstractClientPlayer of the sections in level.players(); and no player of level.players() whose bounding box meets the
 * search box is spin-attacking (the section walk lists an entity only when its bounding box meets the box,
 * EntitySection.getEntities, guarded). The player that kept the last search is tried first (weakly held), so a standing
 * spin costs one test per sweep. Part entities are never LivingEntity (Forge's PartEntity extends Entity), so the search's
 * part pass can add none. Otherwise the original search runs unchanged. Flag:
 * -Dbons_and_furious.legendarySpellbooksDriftScanGate=false; SHADOW MODE (rigs):
 * -Dbons_and_furious.legendarySpellbooksDriftScanGate.shadow=true runs the search where the gate would answer and checks
 * that no listed entity would have drifted (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 *
 * legendary_spellbooks_drift_local_player_only (kind change, a gameplay fix, off by default; idea: Collections Of
 * Optimizations, legendaryspellbooks.localPlayerDriftScan, idea text only). localOnly(): the handler's player becomes null
 * for every player but Minecraft's local player, so its own null test ends it: one sweep per client tick around the local
 * player instead of one per ticked player, and a drifting caster's particles are spawned once per tick instead of once per
 * nearby player (vanilla drops particles farther than 32 blocks from the camera, inside the 64-block sweep). Flag:
 * -Dbons_and_furious.legendarySpellbooksDriftLocalPlayerOnly=false.
 */
public final class LsDriftClient {
    public static final String SCAN_KEY = "legendary_spellbooks_drift_scan_gate";
    public static final String LOCAL_KEY = "legendary_spellbooks_drift_local_player_only";
    static final String IMAGIC = "io.redspace.ironsspellbooks.api.entity.IMagicEntity";
    static final String PLAYER = "net.minecraft.world.entity.player.Player";
    static final String CLIENT_PLAYER = "net.minecraft.client.player.AbstractClientPlayer";

    public static volatile boolean scanEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendarySpellbooksDriftScanGate", "true"));
    public static volatile boolean localEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendarySpellbooksDriftLocalPlayerOnly", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.legendarySpellbooksDriftScanGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** For probes and harnesses (plain fields): searches answered empty, player ticks left out. */
    public static long answered, leftOut;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

    private LsDriftClient() {
    }

    /** The layer's indices, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int MAGIC = EntityPresenceLink.trackedIndex(IMAGIC);
        static final int PLAYERS = EntityPresenceLink.trackedIndex(PLAYER);
        static final int CLIENT_PLAYERS = EntityPresenceLink.trackedIndex(CLIENT_PLAYER);
    }

    private static void announce(int bit, String what) {
        synchronized (LsDriftClient.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}", what);
    }

    private static final WeakReference<AbstractClientPlayer> NONE = new WeakReference<>(null);
    /** The player that kept the last search (client thread only; weak: never keeps a level alive). */
    private static WeakReference<AbstractClientPlayer> lastSpinner = NONE;

    /** A player the drift loop could act on: inside the search box and spin-attacking. */
    static boolean canDrift(AbstractClientPlayer p, AABB box) {
        return p.m_20191_().m_82381_(box) && p.m_21209_();
    }

    /** True when the drift loop certainly acts on nothing this tick (see the class comment). */
    static boolean nothingDrifts(ClientLevel level, AABB box) {
        int magic = Index.MAGIC, players = Index.PLAYERS, clientPlayers = Index.CLIENT_PLAYERS;
        if (magic < 0 || players < 0 || clientPlayers < 0) return false;
        if (!EntityPresenceLink.none(level, magic)) return false;
        int n = EntityPresenceLink.count(level, players);
        if (n < 0 || n != EntityPresenceLink.count(level, clientPlayers)) return false;
        AbstractClientPlayer last = lastSpinner.get();
        if (last != null && last.m_9236_() == level && !last.m_213877_() && canDrift(last, box)) return false;
        for (AbstractClientPlayer p : level.m_6907_()) {
            if (p != last && canDrift(p, box)) {
                lastSpinner = new WeakReference<>(p);
                return false;
            }
        }
        if (last != null) lastSpinner = NONE;
        return true;
    }

    /** LsDriftScanMixin: the handler's LivingEntity search, answered empty while nothing can drift. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List driftSearch(ClientLevel level, Entity except, AABB box, Predicate predicate, Operation<List> original) {
        if (!scanEnabled || level == null || box == null || !nothingDrifts(level, box)) return original.call(level, except, box, predicate);
        if (SHADOW) {
            announce(0, SCAN_KEY + ": SHADOW MODE: the drift searches run in full and are checked");
            List found = original.call(level, except, box, predicate);
            SHADOW_CHECKS.incrementAndGet();
            for (Object o : found) {
                if (o instanceof LivingEntity e && e.m_21209_() && ClientMagicData.getSyncedSpellData(e).getSpinAttackType().equals(FlamebornDriftSpell.FLAMEBORN)) {
                    long k = SHADOW_MISMATCHES.incrementAndGet();
                    if (k <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the gate would answer empty, but {} drifts", SCAN_KEY, k, e);
                    break;
                }
            }
            return found;
        }
        level.m_46473_().m_6174_("getEntities");
        answered++;
        if ((announced & 2) == 0) announce(1, SCAN_KEY + " applies (the Flameborn Drift sweep walks no entity sections while nobody is spin-attacking)");
        return new ArrayList();
    }

    /** LsDriftLocalOnlyMixin: the handler's player, or null for every player but the local one. */
    public static Player localOnly(Player player) {
        if (!localEnabled || player == null) return player;
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || player == mc.f_91074_) return player;
        leftOut++;
        if ((announced & 4) == 0) announce(2, LOCAL_KEY + " applies (the Flameborn Drift sweep runs for your own player only)");
        return null;
    }
}
