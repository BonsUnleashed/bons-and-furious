package bons.furious.mixin.integratedapi_c49;

import bons.furious.patch.integratedapi_c49.IapiBounds;
import com.bawnorton.mixinsquared.TargetHandler;
import com.craisinlord.integrated_api.world.terrainadaptation.beardifier.EnhancedBeardifierData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * integratedapi_beardifier_influence_bounds (Integrated API 1.8.2, world generation).
 *
 * MixinSquared @TargetHandler injection into Integrated API's Beardifier compute handler intapi_calculateDensity (its
 * RETURN @Inject, merged into Beardifier.compute): the call of EnhancedBeardifierHelper.computeDensity is wrapped. Per
 * block, {@link IapiBounds} lets the call run on views of Integrated API's two adaptation lists that hold only the terms
 * reaching the block (or returns the value unchanged when none does). Integrated API's handler, its helper's loops and its
 * arithmetic stay Integrated API's; no Integrated API code is carried (All Rights Reserved). Priority 1500: applied after
 * Integrated API's mixin (priority 100), so the handler exists. require = 0: without the handler nothing is injected.
 * The bons$ fields are named so the Beardifier census of worldgen_empty_beardifier_marker skips them.
 */
@Mixin(value = Beardifier.class, priority = 1500, remap = false)
public abstract class IapiBoundsMixin implements IapiBounds.Holder {
    @Unique
    private IapiBounds.Plan bons$iapiPlan;
    @Unique
    private IapiBounds.View bons$iapiRigidView;
    @Unique
    private IapiBounds.View bons$iapiJunctionView;

    @Override
    public IapiBounds.Plan bons$iapiPlan() {
        return this.bons$iapiPlan;
    }

    @Override
    public void bons$iapiSetPlan(IapiBounds.Plan plan) {
        this.bons$iapiPlan = plan;
    }

    @Override
    public IapiBounds.View bons$iapiRigidView() {
        IapiBounds.View v = this.bons$iapiRigidView;
        if (v == null) v = this.bons$iapiRigidView = new IapiBounds.View();
        return v;
    }

    @Override
    public IapiBounds.View bons$iapiJunctionView() {
        IapiBounds.View v = this.bons$iapiJunctionView;
        if (v == null) v = this.bons$iapiJunctionView = new IapiBounds.View();
        return v;
    }

    @TargetHandler(mixin = "com.craisinlord.integrated_api.mixins.structures.BeardifierMixin", name = "intapi_calculateDensity")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lcom/craisinlord/integrated_api/world/terrainadaptation/beardifier/EnhancedBeardifierHelper;computeDensity(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;DLcom/craisinlord/integrated_api/world/terrainadaptation/beardifier/EnhancedBeardifierData;)D"))
    private double bons$iapiDensity(DensityFunction.FunctionContext ctx, double density, EnhancedBeardifierData data, Operation<Double> original) {
        return IapiBounds.density(this, ctx, density, data, original);
    }
}
