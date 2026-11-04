package bons.furious.patch.cnc_coo49;

import bons.pure.config.PureConfig;
import net.imasillylittleguy.cnc.init.CncModMobEffects;
import net.imasillylittleguy.cnc.network.CncModVariables;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group cnc_coo49 (Critters n' Crawlers 2.2.5, All Rights Reserved: no code of it is carried, the hooks
 * only decide whether its own code runs; since 1.0.49): the helper of five switches.
 *
 * <ul>
 * <li>cnc_encounter_flag_first (exact variant, on; both sides). Critters n' Crawlers registers 26 PlayerTickEvent handlers
 * (the *AdvanceprocProcedure classes), one per animal of its field guide, that run for every player on every tick on
 * both sides: each searches a 4 block box around the player for its animal, then (16 of them) builds that animal's head
 * item and walks the player's whole inventory for it, or (4 of them) looks an advancement up, and only then reads the
 * player's PlayerVariables flag encountered_&lt;animal&gt; and stops when it is already set. Once an animal has been met that
 * work can only end in nothing. The hooks read the flag first (the capability resolved without making a default object)
 * and return at once when it is set; otherwise the handler runs as shipped. The skipped search, item and inventory walk,
 * and advancement look-up have no effect (the advancement handlers run the original whenever its advancement is not
 * loaded, because the original's look-up then throws). Exact variant: the one internal difference is that Forge no longer
 * gathers capabilities (AttachCapabilitiesEvent) for the throwaway head item the original builds every tick, which it did
 * when the player carried that animal's head. PlayerSquonkCryEffectProcedure (the squonk's sobbing touch) tests "living
 * entity on a server level without Sobbing" before its search instead of after it (a pure reorder, exact).</li>
 * <li>cnc_encounter_client_lean (exact variant, on; client). On a client level the 26 handlers run only for the local
 * player: for the other players the client ticks they could only set flags in those players' client copies of
 * PlayerVariables, which nothing reads or sends (the field guide reads the local player's, and the sync and toast packets
 * are only sent for server players). The squonk handler does nothing on a client level and is left out there.</li>
 * <li>cnc_sasquatch_knock_client_skip (exact variant, on; client). SasquatchWoodKnockProcedure (a LivingTickEvent handler)
 * reads 216 block states around every sasquatch on both sides to decide on a knock sound; on a client level the sound is
 * played with no player, which a client level never plays (Forge's ClientLevel plays only for player == the local player),
 * so the handler is left out there. Exact variant: the client no longer makes a new RandomSource (advancing the seed
 * uniquifier) and, on a 1 in 151 roll, no longer draws a seed from the level's random for the silent sound nor raises
 * Forge's PlayLevelSoundEvent.AtPosition for it.</li>
 * <li>cnc_sasquatch_texture_scan_interval (trade-off, off; both sides). SasquatchOnEntityTickUpdateProcedure searches a
 * 16 block box for players (and server players) on every sasquatch tick to choose its visible or hidden texture; with the
 * switch the choice is made every N ticks (phase from the entity id), the synced texture stays in between. Value
 * cnc_sasquatch_texture_scan_interval.value (default 4).</li>
 * <li>cnc_sobbing_shader_own_only (gameplay fix, off; client). SobbingShaderProcedure reacts to every player the client
 * ticks and shuts down whatever post effect is running when that player has no Sobbing (spectator creeper / spider /
 * enderman vision, other mods' effects). With the switch it acts only for the local player and only shuts down its own
 * cry effect (CncSobbingClient); loading is unchanged.</li>
 * </ul>
 * Runtime flags (emergency off): -Dbons_and_furious.cncEncounterFlagFirst=false, cncEncounterClientLean,
 * cncSasquatchKnockClientSkip, cncSasquatchTextureScanInterval, cncSobbingShaderOwnOnly.
 */
public final class CncScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    public static volatile boolean flagFirst = flag("cncEncounterFlagFirst");
    public static volatile boolean clientLean = flag("cncEncounterClientLean");
    public static volatile boolean knock = flag("cncSasquatchKnockClientSkip");
    public static volatile boolean texture = flag("cncSasquatchTextureScanInterval");
    public static volatile boolean sobbing = flag("cncSobbingShaderOwnOnly");

    /** The advancements the four advancement handlers look up (the same ids as the handlers' own constants). */
    public static final ResourceLocation GIANTBOAR_ADVANCEMENT = new ResourceLocation("cnc:giant_boar_killed");
    public static final ResourceLocation SKINWALKER_ADVANCEMENT = new ResourceLocation("cnc:skinwalkeradvancement");
    public static final ResourceLocation WECHUGE_ADVANCEMENT = new ResourceLocation("cnc:wechuge_killed");
    public static final ResourceLocation WENDIGO_ADVANCEMENT = new ResourceLocation("cnc:wendigo_killed");

    private static volatile boolean announcedFlagFirst, announcedClientLean, announcedKnock, announcedTexture, announcedSobbing;

    private CncScans() {
    }

    private static boolean flag(String camel) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
    }

    /** Ticks per texture choice of cnc_sasquatch_texture_scan_interval (read on first use, only while the switch is on). */
    public static final class Texture {
        public static final int N = PureConfig.intValue("cnc_sasquatch_texture_scan_interval", 4, 1, 200);

        private Texture() {
        }
    }

    // ------------------------------------------------------------------------------------- cnc_encounter_flag_first

    /** The player's PlayerVariables while flag-first is on and the capability is there (never a default object), else null. */
    public static CncModVariables.PlayerVariables metVariables(Entity entity) {
        if (!flagFirst || entity == null) return null;
        return entity.getCapability(CncModVariables.PLAYER_VARIABLES_CAPABILITY, null).orElse(null);
    }

    /**
     * The four advancement handlers: true when the original's advancement test cannot throw (the player is not a server
     * player on a server level, so the test is not reached, or the advancement is loaded); else the original must run.
     */
    public static boolean advancementLoaded(Entity entity, ResourceLocation id) {
        if (!(entity instanceof ServerPlayer player) || !(player.m_9236_() instanceof ServerLevel)) return true;
        return player.f_8924_.m_129889_().m_136041_(id) != null;
    }

    /** A handler left out by flag-first: one getEntities on the level's profiler, as its search counts. */
    public static void leftOut(LevelAccessor world) {
        if (world instanceof Level level) level.m_46473_().m_6174_("getEntities");
        if (!announcedFlagFirst) announce(1);
    }

    /**
     * PlayerSquonkCryEffectProcedure: true when the original's condition ends false whatever its search finds (not a living
     * entity, a client level, or Sobbing already active); the original tests the same after its search.
     */
    public static boolean squonkLeftOut(Entity entity) {
        if (!flagFirst || entity == null) return false;
        if (!(entity instanceof LivingEntity living)) return true;
        return living.m_9236_().m_5776_() || living.m_21023_((MobEffect) CncModMobEffects.SOBBING.get());
    }

    // ------------------------------------------------------------------------------------- cnc_encounter_client_lean

    /** True for another player ticked by the client (a client level, a player that is not the local one). */
    public static boolean remotePlayerOnClient(LevelAccessor world, Entity entity) {
        if (!clientLean || world == null || !world.m_5776_() || !(entity instanceof Player player) || player.m_7578_()) return false;
        if (world instanceof Level level) level.m_46473_().m_6174_("getEntities");
        if (!announcedClientLean) announce(2);
        return true;
    }

    /** The squonk handler on a client level (its only effect is behind "not a client level"). */
    public static boolean squonkOnClient(LevelAccessor world, Entity entity) {
        if (!clientLean || entity == null || world == null || !world.m_5776_()) return false;
        if (world instanceof Level level) level.m_46473_().m_6174_("getEntities");
        if (!announcedClientLean) announce(2);
        return true;
    }

    // ------------------------------------------------------------------------------------- the sasquatch

    /** cnc_sasquatch_knock_client_skip: true on a client level (the handler's block scan is left out there). */
    public static boolean knockOnClient(LevelAccessor world) {
        if (!knock || world == null || !world.m_5776_()) return false;
        if (!announcedKnock) announce(3);
        return true;
    }

    /** cnc_sasquatch_texture_scan_interval: true on the ticks between two texture choices. */
    public static boolean textureHeld(Entity entity) {
        if (!texture || entity == null) return false;
        int n = Texture.N;
        if (n <= 1) return false;
        if (!announcedTexture) announce(4);
        return Math.floorMod(entity.f_19797_ + entity.m_19879_(), n) != 0;
    }

    // ------------------------------------------------------------------------------------- the sobbing shader

    /** cnc_sobbing_shader_own_only: true for another player ticked by the client (the handler is left out for it). */
    public static boolean sobbingOtherPlayer(LevelAccessor world, Entity entity) {
        if (!sobbing || world == null || !world.m_5776_() || !(entity instanceof Player player) || player.m_7578_()) return false;
        if (!announcedSobbing) announce(5);
        return true;
    }

    /** First use of a switch: one INFO line. */
    public static void announce(int which) {
        synchronized (CncScans.class) {
            switch (which) {
                case 1 -> {
                    if (announcedFlagFirst) return;
                    announcedFlagFirst = true;
                    LOGGER.info("Bons and Furious: cnc_encounter_flag_first: Critters n' Crawlers field guide handlers stop at once for animals a player has met");
                }
                case 2 -> {
                    if (announcedClientLean) return;
                    announcedClientLean = true;
                    LOGGER.info("Bons and Furious: cnc_encounter_client_lean: Critters n' Crawlers field guide handlers run on the client only for the local player");
                }
                case 3 -> {
                    if (announcedKnock) return;
                    announcedKnock = true;
                    LOGGER.info("Bons and Furious: cnc_sasquatch_knock_client_skip: the sasquatch wood knock scan runs on the server only");
                }
                case 4 -> {
                    if (announcedTexture) return;
                    announcedTexture = true;
                    LOGGER.info("Bons and Furious: cnc_sasquatch_texture_scan_interval: sasquatches choose their texture every {} ticks", Texture.N);
                }
                default -> {
                    if (announcedSobbing) return;
                    announcedSobbing = true;
                    LOGGER.info("Bons and Furious: cnc_sobbing_shader_own_only: the Sobbing screen effect follows the local player and removes only its own effect");
                }
            }
        }
    }
}
