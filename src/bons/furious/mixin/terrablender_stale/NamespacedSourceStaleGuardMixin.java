package bons.furious.mixin.terrablender_stale;

import bons.furious.patch.terrablender_stale.StaleLayerGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * terrablender_stale_layer_guard (Fix; TerraBlender 3.0.1.10 and 3.0.1.11, whose NamespacedSurfaceRuleSource is
 * byte-identical; LGPL-3.0; server side of world generation).
 *
 * NamespacedSurfaceRuleSource.apply(context) does sources.entrySet().forEach(entry -> rules.put(key, value.apply(context)))
 * and then new NamespacedRule(context, base.apply(context), rules). The base.apply call is wrapped to count, per thread, the
 * NamespacedSurfaceRuleSource bases being applied; while that count is above zero the source being applied is an inner
 * layer (one read back from level.dat), and its forEach consumer is wrapped so that each entry goes through
 * StaleLayerGuard.guard (same key, the value inside a GuardedSource). StaleLayerGuard explains what a GuardedSource does and
 * why nothing changes unless TerraBlender would crash. The outermost layer's consumer is returned unchanged.
 *
 * Priority 900: this @ModifyArg is applied before terrablender_lazy_namespace_rules' (default 1000) on the same forEach
 * argument, so its consumer is the inner one and sees the entry after that switch decided whether to defer it (a deferred
 * build is guarded at its first use). In the other order every entry would still be guarded, only built up front.
 */
@Mixin(targets = "terrablender.worldgen.surface.NamespacedSurfaceRuleSource", remap = false, priority = 900)
public abstract class NamespacedSourceStaleGuardMixin {
    @WrapOperation(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;apply(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object bons$countSavedLayers(SurfaceRules.RuleSource base, Object context, Operation<Object> original) {
        StaleLayerGuard.enterBase();
        try {
            return original.call(base, context);
        } finally {
            StaleLayerGuard.exitBase();
        }
    }

    @ModifyArg(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"))
    @SuppressWarnings("rawtypes")
    private Consumer bons$guardSavedLayers(Consumer original) {
        return StaleLayerGuard.consumer(this, original);
    }
}
