package bons.furious.mixin.reliquary_charm;

import bons.furious.patch.reliquary_charm.ReliquaryCharmGate;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import reliquary.items.MobCharmDefinition;
import reliquary.items.MobCharmRegistry;

/**
 * reliquary_mob_charm_type_gate (Reliquary 2.0.45.1248, server, since 1.0.46): at the TAIL of the only writer of
 * MobCharmRegistry's private per-entity map, registerMobCharmDefinition (its static initialiser, registerDynamicCharmDefinitions
 * and any other mod using that public method), ReliquaryCharmGate is handed that map (read-only use) and its per-type memos
 * become stale. Our own logic only; the map itself is not touched.
 */
@Mixin(value = MobCharmRegistry.class, remap = false)
public abstract class MobCharmRegistryVersionMixin {
    @Shadow(remap = false)
    @Final
    private static Map<String, MobCharmDefinition> ENTITY_NAME_CHARM_DEFINITIONS;

    @Inject(method = "registerMobCharmDefinition", at = @At("TAIL"), remap = false)
    private static void bons$charmDefinitionsChanged(MobCharmDefinition definition, CallbackInfo ci) {
        ReliquaryCharmGate.charmsChanged(ENTITY_NAME_CHARM_DEFINITIONS);
    }
}
