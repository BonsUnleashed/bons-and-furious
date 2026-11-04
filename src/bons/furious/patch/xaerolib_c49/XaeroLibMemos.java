package bons.furious.patch.xaerolib_c49;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xaero.lib.client.config.option.value.redirect.ClientOptionValueRedirectorManager;
import xaero.lib.client.config.primary.option.BuiltInPrimaryClientConfigOptions;
import xaero.lib.client.config.sync.SyncedConfigManager;
import xaero.lib.common.config.Config;
import xaero.lib.common.config.option.BuiltInProfiledConfigOptions;
import xaero.lib.common.config.option.value.redirect.OptionValueRedirectorManager;
import xaero.lib.common.config.profile.ConfigProfile;
import xaero.lib.common.config.single.SingleConfigManager;

/**
 * Bons and Furious switches xaerolib_current_profile_memo and xaerolib_enforcement_read_memo (XaeroLib 1.7.3, nested in
 * Xaero's Minimap 26.6.0 and Xaero's World Map 1.47.0, tested build; client; since 1.0.49). Exact. Idea: Collections Of
 * Optimizations (xaerolib.cacheConfigProfile, xaerolib.cacheEnforcementCheck), idea text only. XaeroLib is All Rights
 * Reserved: none of its code is carried; the injections keep and hand back answers XaeroLib's own methods computed.
 *
 * What XaeroLib does. Every config read of Xaero's mods, ClientConfigManager.getEffective(option), first calls
 * getCurrentProfile(): the primary config's CURRENT_PROFILE value (a redirector check and a map read), then the profile
 * map, then a compare with the last profile it returned. Then, when a world is loaded, a server sent a default enforced
 * profile id (every server with XaeroLib, and singleplayer) and a player exists, shouldIgnoreServerEnforcement reads a
 * second option, IGNORE_ENFORCEMENT_IF_EDITOR, through the whole getEffective chain again (client redirector, the server
 * synced config with its redirector and failed values, the profile). Xaero's Minimap and World Map read 100 to 250 options
 * per frame on the render thread.
 *
 * What the switches do.
 *  - xaerolib_current_profile_memo (MixinExtras @WrapMethod on getCurrentProfile): keeps the answer with the XaeroLibEpoch
 *    epoch it was computed at and answers with it while no input changed (the epoch is the same and no change is in
 *    progress) and the kept profile is the one getCurrentProfile last returned (or the answer was null). Kept only when
 *    the primary config manager and its config are XaeroLib's own classes (no override can change how they read) and no
 *    redirector is registered for CURRENT_PROFILE (a redirector's condition is code that can answer differently each
 *    time; registering one changes the epoch). Why identical: with no change, getCurrentProfile reads the same value and
 *    the same profile, and for the profile it returned last it changes nothing (no switch handling): it returns that
 *    profile, which is what the kept answer is.
 *  - xaerolib_enforcement_read_memo (MixinExtras @WrapOperation on the nested getEffective(config,
 *    IGNORE_ENFORCEMENT_IF_EDITOR) call in shouldIgnoreServerEnforcement): keeps the Boolean with the epoch, the config
 *    object and whether a world was loaded, and answers with it while all three are the same. Kept only when the answer is
 *    not null (stock then fails unboxing it, on every call), the config is not a redirector cache config and is one of
 *    XaeroLib's own classes, the server synced manager and its config are XaeroLib's own classes, and neither the client
 *    nor the server synced redirector manager has a redirector for the option (then the chain has no side effects and its
 *    inputs are exactly the tracked ones). It needs xaerolib_current_profile_memo's change tracking and stands down when
 *    that switch is off. The outer tests of shouldIgnoreServerEnforcement (the option, the player, the enforced profile
 *    id) still run every time.
 *
 * -Dbons_and_furious.xaerolibCurrentProfileMemo=false / -Dbons_and_furious.xaerolibEnforcementReadMemo=false: emergency off.
 * -Dbons_and_furious.xaerolibCurrentProfileMemo.shadow=true / ...xaerolibEnforcementReadMemo.shadow=true (verification runs
 * only): XaeroLib computes every answer as shipped; SHADOW_CHECKS counts the answers a memo would have given,
 * SHADOW_MISMATCHES those that differ (WARN, at most 20 per switch).
 */
public final class XaeroLibMemos {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean profileEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaerolibCurrentProfileMemo", "true"));
    public static volatile boolean enforcementEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaerolibEnforcementReadMemo", "true"));
    public static final boolean PROFILE_SHADOW = Boolean.getBoolean("bons_and_furious.xaerolibCurrentProfileMemo.shadow");
    public static final boolean ENFORCEMENT_SHADOW = Boolean.getBoolean("bons_and_furious.xaerolibEnforcementReadMemo.shadow");
    public static final AtomicLong PROFILE_SHADOW_CHECKS = new AtomicLong(), PROFILE_SHADOW_MISMATCHES = new AtomicLong();
    public static final AtomicLong ENFORCEMENT_SHADOW_CHECKS = new AtomicLong(), ENFORCEMENT_SHADOW_MISMATCHES = new AtomicLong();
    /** The change tracking of xaerolib_current_profile_memo is applied (XaeroLib's Config carries the tracked interface). */
    public static final boolean TRACKING = XaeroLibTracked.class.isAssignableFrom(Config.class);
    private static volatile boolean profileAnnounced, enforcementAnnounced;

    private XaeroLibMemos() {
    }

    /**
     * A kept getCurrentProfile answer, valid while XaeroLibEpoch's word is still `epoch`. Immutable. keep = false marks a
     * manager whose answers may not be kept (see profileMemoable): until the next change its reads go straight to XaeroLib
     * without the checks being repeated.
     */
    public static final class ProfileMemo {
        public final long epoch;
        public final ConfigProfile profile;
        public final boolean keep;

        public ProfileMemo(long epoch, ConfigProfile profile, boolean keep) {
            this.epoch = epoch;
            this.profile = profile;
            this.keep = keep;
        }
    }

    /** A kept IGNORE_ENFORCEMENT_IF_EDITOR read (keep = false: a stand-aside mark for that config, as above). Immutable. */
    public static final class EnforcementMemo {
        public final long epoch;
        public final ConfigProfile config;
        public final boolean levelNull;
        public final Object value;
        public final boolean keep;

        public EnforcementMemo(long epoch, ConfigProfile config, boolean levelNull, Object value, boolean keep) {
            this.epoch = epoch;
            this.config = config;
            this.levelNull = levelNull;
            this.value = value;
            this.keep = keep;
        }
    }

    /** True while nothing changed since the word e was taken. */
    public static boolean holds(long e) {
        return XaeroLibEpoch.holds(e);
    }

    static boolean knownRedirectors(OptionValueRedirectorManager m, Object option) {
        if (m == null) return true;
        Class<?> c = m.getClass();
        if (c != OptionValueRedirectorManager.class && c != ClientOptionValueRedirectorManager.class) return false;
        return m instanceof XaeroLibRedirectors r && !r.bons$xaerolibC49HasRedirector(option);
    }

    static boolean counts(Config c) {
        return c instanceof XaeroLibTracked t && !t.bons$xaerolibC49Quiet();
    }

    /** May a getCurrentProfile answer of a manager with this primary config manager be kept? */
    public static boolean profileMemoable(SingleConfigManager<?> primary) {
        if (!TRACKING || primary == null || primary.getClass() != SingleConfigManager.class) return false;
        Config c = primary.getConfig();
        if (c == null || c.getClass() != Config.class || !counts(c)) return false;
        if (!knownRedirectors(primary.getRedirectorManager(), BuiltInPrimaryClientConfigOptions.CURRENT_PROFILE)) return false;
        if (!profileAnnounced) announceProfile();
        return true;
    }

    /** May a nested IGNORE_ENFORCEMENT_IF_EDITOR read of this config be kept (value already known to be non-null)? */
    public static boolean enforcementMemoable(ConfigProfile config, SyncedConfigManager serverSynced, ClientOptionValueRedirectorManager client) {
        if (!TRACKING || config == null || config.getClass() != ConfigProfile.class || !counts(config)) return false;
        if (serverSynced == null || serverSynced.getClass() != SyncedConfigManager.class) return false;
        Config s = serverSynced.getConfig();
        if (s == null || s.getClass() != Config.class || !counts(s)) return false;
        Object option = BuiltInProfiledConfigOptions.IGNORE_ENFORCEMENT_IF_EDITOR;
        if (client == null || client.getClass() != ClientOptionValueRedirectorManager.class || !knownRedirectors(client, option)) return false;
        if (!knownRedirectors(serverSynced.getRedirectorManager(), option)) return false;
        if (!enforcementAnnounced) announceEnforcement();
        return true;
    }

    public static boolean levelNull() {
        Minecraft mc = Minecraft.m_91087_();
        return mc == null || mc.f_91073_ == null;
    }

    public static void profileShadow(ProfileMemo kept, ConfigProfile fresh) {
        PROFILE_SHADOW_CHECKS.incrementAndGet();
        if (kept.profile != fresh && PROFILE_SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: xaerolib_current_profile_memo shadow mismatch: kept profile {}, XaeroLib returned {}",
                    kept.profile == null ? null : kept.profile.getId(), fresh == null ? null : fresh.getId());
    }

    public static void enforcementShadow(EnforcementMemo kept, Object fresh) {
        ENFORCEMENT_SHADOW_CHECKS.incrementAndGet();
        if (!kept.value.equals(fresh) && ENFORCEMENT_SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: xaerolib_enforcement_read_memo shadow mismatch for profile {}: kept {}, XaeroLib returned {}",
                    kept.config.getId(), kept.value, fresh);
    }

    private static synchronized void announceProfile() {
        if (profileAnnounced) return;
        profileAnnounced = true;
        LOGGER.info("Bons and Furious: xaerolib_current_profile_memo applies (XaeroLib's current config profile is looked up again only after a config change)"
                + (PROFILE_SHADOW ? " [shadow mode: every lookup still runs and is compared]" : ""));
    }

    private static synchronized void announceEnforcement() {
        if (enforcementAnnounced) return;
        enforcementAnnounced = true;
        LOGGER.info("Bons and Furious: xaerolib_enforcement_read_memo applies (XaeroLib's server enforcement setting is read again only after a config change)"
                + (ENFORCEMENT_SHADOW ? " [shadow mode: every read still runs and is compared]" : ""));
    }
}
