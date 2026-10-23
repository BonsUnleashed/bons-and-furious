package bons.furious.patch.projectile_gates;

import bons.furious.mixin.projectile_gates.AbstractClientPlayerInfoAccessor;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Bons and Furious switch knightlib_bone_hitbox_gate (KnightLib 2.0.3, LGPL-3.0, client, since 1.0.46). Helper of
 * KnightLibPickGateMixin; no KnightLib or Minecraft code is carried, only our own decision.
 *
 * <p>Every frame BoneHitboxPicker.pick (from KnightLib's GameRenderer.pick TAIL handler) walks every entity of the client
 * level and asks each one but the camera isSpectator() and then isPickable() before testing {@code instanceof
 * BoneHitboxHolder}; only holders do anything else. While the client level holds no BoneHitboxHolder, the loop can act on
 * nothing: vanilla_entity_class_count_layer counts every entity of the level's entity sections whose class implements that
 * interface (directly, through a superclass or another interface, or through a mixin), and entitiesForRendering() yields
 * exactly the entity lookup of the level's TransientEntitySectionManager, every member of which sits in one of those sections
 * (addEntity puts it in both; a move re-adds it to the new section; removal takes it out of its section and then out of the
 * lookup within the same synchronous callback). The gate then hands the loop an empty iterable after asking the level for
 * its entities as before.
 *
 * <p>Exact because the skipped calls are pure reads. Census of every client jar, Jar-in-Jar included (2026-10-19,
 * evidence/census/projectile_gates-census.txt + pick-helpers-effects.txt): 213 isPickable and 16 isSpectator overrides, none
 * merged by a mixin; they read fields, synched entity data, entity removal state, a parent part's answer or a config value
 * (Amendments' DEFLECT_FIRE_CHARGES, also read by its SmallFireball isPickable inject), and one fills a cache:
 * AbstractClientPlayer.isSpectator calls getPlayerInfo(), which stores the PlayerInfo the first time it is found. So the gate
 * acts only while every player of the level already holds its PlayerInfo (then isSpectator only reads it). With a holder in
 * the level, a player without PlayerInfo, the layer off or unsure, KnightLib's loop runs as shipped.
 *
 * <p>-Dbons_and_furious.knightlibBoneHitboxGate=false turns the gate off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.knightlibBoneHitboxGate.shadow=true never skips; where the gate would, it walks the iterable and
 * counts SHADOW_CHECKS / SHADOW_MISMATCHES (a BoneHitboxHolder in it; WARN for the first 20), then KnightLib's loop runs.
 */
public final class BoneHitboxPickGate {
    /** knightlib_bone_hitbox_gate's runtime switch. */
    public static volatile boolean enabled = ProjectileGates.flag("knightlibBoneHitboxGate");
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.knightlibBoneHitboxGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes and the rig (plain field; written by the render thread). */
    public static long skipped;
    private static volatile boolean announced;

    private BoneHitboxPickGate() {
    }

    /**
     * BoneHitboxPicker.pick's entity loop: {@code all} (ClientLevel.entitiesForRendering(), already asked) or an empty
     * iterable when the loop can act on no entity and its isSpectator/isPickable calls would change nothing.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterable candidates(Level level, Iterable all) {
        if (!enabled || level == null || !ProjectileGates.none(level, ProjectileGates.HOLDER_INDEX) || !playersHaveInfo(level)) return all;
        if (!announced) {
            announced = true;
            ProjectileGates.LOGGER.info("Bons and Furious: knightlib_bone_hitbox_gate: KnightLib's per-frame bone-hitbox pick loop is skipped while the "
                    + "client level holds no bone-hitbox entity{}", SHADOW ? " (SHADOW MODE: the loop runs every frame and is compared)" : "");
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : all) {
                if (isHolder(e)) {
                    if (SHADOW_MISMATCHES.incrementAndGet() <= 20)
                        ProjectileGates.LOGGER.warn("Bons and Furious: knightlib_bone_hitbox_gate SHADOW MISMATCH {}: the layer says {} holds no bone-hitbox entity, "
                                + "the loop would see {}", SHADOW_MISMATCHES.get(), level, e);
                    break;
                }
            }
            return all;
        }
        skipped++;
        return Collections.emptyList();
    }

    /** Every player of the client level holds its PlayerInfo already, so isSpectator() only reads it. */
    static boolean playersHaveInfo(Level level) {
        List<? extends Player> players = level.players();
        for (int i = 0, n = players.size(); i < n; i++) {
            if (!(players.get(i) instanceof AbstractClientPlayerInfoAccessor p) || p.bons$playerInfo() == null) return false;
        }
        return true;
    }

    private static final ClassValue<Boolean> HOLDER_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) if (implementsHolder(c)) return true;
            return false;
        }
    };

    private static boolean implementsHolder(Class<?> c) {
        if (c.getName().equals(ProjectileGates.HOLDER)) return true;
        for (Class<?> i : c.getInterfaces()) if (implementsHolder(i)) return true;
        return false;
    }

    /** Shadow mode only: instanceof BoneHitboxHolder, by name. */
    static boolean isHolder(Object e) {
        return HOLDER_CLASS.get(e.getClass());
    }
}
