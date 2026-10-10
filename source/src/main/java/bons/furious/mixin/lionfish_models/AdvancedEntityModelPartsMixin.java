package bons.furious.mixin.lionfish_models;

import bons.furious.patch.lionfish_models.LionfishModelParts;
import bons.furious.patch.lionfish_models.PartsHolder;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * lionfish_model_parts (Lionfish API, LGPL-3.0: no Lionfish code is carried; 1.21.1 tested build lionfishapi-3.1; client,
 * since 1.0.36).
 *
 * MixinExtras @WrapOperation on the getAllParts() call in AdvancedEntityModel.resetToDefaultPose (the kept list for the
 * proven model classes, the call otherwise). The body stays Lionfish's. One @Unique field per model holds what is kept. See
 * LionfishModelParts for why it is identical.
 *
 * Ported to 1.21.1: resetToDefaultPose is unchanged in Lionfish 3.1. The getAnyDescendantWithName part of 1.0.36 (a wrap of
 * its getAllParts() call and a @WrapMethod keeping found bones) is not ported: Lionfish 3.1 keeps those answers itself in
 * AdvancedEntityModel.descendantCache.
 */
@Mixin(value = AdvancedEntityModel.class, remap = false)
public abstract class AdvancedEntityModelPartsMixin implements PartsHolder {
    @Unique
    private Iterable<AdvancedModelBox> bons$partList;

    @Override
    public Iterable<AdvancedModelBox> bons$keptParts() {
        return this.bons$partList;
    }

    @Override
    public void bons$setKeptParts(Iterable<AdvancedModelBox> parts) {
        this.bons$partList = parts;
    }

    @WrapOperation(method = "resetToDefaultPose", at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/lionfishapi/client/model/tools/AdvancedEntityModel;getAllParts()Ljava/lang/Iterable;"))
    private Iterable<AdvancedModelBox> bons$keptPartList(AdvancedEntityModel<?> model, Operation<Iterable<AdvancedModelBox>> original) {
        return LionfishModelParts.parts(model, original);
    }
}
