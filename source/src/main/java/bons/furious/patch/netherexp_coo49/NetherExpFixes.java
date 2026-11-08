package bons.furious.patch.netherexp_coo49;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group netherexp_coo49 (Jaden's Nether Expansion; 1.21.1 tested build: Jaden's Nether Expansion 2.4.1). Its
 * licence (CC BY-NC-ND 4.0) is not ours: no code of it is carried; the wrapper decides around its own call.
 *
 * netherexp_candle_effect_refresh_half (trade-off, off): a lit Cierge of Treachery (2.3.5's treacherous candle) gives every
 *   player within 16 blocks Betrayed (200 ticks, level I) again on every tick; each re-give updates the effect and sends an
 *   effect packet to that player, 20 per second per player during the fight. With the switch on, the re-give is skipped while
 *   the player already has Betrayed at level I (not ambient, visible, with its icon: what the cierge's own effect is) with at
 *   least 100 ticks left: about one update per 5 seconds; the effect never runs out while the player stays in range, and
 *   leaving the range still removes it at once. Runtime flag bons_and_furious.netherexpCandleEffectRefreshHalf.
 */
public final class NetherExpFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean candleRefresh = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.netherexpCandleEffectRefreshHalf", "true"));
    private static volatile boolean announced;

    private NetherExpFixes() {
    }

    /** True when the player's Betrayed is the cierge's own kind (level I, not ambient, shown) with 100 or more ticks left. */
    public static boolean stillFresh(LivingEntity player, MobEffectInstance given) {
        if (given.getAmplifier() != 0 || given.isAmbient() || !given.isVisible() || !given.showIcon()) return false;
        Holder<MobEffect> effect = given.getEffect();
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || current.getAmplifier() != 0 || current.isAmbient() || !current.isVisible() || !current.showIcon()
                || current.getDuration() < 100) return false;
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: netherexp_candle_effect_refresh_half: the Cierge of Treachery refreshes Betrayed every 5 seconds instead of every tick");
    }
}
