package bons.furious.mixin.enigmaticaddons_coo49;

import bons.furious.patch.enigmaticaddons_coo49.FrostSkip;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticaddons_idle_frost_skip (Enigmatic Addons 1.2.7, both sides; All Rights Reserved: no code of the mod is carried).
 * A MixinSquared injection into Enigmatic Addons' MixinLivingEntity.tickMix handler (merged into LivingEntity.tick at its
 * end, run for every living entity on every tick), which does "if (!canFreeze() && getTicksFrozen() > 0)
 * setTicksFrozen(0)": canFreeze() is asked first, for every entity. MixinExtras @WrapOperation on that canFreeze() call:
 * when FrostSkip.skipCanFreeze says the call can only compute a value (no frozen ticks, so the condition is false
 * whatever canFreeze answers; plain canFreeze and only reviewed read-only handlers merged into it; no pending Curios slot
 * update) the call is left out and true is passed on, which ends the condition exactly where a true canFreeze ends it;
 * otherwise canFreeze() runs as shipped. Priority 1500: applied after Enigmatic Addons' own mixin. require = 0 and
 * expect = 0: where Enigmatic Addons' handler is not merged (its mixin absent, as in a pack check without the mod) the
 * injection finds nothing and nothing happens, also under Mixin's injection count check; with Enigmatic Addons installed
 * the handler is there (the switch's guard lines pin tickMix), and the group's offline proof applies Enigmatic Addons' own
 * mixin and checks the merged handler calls this wrapper.
 */
@Mixin(value = LivingEntity.class, priority = 1500, remap = false)
public abstract class EaFrostTickMixin {
    @TargetHandler(mixin = "auviotre.enigmatic.addon.mixin.MixinLivingEntity", name = "tickMix")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_142079_()Z"),
            require = 0, expect = 0, allow = 1)
    private boolean bons$enigmaticaddonsCoo49IdleFrost(LivingEntity self, Operation<Boolean> original) {
        return FrostSkip.canFreeze(self, original);
    }
}
