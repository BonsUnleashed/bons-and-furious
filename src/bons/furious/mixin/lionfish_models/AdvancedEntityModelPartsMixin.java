package bons.furious.mixin.lionfish_models;

import bons.furious.patch.lionfish_models.LionfishModelParts;
import bons.furious.patch.lionfish_models.PartsHolder;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashMap;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lionfish_model_parts (Lionfish API 2.8, LGPL-3.0: no Lionfish code is carried; client, since 1.0.36).
 *
 * MixinExtras @WrapOperation on the getAllParts() call in AdvancedEntityModel.resetToDefaultPose and
 * getAnyDescendantWithName (the kept list for the proven model classes, the call otherwise), and @WrapMethod on
 * getAnyDescendantWithName (a kept positive answer while the part still carries the name, the method otherwise). Both
 * bodies stay Lionfish's. Two @Unique fields per model hold what is kept. See LionfishModelParts for why it is identical.
 */
@Mixin(value = AdvancedEntityModel.class, remap = false)
public abstract class AdvancedEntityModelPartsMixin implements PartsHolder {
    @Unique
    private Iterable<AdvancedModelBox> bons$partList;

    @Unique
    private HashMap<String, Optional<AdvancedModelBox>> bons$bonesByName;

    @Override
    public Iterable<AdvancedModelBox> bons$keptParts() {
        return this.bons$partList;
    }

    @Override
    public void bons$setKeptParts(Iterable<AdvancedModelBox> parts) {
        this.bons$partList = parts;
    }

    @Override
    public HashMap<String, Optional<AdvancedModelBox>> bons$keptBones() {
        return this.bons$bonesByName;
    }

    @Override
    public void bons$setKeptBones(HashMap<String, Optional<AdvancedModelBox>> bones) {
        this.bons$bonesByName = bones;
    }

    @WrapOperation(method = {"resetToDefaultPose", "getAnyDescendantWithName"}, at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/lionfishapi/client/model/tools/AdvancedEntityModel;getAllParts()Ljava/lang/Iterable;"))
    private Iterable<AdvancedModelBox> bons$keptPartList(AdvancedEntityModel<?> model, Operation<Iterable<AdvancedModelBox>> original) {
        return LionfishModelParts.parts(model, original);
    }

    @WrapMethod(method = "getAnyDescendantWithName")
    private Optional<AdvancedModelBox> bons$keptBone(String key, Operation<Optional<AdvancedModelBox>> original) {
        return LionfishModelParts.bone((AdvancedEntityModel<?>) (Object) this, key, original);
    }
}
