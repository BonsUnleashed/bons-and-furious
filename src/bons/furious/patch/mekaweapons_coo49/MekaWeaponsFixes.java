package bons.furious.patch.mekaweapons_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group mekaweapons_coo49 (Mekanism Weapons 4.2.1; the bow switch also 3.0). Runtime flags and the one
 * INFO line per switch. Mekanism Weapons is MIT; the wrappers carry only our own decisions and call its own methods.
 *
 * mekaweapons_gun_sound_client_only (fix, on): ItemMekaGun.use calls mekanism.client.sound.SoundHandler.playSound(LASER)
 *   on BOTH sides. SoundHandler is a client class (Forge strips @OnlyIn(CLIENT) classes from a dedicated server): the
 *   first shot on a dedicated server throws NoClassDefFoundError, an Error that BlockableEventLoop does not catch -> the
 *   server crashes; in singleplayer the integrated server's call plays the sound a second time. The call now runs only
 *   when the level is client-side (the shooter's own client): one sound per shot, no server crash.
 * mekaweapons_gun_laser_server_only (fix, on): ItemMekaGun.use runs fireLaser on the client too: a LivingEntity box query,
 *   setSecondsOnFire on the client copy of the hit mob (fire flicker until the server's state arrives) and a client-side
 *   hurt (posts LivingAttackEvent on the client, then returns false). Its result only feeds sendLaserDataToPlayers, which
 *   acts on a ServerLevel only. On the client the call now returns `to` (what fireLaser returns when nothing is hit) without
 *   running; the server's laser, damage, fire, particles, energy and heat are unchanged.
 * mekaweapons_bow_offhand_arrow (gameplay fix, off): ItemMekaBow.customArrow takes the arrow's module stats from
 *   player.getMainHandItem(); with the Meka-Bow in the off hand that is whatever the main hand holds. When the player's use
 *   item (still the bow while releaseUsing runs) is a Meka-Bow, that stack is used instead.
 * (mekaweapons_tana_sweep_full_swing lives in group mekaweapons_tana_coo49: its tested build is 4.2.1.)
 */
public final class MekaWeaponsFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean gunSound = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mekaweaponsGunSoundClientOnly", "true"));
    public static volatile boolean gunLaser = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mekaweaponsGunLaserServerOnly", "true"));
    public static volatile boolean bowOffhand = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mekaweaponsBowOffhandArrow", "true"));
    private static volatile int announced;

    private MekaWeaponsFixes() {
    }

    public static void announce(int bit, String key, String what) {
        if ((announced & bit) != 0) return;
        synchronized (MekaWeaponsFixes.class) {
            if ((announced & bit) != 0) return;
            announced |= bit;
        }
        LOGGER.info("Bons and Furious: {}: {}", key, what);
    }
}
