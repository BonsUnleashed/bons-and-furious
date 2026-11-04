package bons.furious.mixin.mcreator_guards49;

import bons.furious.patch.mcreator_guards49.AnimationGuards;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * macabre_animation_entity_guard (Macabre 1.0.0 (= 0.9.2; COO's pin 0.8.4 an accepted alternative build); both sides; since 1.0.49), on the mcreator_animation_entity_guard layer.
 *
 * EntityAnimationFactory.onEntityTick runs for every living entity on every tick and only tests the entity against the mod's
 * 122 animated entity classes, all GeckoLib entities (GeoEntity). A plain LivingTickEvent whose entity is not a GeckoLib entity
 * reaches the method as null, so its own first test ({@code event != null}) returns at once; GeckoLib entities and event
 * subclasses run the original code unchanged (AnimationGuards.Caller.filter). Guarded by the handler's fingerprint. Our own
 * logic only; the mod's code stays in its jar.
 */
@Mixin(targets = "com.curseforge.macabre.init.EntityAnimationFactory", remap = false)
public abstract class MacabreEntityGuardMixin {
    // A variable modifier instead of a cancellable injection: this runs per entity per tick and must not allocate.
    @ModifyVariable(method = "onEntityTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V", at = @At("HEAD"),
            argsOnly = true, remap = false)
    private static LivingEvent.LivingTickEvent bons$skipNonGeckoEntity(LivingEvent.LivingTickEvent event) {
        return AnimationGuards.MACABRE.filter(event);
    }
}