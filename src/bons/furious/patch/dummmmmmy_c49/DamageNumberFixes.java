package bons.furious.patch.dummmmmmy_c49;

import java.util.Optional;
import net.mehvahdjukaar.dummmmmmy.configs.CommonConfigs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fixes for MmmMmmMmmMmm 1.20-2.0.12 (Supplementaries Team License v1.5; server side incl. the integrated
 * server). Ours; no MmmMmmMmmMmm code is carried (injectors with our own decisions).
 *
 * dummmmmmy_damage_number_send_first. ModEvents.onEntityDamage builds a ClientBoundDamageNumberMessage for every hurt of
 * every living entity (encoding the damage type) BEFORE looking at the configured mode, whose default is NONE: the message
 * is then dropped. For a damage source whose type is an inline (Holder.direct, unregistered) DamageType building it throws
 * an AssertionError inside LivingEntity.hurt even with damage numbers off; the ALL_PLAYERS / LOCAL_PLAYER modes also call
 * getEntity() on the source without a null check. Fix: the recipient is decided first, exactly as the switch decides it
 * (ALL_ENTITIES; ALL_PLAYERS and LOCAL_PLAYER only for a ServerPlayer attacker; nothing for NONE), null-safe for the source;
 * when nothing would be sent the method returns before the message is built. When something is sent the mod's own code
 * runs unchanged (same packets). onEntityHeal is not touched: its message carries no damage source, never throws, and its
 * cost with healing numbers off is already nil.
 *
 * dummmmmmy_inline_damage_type_fallback. ClientBoundDamageNumberMessage.encodeDamage throws an AssertionError when the
 * source's type holder has no registry key (an inline DamageType). Fix: it answers the mod's own TRUE_DAMAGE id (the one it
 * already uses for a null source; the client colour lookup knows it). Registered types are encoded exactly as before.
 *
 * -Dbons_and_furious.dummmmmmyDamageNumberSendFirst=false / -Dbons_and_furious.dummmmmmyInlineDamageTypeFallback=false
 * switch them off at run time.
 */
public final class DamageNumberFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean sendFirst = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dummmmmmyDamageNumberSendFirst", "true"));
    public static volatile boolean inlineFallback = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dummmmmmyInlineDamageTypeFallback", "true"));
    private static volatile boolean announcedSend, announcedInline;

    private DamageNumberFixes() {}

    /** Whether onEntityDamage's switch would send the message (the mode read as the switch reads it; source null-safe). */
    public static boolean damageSent(DamageSource source) {
        CommonConfigs.Mode mode = CommonConfigs.DAMAGE_NUMBERS_MODE.get();
        if (mode == CommonConfigs.Mode.ALL_ENTITIES) return true;
        if (mode == CommonConfigs.Mode.ALL_PLAYERS || mode == CommonConfigs.Mode.LOCAL_PLAYER)
            return source != null && source.m_7639_() instanceof ServerPlayer;
        announceSend();
        return false;
    }

    /** True when encodeDamage would throw "Damage type not found in registry" for this source. */
    public static boolean inlineType(DamageSource source) {
        if (source == null || source.m_269415_() == null) return false;
        Optional<?> key = source.m_269150_().m_203543_();
        if (!key.isEmpty()) return false;
        if (!announcedInline) {
            announcedInline = true;
            LOGGER.info("Bons and Furious: dummmmmmy_inline_damage_type_fallback applies (a damage number for an unregistered damage type uses the true-damage colour instead of throwing)");
        }
        return true;
    }

    private static void announceSend() {
        if (!announcedSend) {
            announcedSend = true;
            LOGGER.info("Bons and Furious: dummmmmmy_damage_number_send_first applies (MmmMmmMmmMmm builds a damage number message only when it is sent)");
        }
    }
}
