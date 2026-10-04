package bons.furious.mixin.terrablender;

import bons.furious.patch.terrablender.LazyNamespaceRules;
import java.util.Map;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * terrablender_lazy_namespace_rules (TerraBlender 3.0.1.10, LGPL-3.0; server side of world generation).
 *
 * NamespacedSurfaceRuleSource.apply(context) does sources.entrySet().forEach(entry -> rules.put(key, value.apply(context))).
 * The consumer handed to forEach is wrapped: each entry first goes through LazyNamespaceRules.defer, which returns the
 * same entry, or (for a namespace made only of vanilla sources) the same key with a source whose apply defers the build
 * to the rule's first use. TerraBlender's own lambda still builds the map in the same order with the same keys. Runs
 * once per chunk surface pass.
 */
@Mixin(targets = "terrablender.worldgen.surface.NamespacedSurfaceRuleSource", remap = false)
public abstract class NamespacedSourceDeferMixin {
    @ModifyArg(method = "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Consumer bons$deferNamespaces(Consumer original) {
        return entry -> original.accept(LazyNamespaceRules.defer((Map.Entry<String, ?>) entry));
    }
}
