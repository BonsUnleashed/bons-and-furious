package bons.furious.mixin.cataclysm_client_c49;

import bons.furious.patch.cataclysm_client_c49.PoseReset;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_pose_reset_loop (L_Ender's Cataclysm; 1.21.1 tested build: L_Ender's Cataclysm 1.21.1-3.33; client; since
 * 1.0.49): in the typed setupAnim of the 21 models that start with root().getAllParts().forEach(ModelPart::resetPose), the
 * getAllParts call and the forEach calls are wrapped (PoseReset): a tree of plain parts is reset by a walk, anything else
 * runs as shipped. Our own logic only.
 *
 * Ported to 1.21.1: the same 21 models of 3.33 open setupAnim with that reset (one getAllParts call each); the methods are
 * named by their exact descriptors (Ceraunus_Model's generic setupAnim erases to Entity, Ignited_Berserker_Model's to
 * Ignited_Berserker_Entity), so the synthetic bridge setupAnim(Entity, ...) of the other models is never selected.
 */
@Mixin(targets = {
        "com.github.L_Ender.cataclysm.client.model.entity.Ancient_Remnant_Rework_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Aptrgangr_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Ceraunus_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Cindaria_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Clawdian_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Draugr_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Elemental_Spear_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Elite_Draugr_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Hippocamtus_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Ignited_Berserker_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Kobolediator_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Maledictus_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Netherite_Ministrosity_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Netherite_Monstrosity_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Royal_Draugr_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Scylla_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Storm_Serpent_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Symbiocto_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.The_Prowler_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Urchinkin_Model",
        "com.github.L_Ender.cataclysm.client.model.entity.Wave_Model"}, remap = false)
public abstract class ModelPoseResetMixin {
    @WrapOperation(method = {
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Ancient_Remnant/Ancient_Remnant_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Aptrgangr_Entity;FFFFF)V",
            "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Cindaria_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Clawdian_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Draugr_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/projectile/Elemental_Spear_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Elite_Draugr_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Hippocamtus_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Ignited_Berserker_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Kobolediator_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Maledictus/Maledictus_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/Pet/Netherite_Ministrosity_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/NewNetherite_Monstrosity/Netherite_Monstrosity_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Royal_Draugr_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Scylla/Scylla_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/projectile/Storm_Serpent_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Symbiocto_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/The_Prowler_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Urchinkin_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/effect/Wave_Entity;FFFFF)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;getAllParts()Ljava/util/stream/Stream;"),
            require = 1, allow = 1)
    private Stream<ModelPart> bons$poseResetParts(ModelPart root, Operation<Stream<ModelPart>> original, @Share("bons$poseRoot") LocalRef<ModelPart> kept) {
        return PoseReset.allParts(root, original, kept);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = {
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Ancient_Remnant/Ancient_Remnant_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Aptrgangr_Entity;FFFFF)V",
            "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Cindaria_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Clawdian_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Draugr_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/projectile/Elemental_Spear_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Elite_Draugr_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Hippocamtus_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Ignited_Berserker_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Kobolediator_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Maledictus/Maledictus_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/Pet/Netherite_Ministrosity_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/NewNetherite_Monstrosity/Netherite_Monstrosity_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/Draugar/Royal_Draugr_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Scylla/Scylla_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/projectile/Storm_Serpent_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Symbiocto_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/The_Prowler_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/AcropolisMonsters/Urchinkin_Entity;FFFFF)V",
            "setupAnim(Lcom/github/L_Ender/cataclysm/entity/effect/Wave_Entity;FFFFF)V"},
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"),
            require = 1)
    private void bons$poseResetWalk(Stream stream, Consumer action, Operation<Void> original, @Share("bons$poseRoot") LocalRef<ModelPart> kept) {
        PoseReset.forEach(stream, action, original, kept);
    }
}
