package bons.furious.mixin.mcreator_guards49;

import bons.furious.patch.mcreator_guards49.MacabreAnimationGuards;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * macabre_animation_entity_guard (Macabre 1.0.1 for NeoForge 1.21.1; both sides; since 1.0.49), on the
 * mcreator_animation_entity_guard design.
 *
 * EntityAnimationFactory.onEntityTick(EntityTickEvent.Pre) runs for every entity on every tick and only tests the entity
 * against the mod's 122 animated entity classes, all GeckoLib entities (GeoEntity). A plain EntityTickEvent.Pre whose entity
 * is not a GeckoLib entity reaches the method as null, so its own first test ({@code event != null}) returns at once;
 * GeckoLib entities and event subclasses run the original code unchanged (MacabreAnimationGuards.filter). Guarded by the
 * handler's fingerprint. Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "com.curseforge.macabre.init.EntityAnimationFactory", remap = false)
public abstract class MacabreEntityGuardMixin {
    // A variable modifier instead of a cancellable injection: this runs per entity per tick and must not allocate.
    @ModifyVariable(method = "onEntityTick(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V", at = @At("HEAD"),
            argsOnly = true, remap = false)
    private static EntityTickEvent.Pre bons$skipNonGeckoEntity(EntityTickEvent.Pre event) {
        return MacabreAnimationGuards.filter(event);
    }
}
