package bons.furious.patch.shake_gates_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 presence gates of group shake_gates_c49 (client; Minecraft 1.21.1 + NeoForge 21.1). Helper of the
 * group's mixins; no code of the target mods or of Minecraft is carried.
 *
 * Per-frame (or per fog event) entity searches for entity types that usually do not exist:
 *   dodosmobs_shake_scan_presence_gate        Dodo's Mobs ClientEvent.onCameraSetup: ScreenShake_Entity, player +-20
 *   eeeabsmobs_shake_scan_presence_gate       EEEAB's Mobs ClientEventHandler.onSetupCamera: EntityCameraShake, +-20
 *   fromtheshadows_shake_scan_presence_gate   From The Shadows 3.0 ClientEvents.onCameraSetup: ScreenShakeEntity, +-20
 *   mythsandlegends_shake_scan_presence_gate  Myths and Legends ClientEvents.onCameraSetup: EffectScreenShake, +-20
 *   mythsandlegends_legacy_shake_scan_presence_gate   the same search in the legacy build, EventBusClientEvents.onCameraSetup
 *                                             (a key of its own: each build lacks the other one's class, so one key is ABSENT)
 *   saintsdragons_shake_scan_presence_gate    Saint's Dragons ClientEventHandler.onComputeCamera: every Mob +-64 that
 *                                             implements its ShakesScreen (the Alex's Caves shape)
 *   cnc_wechuge_fog_scan_presence_gate        Critters n' Crawlers FogColorSetupProcedure / FogDistanceSetupProcedure
 *                                             execute: WechugeEntity in a 24 and a 12 block box around the camera entity
 *   saintsdragons_skyfall_scan_presence_gate  Saint's Dragons IgnivorusSkyfallScreenEffects.sample: a loop over every
 *                                             client entity (entitiesForRendering) for an Ignivorus, 2-3 times per frame
 * vanilla_entity_class_count_layer counts those classes (and ShakesScreen implementors) in every level's entity sections
 * (registration bons_and_furious.shake_gates_c49.entity_classes.json); while the level holds none:
 *  - the searches are made exactly as before (the profiler's "getEntities" count and NeoForge's part pass run) but marked
 *    EMPTY, so vanilla_entity_walk_skip leaves out their walk over the entity sections: for the class searches the walk
 *    would visit no entity; for Saint's Dragons the walk would visit the other mobs, run the mod's predicate (instanceof
 *    ShakesScreen: false for each, no side effect) and add nothing; NeoForge's part pass cannot add a Mob (a part entity is
 *    never one). The listeners then run on the same empty list (same camera angles, fog, random draws);
 *  - the skyfall loop gets an empty iterable instead of entitiesForRendering() (the client's entity lookup and its
 *    sections hold the same entities; the loop's only effects are for Ignivorus entities), so sample() returns {0, 0, 0} as
 *    it does after the full loop.
 * With an instance anywhere in the level (or counts the layer is not sure of) everything runs in full.
 * Requires vanilla_entity_class_count_layer and vanilla_entity_walk_skip (reached through EntityPresenceLink's method
 * handles); without them every search runs in full.
 * Runtime flags (-D...=false turns one off): bons_and_furious.dodosmobsShakeScanPresenceGate, .eeeabsmobsShakeScanPresenceGate,
 * .fromtheshadowsShakeScanPresenceGate, .mythsandlegendsShakeScanPresenceGate, .mythsandlegendsLegacyShakeScanPresenceGate,
 * .saintsdragonsShakeScanPresenceGate,
 * .cncWechugeFogScanPresenceGate, .saintsdragonsSkyfallScanPresenceGate.
 * SHADOW MODE: -Dbons_and_furious.shakeGates49.shadow=true never marks or replaces; where a gate would act it runs the
 * original and counts SHADOW_CHECKS / SHADOW_MISMATCHES (a result holding any non-part entity, or an Ignivorus in the
 * rendered-entity list; WARN for the first 20).
 */
public final class ShakeGates49 {
    public static final int DODOS = 0, EEEAB = 1, FROM_THE_SHADOWS = 2, MYTHS = 3, CNC_WECHUGE = 4, SD_SHAKES = 5, SD_IGNIVORUS = 6, MYTHS_LEGACY = 7;
    static final String[] NAMES = {
            "com.github.dodo.dodosmobs.entity.effect.ScreenShake_Entity",
            "com.eeeab.eeeabsmobs.sever.entity.effect.EntityCameraShake",
            "net.sonmok14.fromtheshadows.server.entity.projectiles.ScreenShakeEntity",
            "com.ovinter.mythsandlegends.entity.effects.EffectScreenShake",
            "net.imasillylittleguy.cnc.entity.WechugeEntity",
            "com.leon.saintsdragons.server.entity.interfaces.ShakesScreen",
            "com.leon.saintsdragons.server.entity.dragons.ignivorus.Ignivorus",
            "com.ovinter.mythsandlegends.entity.effects.EffectScreenShake"};
    static final String[] KEYS = {"dodosmobs_shake_scan_presence_gate", "eeeabsmobs_shake_scan_presence_gate",
            "fromtheshadows_shake_scan_presence_gate", "mythsandlegends_shake_scan_presence_gate", "cnc_wechuge_fog_scan_presence_gate",
            "saintsdragons_shake_scan_presence_gate", "saintsdragons_skyfall_scan_presence_gate", "mythsandlegends_legacy_shake_scan_presence_gate"};

    public static volatile boolean dodosmobs = flag("dodosmobsShakeScanPresenceGate");
    public static volatile boolean eeeabsmobs = flag("eeeabsmobsShakeScanPresenceGate");
    public static volatile boolean fromtheshadows = flag("fromtheshadowsShakeScanPresenceGate");
    public static volatile boolean mythsandlegends = flag("mythsandlegendsShakeScanPresenceGate");
    /** Myths and Legends' legacy build: its own key, mythsandlegends_legacy_shake_scan_presence_gate. */
    public static volatile boolean mythsandlegendsLegacy = flag("mythsandlegendsLegacyShakeScanPresenceGate");
    public static volatile boolean cncWechuge = flag("cncWechugeFogScanPresenceGate");
    public static volatile boolean saintsdragons = flag("saintsdragonsShakeScanPresenceGate");
    public static volatile boolean saintsdragonsSkyfall = flag("saintsdragonsSkyfallScanPresenceGate");
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.shakeGates49.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics (the render thread writes them): searches marked, skyfall loops answered empty. */
    public static long marked, loopsSkipped;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Per call site kind: the class the search names (checked by name once, then by identity). */
    private static final Class<?>[] SEEN = new Class<?>[NAMES.length];
    private static volatile int announced;

    private ShakeGates49() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The layer's indices, resolved on first use (the layer reads its registration files when it first initialises). */
    private static final class Indices {
        static final int[] OF = new int[NAMES.length];

        static {
            for (int i = 0; i < NAMES.length; i++) OF[i] = EntityPresenceLink.trackedIndex(NAMES[i]);
        }
    }

    /** The search names exactly the class of kind {@code which} (by name, remembered by identity). */
    private static boolean names(int which, Class<?> searched) {
        if (searched == null) return false;
        if (SEEN[which] == searched) return true;
        if (!searched.getName().equals(NAMES[which])) return false;
        SEEN[which] = searched;
        return true;
    }

    private static void announce(int which) {
        int a = announced;
        if ((a & (1 << which)) != 0) return;
        synchronized (ShakeGates49.class) {
            if ((announced & (1 << which)) != 0) return;
            announced |= 1 << which;
        }
        if (which == SD_IGNIVORUS) LOGGER.info("Bons and Furious: {}: the skyfall screen effect skips its loop over every client entity while the client level holds no Ignivorus{}",
                KEYS[which], SHADOW ? " (SHADOW MODE: the loop runs in full and is compared)" : "");
        else LOGGER.info("Bons and Furious: {}: the search walks no entity sections while the client level holds no {}{}", KEYS[which],
                NAMES[which].substring(NAMES[which].lastIndexOf('.') + 1), SHADOW ? " (SHADOW MODE: the search runs in full and is compared)" : "");
    }

    @SuppressWarnings("rawtypes")
    private static List shadow(int which, List found, Object level) {
        SHADOW_CHECKS.incrementAndGet();
        for (Object e : found) {
            if (e instanceof PartEntity) continue;   // the walk never yields part entities; NeoForge's pass after it does
            if (SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no {}, the search found {}", KEYS[which],
                        SHADOW_MISMATCHES.get(), level, NAMES[which], e);
            }
            break;
        }
        return found;
    }

    /** A gate's question: the layer says the level holds no instance of kind {@code which} (false = unknown). */
    private static boolean none(int which, Object level) {
        return level instanceof Level l && EntityPresenceLink.none(l, Indices.OF[which]);
    }

    /** getEntitiesOfClass(Class, AABB) whose class is the tracked one of kind {@code which}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(boolean on, int which, Object level, Class<?> searched, Object box, Operation<List> original) {
        if (!on || !names(which, searched) || !none(which, level)) return original.call(level, searched, box);
        announce(which);
        if (SHADOW) return shadow(which, original.call(level, searched, box), level);
        if (!EntityPresenceLink.markEmpty(KEYS[which], level, searched, Indices.OF[which])) return original.call(level, searched, box);
        marked++;
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** getEntitiesOfClass(Class, AABB, Predicate) whose class is the tracked one of kind {@code which} (the cnc fog searches). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scan(boolean on, int which, Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!on || !names(which, searched) || !none(which, level)) return original.call(level, searched, box, predicate);
        announce(which);
        if (SHADOW) return shadow(which, original.call(level, searched, box, predicate), level);
        if (!EntityPresenceLink.markEmpty(KEYS[which], level, searched, Indices.OF[which])) return original.call(level, searched, box, predicate);
        marked++;
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** Saint's Dragons: getEntitiesOfClass(Mob.class, AABB, mob -> mob instanceof ShakesScreen). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List scanShakesScreen(Object level, Class<?> searched, Object box, Object predicate, Operation<List> original) {
        if (!saintsdragons || searched != Mob.class || !none(SD_SHAKES, level)) return original.call(level, searched, box, predicate);
        announce(SD_SHAKES);
        if (SHADOW) return shadow(SD_SHAKES, original.call(level, searched, box, predicate), level);
        if (!EntityPresenceLink.markEmpty(KEYS[SD_SHAKES], level, Mob.class, Indices.OF[SD_SHAKES])) return original.call(level, searched, box, predicate);
        marked++;
        try {
            return original.call(level, searched, box, predicate);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** Saint's Dragons skyfall: ClientLevel.entitiesForRendering() inside IgnivorusSkyfallScreenEffects.sample. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterable renderList(Object level, Operation<Iterable> original) {
        if (!saintsdragonsSkyfall || !none(SD_IGNIVORUS, level)) return original.call(level);
        announce(SD_IGNIVORUS);
        if (SHADOW) {
            Iterable all = original.call(level);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : all) {
                if (e != null && isIgnivorus(e.getClass())) {
                    if (SHADOW_MISMATCHES.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the layer says {} holds no Ignivorus, the client entity list holds {}",
                            KEYS[SD_IGNIVORUS], SHADOW_MISMATCHES.get(), level, e);
                    break;
                }
            }
            return all;
        }
        loopsSkipped++;
        return Collections.emptyList();
    }

    private static boolean isIgnivorus(Class<?> c) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) if (k.getName().equals(NAMES[SD_IGNIVORUS])) return true;
        return false;
    }
}
