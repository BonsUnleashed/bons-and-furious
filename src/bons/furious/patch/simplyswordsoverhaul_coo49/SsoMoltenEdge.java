package bons.furious.patch.simplyswordsoverhaul_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Bons and Furious switch simplyswordsoverhaul_molten_edge_buff_refresh (Simply Swords Overhaul 1.0.4 with Simply Swords
 * 1.56.0; MIT; server side; since 1.0.49). Trade-off, off by default. Ours; no Simply Swords Overhaul code is carried.
 * Idea: Collections Of Optimizations (simplyswordsoverhaul.leanMoltenEdgeBuffs), idea text only.
 *
 * Simply Swords Overhaul's Molten Edge handler gives a player holding the sword, below 60% health (80% with one in each
 * hand), strength and haste at a level that grows with the missing health and speed one level lower, each as a new
 * 100-tick, non-ambient, hidden effect, on every tick (and once more per tick for the second sword). Each of those calls
 * that only renews the timer replaces the effect in full: Forge's effect events, the attribute modifiers taken off and put
 * back, an effect packet, and the attributes marked for an update packet.
 *
 * addEffect leaves a call out while the player already has that effect at the same level and with the same flags and
 * more than RENEW_AT ticks left; a new effect, a higher or lower level, other flags, or a timer at RENEW_AT or below goes
 * through as shipped (a lower level still lands as the effect's hidden follow-up, as vanilla keeps it). The difference:
 * the timers of the sword's buffs run between RENEW_AT and 100 ticks instead of staying at 100, so when the sword is put
 * away, or the health recovers to a lower level, the higher buff mostly ends up to 100 - RENEW_AT ticks (1 second) sooner.
 * A higher level that ends sooner also brings its hidden lower level forward, and a later rise can then replace that
 * lower level where the original kept it under the higher one: after several level changes within a few seconds a buff
 * can end up to its full 100 ticks sooner, and now and then a lower level that vanilla drops (when an expired middle
 * level surfaces in the effect's hidden chain, the levels below it go with it) stays on until it runs out. Held at one
 * level the buffs are the same. Runtime flag -Dbons_and_furious.simplyswordsoverhaulMoltenEdgeBuffRefresh=false.
 */
public final class SsoMoltenEdge {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsoverhaulMoltenEdgeBuffRefresh", "true"));
    /** A buff the sword already gave is renewed once it has this many ticks left, or fewer. */
    public static final int RENEW_AT = 80;
    /** Calls left out (statistics; plain, racy). */
    public static long skipped;

    private SsoMoltenEdge() {
    }

    /** In place of the handler's Player.addEffect(effect). */
    public static boolean addEffect(Player player, MobEffectInstance wanted, Operation<Boolean> original) {
        if (enabled && wanted != null) {
            MobEffectInstance had = player.m_21124_(wanted.m_19544_());
            if (had != null && had.m_19564_() == wanted.m_19564_() && had.m_19557_() > RENEW_AT && had.m_19571_() == wanted.m_19571_()
                    && had.m_19572_() == wanted.m_19572_() && had.m_19575_() == wanted.m_19575_()) {
                skipped++;
                return false;
            }
        }
        return original.call(player, wanted);
    }
}
