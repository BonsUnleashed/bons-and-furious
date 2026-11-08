package bons.furious.patch.dummmmmmy_c49;

import net.mehvahdjukaar.dummmmmmy.configs.CommonConfigs;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches for MmmMmmMmmMmm (Supplementaries Team License v1.5; 1.21.1 tested build: MmmMmmMmmMmm
 * 1.21-2.1.2 for NeoForge; server side incl. the integrated server). Ours; no MmmMmmMmmMmm code is carried (injectors with
 * our own decisions).
 *
 * dummmmmmy_damage_number_send_first. ModEvents.onEntityDamage (called from LivingEntity.actuallyHurt for every hurt of
 * every living entity) builds a ClientBoundDamageNumberMessage (a record holding the damage type holder) BEFORE looking at
 * the configured mode, whose default is NONE: the message is then dropped. The recipient is decided first, exactly as the
 * switch decides it (ALL_ENTITIES; ALL_PLAYERS and LOCAL_PLAYER only for a ServerPlayer attacker; nothing for NONE),
 * null-safe for the source; when nothing would be sent the method returns before the message is built. When something is
 * sent the mod's own code runs unchanged (same packets). With Critical Strike installed (Dummmmmmy.CRITICAL_STRIKE) the mod
 * also reads the source's crit multiplier before the message; the switch then stands aside. onEntityHeal is not touched: its
 * message carries no damage source and its cost with healing numbers off is already nil.
 *
 * dummmmmmy_inline_damage_type_fallback. ClientBoundDamageNumberMessage.encodeDamage keeps the source's damage type holder,
 * and the message writes it with DamageType.STREAM_CODEC (ByteBufCodecs.holderRegistry): the id of the holder's value in the
 * connection's damage type registry, IllegalArgumentException ("Can't find id") when it has none. A damage type another mod
 * creates on the fly (Holder.direct, not registered) therefore fails when the number is written for a player on another
 * connection (a dedicated server, LAN guests): PacketEncoder rethrows and that player is disconnected ("Internal
 * Exception"). Fix: a source whose damage type has no id in the level's damage type registry (the registry the server's
 * connections write with) is encoded as the mod's own TRUE_DAMAGE type (the one it already uses for a null source; the client
 * colour lookup knows it). Every type the registry has an id for is encoded exactly as before.
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
            return source != null && source.getEntity() instanceof ServerPlayer;
        announceSend();
        return false;
    }

    /** True when the source's damage type cannot be written by DamageType.STREAM_CODEC (no id in the level's registry). */
    public static boolean unencodable(DamageSource source, Level level) {
        if (source == null || level == null) return false;
        Holder<DamageType> holder = source.typeHolder();
        if (holder == null || level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getId(holder.value()) != -1) return false;
        if (!announcedInline) {
            announcedInline = true;
            LOGGER.info("Bons and Furious: dummmmmmy_inline_damage_type_fallback applies (a damage number for an unregistered damage type uses the true-damage type instead of failing to be sent)");
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
