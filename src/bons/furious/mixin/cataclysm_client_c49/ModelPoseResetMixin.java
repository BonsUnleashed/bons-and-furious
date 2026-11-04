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
 * cataclysm_pose_reset_loop (L_Ender's Cataclysm 3.16 / 3.31; client; since 1.0.49): in the setupAnim (typed, or m_6973_ for the generic Ceraunus model) of the 21
 * models that start with root().getAllParts().forEach(ModelPart::resetPose), the getAllParts call and the forEach calls
 * are wrapped (PoseReset): a tree of plain parts is reset by a walk, anything else runs as shipped. Our own logic only.
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
    @WrapOperation(method = {"setupAnim", "m_6973_"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;m_171331_()Ljava/util/stream/Stream;", remap = false),
            require = 1, allow = 1, remap = false)
    private Stream<ModelPart> bons$poseResetParts(ModelPart root, Operation<Stream<ModelPart>> original, @Share("bons$poseRoot") LocalRef<ModelPart> kept) {
        return PoseReset.allParts(root, original, kept);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = {"setupAnim", "m_6973_"}, at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V", remap = false),
            require = 1, remap = false)
    private void bons$poseResetWalk(Stream stream, Consumer action, Operation<Void> original, @Share("bons$poseRoot") LocalRef<ModelPart> kept) {
        PoseReset.forEach(stream, action, original, kept);
    }
}
