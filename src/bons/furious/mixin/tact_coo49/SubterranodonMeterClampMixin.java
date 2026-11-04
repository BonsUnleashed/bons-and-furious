package bons.furious.mixin.tact_coo49;

import bons.furious.patch.tact_coo49.MeterClamp;
import com.bawnorton.mixinsquared.TargetHandler;
import com.github.alexmodguy.alexscaves.server.entity.living.SubterranodonEntity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tact_subterranodon_meter_clamp (TACT 1.3.0 / 1.5.3 with Alex's Caves 2.0.2, both sides): the value TACT's
 * SubterranodonEntityMixin.tact_subterranodonFlight handler (merged into SubterranodonEntity, HEAD of setMeterAmount)
 * returns is held to [min(0, asked), max(1, asked)] by MeterClamp. See MeterClamp.
 */
@Mixin(value = SubterranodonEntity.class, priority = 1500, remap = false)
public abstract class SubterranodonMeterClampMixin {
    @TargetHandler(mixin = "com.telepathicgrunt.tact.mixin.SubterranodonEntityMixin", name = "tact_subterranodonFlight")
    @ModifyReturnValue(method = "@MixinSquared:Handler", at = @At("RETURN"))
    private float bons$clampMeter(float stored, @Local(argsOnly = true) float asked) {
        return MeterClamp.clamp(stored, asked);
    }
}
