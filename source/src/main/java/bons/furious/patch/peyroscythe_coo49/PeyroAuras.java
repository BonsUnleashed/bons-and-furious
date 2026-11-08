package bons.furious.patch.peyroscythe_coo49;

import bons.pure.config.PureConfig;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group peyroscythe_coo49 (Peyro's Scythes &amp; Spells 1.21.1-1.3.2 for NeoForge, GPLv3; needs Iron's
 * Spellbooks). Ours; no Peyroscythe code is carried. Three switches, all off by default:
 *
 * peyroscythe_aura_apply_interval (trade-off, both sides). Idea: Collections Of Optimizations
 * (peyroscythe.auraEffectApplyInterval), idea text only. The Crimson Moon and the Golden Bell re-add their effect (Moon
 * Frenzy 40 ticks, Holy Bell Suppression 40 ticks) to every living entity inside them on every tick: a new effect
 * instance, two NeoForge events (MobEffectEvent.Applicable and Added), a duration bump that counts as a change (on the
 * server the effect's attribute modifiers are taken off and put back, a player gets an effect packet), and on the Crimson
 * Moon a walk over the entity's persistent moon frenzy marks and a write of them. The Crimson Moon even computes a "hit
 * tick" (age % 10 == 0) and never uses it. With the switch the add runs only on the aura's beat ticks (age % N == 0, N =
 * the value, 10 by default: the mod's own unused beat); on the other ticks the wrapped call answers false without running,
 * which the Crimson Moon reads as a failed add (no marks that tick). Every effect lasts longer than N - 1 ticks for
 * N <= 19, so an entity that stays inside keeps its effect without a gap; it gets it up to N - 1 ticks later after it
 * steps in, and loses it up to N - 1 ticks earlier after it steps out (the last refresh was earlier). N = 1 is the
 * original. (This build has no Mundus spell entity.)
 *
 * peyroscythe_aura_server_only (trade-off, client). Idea: Collections Of Optimizations (peyroscythe.serverOnlyAuraScans),
 * idea text only. The same two auras search the level for the entities in their box (every max(radius / 2, 2) ticks) and
 * the Frost Fog searches the living entities in its radius every tick, on both sides, and then hand their effects to the
 * client's copies too. With the switch, on a client level the two auras do not update their tracking list (it stays the
 * empty list every aura starts with, so their loop does nothing) and the Frost Fog's search answers an empty list. The
 * server does everything as before and sends its results: the local player gets the aura effects from the server's effect
 * packets (about one network round trip later than its own copy would have), so the Holy Bell's slowdown of the player's
 * own movement, which the client computes, starts and ends one round trip later; other entities no longer get client-only
 * effect copies (the server never syncs non-player effects, and a client never removes them), and the Frost Fog no longer
 * writes freeze ticks on client copies (the server syncs those).
 *
 * peyroscythe_black_flame_wings_server_contact (gameplay fix, client). Idea: Collections Of Optimizations
 * (peyroscythe.blackFlameWingsServerOnlyContact), idea text only. Black Flame Wings' effect tick (every 10 ticks, both
 * sides) searches the entities touching the wearer and calls Iron's damage helper on each and sets its invulnerability
 * timer to 20, also on the client's copies (where the helper posts Iron's SpellDamageEvent, marks the wearer's last hurt
 * mob, and LivingEntity.hurt refuses the hit on the client before any NeoForge damage event). With the switch the search
 * answers an empty list on a client level; the server's contact damage, the particles, the Angel Wings removal, the mana
 * drain and the fall damage reset are unchanged.
 *
 * Runtime flags (-D...=false turns one off): bons_and_furious.peyroscytheAuraApplyInterval, .peyroscytheAuraServerOnly,
 * .peyroscytheBlackFlameWingsServerContact. The interval is read only while its switch is on (PureConfig value line).
 */
public final class PeyroAuras {
    public static final String INTERVAL_KEY = "peyroscythe_aura_apply_interval";
    public static final String SERVER_ONLY_KEY = "peyroscythe_aura_server_only";
    public static final String WINGS_KEY = "peyroscythe_black_flame_wings_server_contact";

    public static volatile boolean interval = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.peyroscytheAuraApplyInterval", "true"));
    public static volatile boolean serverOnly = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.peyroscytheAuraServerOnly", "true"));
    public static volatile boolean wings = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.peyroscytheBlackFlameWingsServerContact", "true"));
    /** Statistics for probes (plain fields): adds left out, client tracking updates left out, client searches answered empty. */
    public static long addsLeftOut, trackingLeftOut, searchesLeftOut;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

    private PeyroAuras() {
    }

    /** The interval, read on the switch-on path only (the first off-beat question). */
    private static final class Beat {
        static final int N = PureConfig.intValue(INTERVAL_KEY, 10, 1, 19);
    }

    private static void announce(int bit, String what) {
        synchronized (PeyroAuras.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}", what);
    }

    /** The interval mixins: true when the aura's add is left out this tick (not a beat tick of the aura's own age). */
    public static boolean offBeat(Object aura) {
        if (!interval) return false;
        int n = Beat.N;
        if (n <= 1) return false;
        boolean off = Math.floorMod(((Entity) aura).tickCount, n) != 0;
        if (off) {
            addsLeftOut++;
            if ((announced & 1) == 0) announce(0, INTERVAL_KEY + " applies (Peyroscythe's auras hand out their effects every " + n + " ticks)");
        }
        return off;
    }

    /** The server-only mixins: true when the aura stands on a client level and its tracking update is left out. */
    public static boolean clientTracking(Object aura) {
        if (!serverOnly) return false;
        Level level = ((Entity) aura).level();
        if (level == null || !level.isClientSide) return false;
        trackingLeftOut++;
        if ((announced & 2) == 0) announce(1, SERVER_ONLY_KEY + " applies (Peyroscythe's auras search and hand out effects on the server only)");
        return true;
    }

    /** The Frost Fog's search on a client level: the empty list (null = run the search). */
    @SuppressWarnings("rawtypes")
    public static List clientFogSearch(Level level) {
        if (!serverOnly || level == null || !level.isClientSide) return null;
        searchesLeftOut++;
        if ((announced & 2) == 0) announce(1, SERVER_ONLY_KEY + " applies (Peyroscythe's auras search and hand out effects on the server only)");
        return Collections.emptyList();
    }

    /** Black Flame Wings' contact search on a client level: the empty list (null = run the search). */
    @SuppressWarnings("rawtypes")
    public static List clientWingsSearch(Level level) {
        if (!wings || level == null || !level.isClientSide) return null;
        searchesLeftOut++;
        if ((announced & 4) == 0) announce(2, WINGS_KEY + " applies (Black Flame Wings' contact damage runs on the server only)");
        return Collections.emptyList();
    }
}
