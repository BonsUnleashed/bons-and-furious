package bons.furious.patch.netherexp_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group netherexp_coo49 (Jaden's Nether Expansion 2.3.5). Its licence (CC-BY-NC-SA-4.0) is not ours: no
 * code of it is carried; the wrappers decide around its own calls.
 *
 * netherexp_compass_tooltip_no_tag_write (fix, on): SanctumCompassItem.appendHoverText reads two flags through
 *   stack.getOrCreateTag(), which attaches an empty tag to a compass that has none. On the client that happens while the
 *   player only hovers the compass (a chest, the creative tab): the client copy now differs from the server's, so the next
 *   click in that container makes the server resend the slot, and the creative inventory sends the tagged copy to the
 *   server. The read now uses the existing tag or a fresh detached one: the same lines, nothing written to the stack.
 * netherexp_candle_effect_refresh_half (trade-off, off): a lit treacherous candle gives every player within 16 blocks
 *   Betrayed (200 ticks, level I) again on every tick; each re-give updates the effect and sends an effect packet to that
 *   player, 20 per second per player during a candle fight. With the switch on, the re-give is skipped while the player
 *   already has Betrayed at level I (not ambient) with at least 100 ticks left: about one update per 5 seconds; the
 *   effect never runs out while the player stays in range, and leaving the range still removes it at once.
 */
public final class NetherExpFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean compassTag = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.netherexpCompassTooltipNoTagWrite", "true"));
    public static volatile boolean candleRefresh = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.netherexpCandleEffectRefreshHalf", "true"));
    private static volatile int announced;

    private NetherExpFixes() {
    }

    /** The tag appendHoverText reads: the stack's own, or a detached empty one (never attached). */
    public static CompoundTag readOnlyTag(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        if (tag != null) return tag;
        announce(1, "netherexp_compass_tooltip_no_tag_write", "hovering an untagged sanctum compass no longer writes a tag to it");
        return new CompoundTag();
    }

    /** True when the player's Betrayed is the candle's own kind (level I, not ambient) with 100 or more ticks left. */
    public static boolean stillFresh(LivingEntity player, MobEffectInstance given) {
        if (given.m_19564_() != 0 || given.m_19571_()) return false;
        MobEffect effect = given.m_19544_();
        MobEffectInstance current = player.m_21124_(effect);
        if (current == null || current.m_19564_() != 0 || current.m_19571_() || current.m_19557_() < 100) return false;
        announce(2, "netherexp_candle_effect_refresh_half", "treacherous candles refresh Betrayed every 5 seconds instead of every tick");
        return true;
    }

    private static void announce(int bit, String key, String what) {
        if ((announced & bit) != 0) return;
        synchronized (NetherExpFixes.class) {
            if ((announced & bit) != 0) return;
            announced |= bit;
        }
        LOGGER.info("Bons and Furious: {}: {}", key, what);
    }
}
