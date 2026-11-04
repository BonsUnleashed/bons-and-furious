package bons.furious.patch.betterspellcasting_coo49;

import bons.furious.mixin.betterspellcasting_coo49.BscComboStateAccessor;
import bons.pure.config.PureConfig;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group betterspellcasting_coo49 (Better Spellcasting 1.2.0, LGPL-2.1-or-later; needs Iron's Spellbooks
 * and Better Combat). Ours; no Better Spellcasting code is carried. Two gameplay fixes, both off by default:
 *
 * better_spellcasting_queued_spell_expiry (server). Idea: Collections Of Optimizations (betterspellcasting.expireQueuedSpell,
 * betterspellcasting.queuedSpellWindow), idea text only. AttackSpellHandler arms a weapon's on-hit spell at the swing
 * (trackSwingServerSide puts the player's combo state, then the pending spell, both keyed by the player's UUID in static
 * maps) and fires it on the next LivingHurtEvent whose source entity is that player, whatever the damage: an arrow, a
 * fire, another weapon, minutes or a relog later (nothing ever clears the maps). With the switch the pending entry is
 * still taken out as shipped, but it fires only when the swing that armed it (its combo state's lastSwingTick; every path
 * that writes the combo state also replaces or removes the pending spell) is at most the window's ticks old by the
 * attacker's game time (one clock for every level of a server); an older entry, a swing time in the future (another
 * world's clock) or a missing state drops it without casting. Value: the window in ticks, default 40 (the mod's own
 * combo timeout and COO's default).
 *
 * better_spellcasting_server_swing_only (client). Idea: Collections Of Optimizations
 * (betterspellcasting.skipRedundantSwingPacket), idea text only. On every client attack on an entity the handler sends a
 * SwingMessage, and the server answers it with trackSwingServerSide(player, no target), next to the swing the server
 * already tracks from its own attack event with the real target. The server drops the second one only when both arrive
 * on the same tick with the same weapon; otherwise the combo advances twice for one hit, and a hit the server refused
 * (reach, a cancelled attack event) still advances the combo and casts the on-attack spell. With the switch the client
 * sends no SwingMessage for entity hits (the server's own attack event tracks them, as before); swings at the air keep
 * sending theirs, the only way the server hears of them.
 *
 * Runtime flags (-D...=false turns one off): bons_and_furious.betterSpellcastingQueuedSpellExpiry,
 * .betterSpellcastingServerSwingOnly. The window is read only while its switch is on (PureConfig value line).
 */
public final class BscSpells {
    public static final String EXPIRY_KEY = "better_spellcasting_queued_spell_expiry";
    public static final String SWING_KEY = "better_spellcasting_server_swing_only";

    public static volatile boolean expiry = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.betterSpellcastingQueuedSpellExpiry", "true"));
    public static volatile boolean swing = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.betterSpellcastingServerSwingOnly", "true"));
    /** Statistics for probes (plain fields): queued spells dropped as expired; hit swing packets left out. */
    public static long expired, hitSwingsLeftOut;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

    private BscSpells() {
    }

    /** The window, read on the switch-on path only (the first queued spell). */
    private static final class Window {
        static final int TICKS = PureConfig.intValue(EXPIRY_KEY, 40, 1, 1200);
    }

    private static void announce(int bit, String what) {
        synchronized (BscSpells.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}", what);
    }

    /**
     * BscQueuedSpellExpiryMixin: the pending spell the handler goes on with (null = none, the handler returns). The entry
     * is already removed (as shipped) when this runs.
     */
    @SuppressWarnings("rawtypes")
    public static Object queued(Object removed, Object key, LivingHurtEvent event, Map serverCombo) {
        if (removed == null || !expiry) return removed;
        Object state = serverCombo == null ? null : serverCombo.get(key);
        Entity attacker = event == null || event.getSource() == null ? null : event.getSource().m_7639_();
        Level level = attacker == null ? null : attacker.m_9236_();
        if (level == null) return removed;   // cannot happen (the handler read the attacker's uuid); keep the shipped answer
        if (state != null) {
            long age = level.m_46467_() - ((BscComboStateAccessor) state).bons$betterspellcastingCoo49LastSwingTick();
            if (age >= 0 && age <= Window.TICKS) return removed;
        }
        expired++;
        if ((announced & 1) == 0) announce(0, EXPIRY_KEY + " applies (Better Spellcasting drops an armed on-hit spell whose swing is older than " + Window.TICKS + " ticks)");
        return null;
    }

    /** BscSwingPacketMixin: the client-side test of onClientAttackEntity (false = return before the SwingMessage). */
    public static boolean sendHitSwing(boolean clientSide) {
        if (!swing || !clientSide) return clientSide;
        hitSwingsLeftOut++;
        if ((announced & 2) == 0) announce(1, SWING_KEY + " applies (Better Spellcasting's client no longer sends a swing packet for entity hits)");
        return false;
    }
}
