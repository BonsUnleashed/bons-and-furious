package bons.furious.patch.lionfish_models;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lionfish_model_parts (Lionfish API 2.8 with the L_Ender's Cataclysm 3.16 models, client, since
 * 1.0.36). Ours; Lionfish API is LGPL-3.0-only, but no Lionfish code is carried (both injectors keep its bodies).
 *
 * AdvancedEntityModel.resetToDefaultPose() (every frame for every Lionfish model: 57 Cataclysm entity models call it from
 * setupAnim) and getAnyDescendantWithName(key) (once per animated bone per frame for the 9 models with keyframe
 * animations) each ask getAllParts(), which the models implement as ImmutableList.of(<every part>): a new list (up to 74
 * parts) per call; the name lookup then streams it to the first part whose boxName equals the key.
 *
 * For the 79 model classes listed in PURE_PARTS, getAllParts() only reads final fields of the model and builds an
 * immutable list (bytecode of each, in the build notes; the 80th, Ender_Guardian_Bullet_Model, reads a non-final field and
 * is left out), so every call returns an equal list: the same parts in the same order. The first complete list
 * (ImmutableList.of rejects nulls, so it is never taken half-built) is kept on the model and given to both methods.
 * A found part is kept per name and handed out again while it still carries that name; no class in the pack writes
 * AdvancedModelBox.boxName outside its constructor (pack-wide bytecode scan), so the first match cannot change; a miss
 * runs the stock lookup every time. The only caller (AdvancedKeyframeAnimations.animate) only calls ifPresent on the
 * Optional, so handing out one Optional instead of an equal new one is not observable. Other model classes run as before.
 *
 * -Dbons_and_furious.lionfishModelParts=false always asks getAllParts(); .shadow=true (verification runs) also asks it
 * (and the stock lookup) on every kept answer and counts any difference (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class LionfishModelParts {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lionfishModelParts", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lionfishModelParts.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** Model classes whose getAllParts() is ImmutableList.of(final fields of this) (build scan of the pack's jars). */
    static final Set<String> PURE_PARTS = Set.of(
            "com.github.L_Ender.cataclysm.client.model.block.Abyssal_Egg_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Altar_of_Abyss_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Altar_of_Amethyst_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Altar_of_Fire_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Altar_of_Void_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Boss_Respawn_Spawner_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Cursed_Tombstone_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Door_Of_Seal_Model",
            "com.github.L_Ender.cataclysm.client.model.block.EMP_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Goddess_Statue_Model",
            "com.github.L_Ender.cataclysm.client.model.block.Mechanical_Anvil_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Abyss_Blast_Portal_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Abyss_Mine_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Amethyst_Cluster_Projectile_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Amethyst_Crab_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Ancient_Desert_Stele_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Ancient_Remnant_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Axe_Blade_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Coral_Bardiche_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Coral_Golem_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Coral_Spear_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Coralssus_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Cursed_Sandstorm_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Deepling_Angler_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Deepling_Brute_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Deepling_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Deepling_Priest_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Deepling_Warlock_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Ender_Golem_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Endermaptera_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Ignis_Fireball_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Ignited_Revenant_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Koboleton_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Lava_Bomb_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Lionfish_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Modern_Remnant_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Nameless_Sorcerer_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Old_Netherite_Monstrosity_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Phantom_Halberd_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.PlayerSandstorm_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Sandstorm_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Sandstorm_Projectile_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.The_Baby_Leviathan_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.The_Harbinger_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.The_Leviathan_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.The_Leviathan_Tongue_End_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.The_Leviathan_Tongue_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.The_Watcher_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Tidal_Hook_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Tidal_Tentacle_Claws_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Tidal_Tentacle_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Void_Howitzer_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Void_Rune_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Wadjet_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Wither_Homing_Missile_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Wither_Howitzer_Model",
            "com.github.L_Ender.cataclysm.client.model.entity.Wither_Missile_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Ancient_Spear_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Astrape_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Azure_Sea_Shield_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Black_Steel_Targe_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Bulwark_of_the_flame_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Ceraunus_Item_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Cursed_Bow_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Gauntlet_of_Bulwark_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Gauntlet_of_Guard_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Gauntlet_of_Maelstrom_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Incinerator_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Laser_Gatling_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Meat_Shredder_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Soul_render_Model",
            "com.github.L_Ender.cataclysm.client.model.item.The_Annihilator_Model",
            "com.github.L_Ender.cataclysm.client.model.item.The_Immolator_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Tidal_Claws_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Void_Forge_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Wither_Assault_SHoulder_Weapon_Model",
            "com.github.L_Ender.cataclysm.client.model.item.Wrath_of_Desert_Model");

    static final ClassValue<Boolean> PURE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return PURE_PARTS.contains(type.getName());
        }
    };

    private LionfishModelParts() {
    }

    /** The getAllParts() call inside resetToDefaultPose / getAnyDescendantWithName. */
    public static Iterable<AdvancedModelBox> parts(AdvancedEntityModel<?> model, Operation<Iterable<AdvancedModelBox>> original) {
        if (!enabled || !PURE.get(model.getClass())) return original.call(model);
        PartsHolder h = (PartsHolder) model;
        Iterable<AdvancedModelBox> kept = h.bons$keptParts();
        if (kept != null) {
            if (SHADOW) shadowParts(model, kept, original.call(model));
            return kept;
        }
        Iterable<AdvancedModelBox> fresh = original.call(model);
        h.bons$setKeptParts(fresh);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lionfish_model_parts applies (Lionfish models keep their part list and found bones){}", SHADOW ? " - shadow verification on" : "");
        }
        return fresh;
    }

    /** getAnyDescendantWithName(key), wrapped. */
    public static Optional<AdvancedModelBox> bone(AdvancedEntityModel<?> model, String key, Operation<Optional<AdvancedModelBox>> original) {
        if (!enabled || !PURE.get(model.getClass())) return original.call(key);
        PartsHolder h = (PartsHolder) model;
        HashMap<String, Optional<AdvancedModelBox>> bones = h.bons$keptBones();
        if (bones != null) {
            Optional<AdvancedModelBox> o = bones.get(key);
            if (o != null && Objects.equals(o.get().boxName, key)) {
                if (SHADOW) {
                    Optional<AdvancedModelBox> asked = original.call(key);
                    SHADOW_CHECKS.incrementAndGet();
                    if (asked.orElse(null) != o.get() && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                        LOGGER.warn("Bons and Furious: lionfish_model_parts shadow mismatch: {} bone {}", model.getClass().getName(), key);
                    return asked;
                }
                return o;
            }
        }
        Optional<AdvancedModelBox> answer = original.call(key);
        if (answer.isPresent()) {
            if (bones == null) h.bons$setKeptBones(bones = new HashMap<>());
            bones.put(key, answer);
        }
        return answer;
    }

    private static void shadowParts(AdvancedEntityModel<?> model, Iterable<AdvancedModelBox> kept, Iterable<AdvancedModelBox> fresh) {
        SHADOW_CHECKS.incrementAndGet();
        Iterator<AdvancedModelBox> a = kept.iterator(), b = fresh.iterator();
        boolean same = true;
        while (a.hasNext() && b.hasNext()) if (a.next() != b.next()) { same = false; break; }
        if (same && (a.hasNext() || b.hasNext())) same = false;
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: lionfish_model_parts shadow mismatch: {} part list changed", model.getClass().getName());
    }
}