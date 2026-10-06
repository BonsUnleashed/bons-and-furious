package bons.furious.mixin.terrain;

import bons.furious.patch.terrain.ClimateRepeat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_climate_sample_repeat (Minecraft 1.20.1 world generation).
 *
 * Climate.Sampler.sample evaluates six density functions at one quart. In this pack the biome lookup chain samples the
 * same quart several times in a row (Alex's Caves, ElysiumAPI, TerraBlender, vanilla). The method now returns this
 * thread's previous result when it was for the same sampler and quart (see ClimateRepeat), otherwise it runs and its
 * result is remembered. The audit state of each sampler lives in a field added here.
 */
@Mixin(value = Climate.Sampler.class, remap = false)
public abstract class ClimateSampleRepeatMixin implements ClimateRepeat.AuditedSampler {
    @Unique   // 1.0.34: transient: Gson reads a record's non-transient fields as components and fails without an accessor
    private transient byte bons$audit;

    @Override
    public byte bons$auditState() {
        return this.bons$audit;
    }

    @Override
    public void bons$auditState(byte state) {
        this.bons$audit = state;
    }

    /** Start of sample: the previous result of this thread for the same sampler and quart replaces the evaluation. */
    @Inject(method = "m_183445_", at = @At("HEAD"), cancellable = true)
    private void bons$repeatedSample(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        Object last = ClimateRepeat.lastSample(this, x, y, z);
        if (last != null) cir.setReturnValue((Climate.TargetPoint) last);
    }

    /** Return of sample: remember the result and return it unchanged. */
    @ModifyReturnValue(method = "m_183445_", at = @At("RETURN"))
    private Climate.TargetPoint bons$rememberSample(Climate.TargetPoint point, int x, int y, int z) {
        return (Climate.TargetPoint) ClimateRepeat.rememberSample(this, x, y, z, point);
    }
}
