package bons.furious.mixin.mcreator_entity_guards;

import bons.furious.patch.mcreator_entity_guards.EntityGuards;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * mcreator_animation_entity_guard (Born in Chaos 1.7.5; both sides; since 1.0.45).
 *
 * EntityAnimationFactory.onEntityTick runs for every living entity on every tick and only tests the entity against the mod's 83
 * animated entity classes, all GeckoLib entities (GeoEntity). A plain LivingTickEvent whose entity is not a GeckoLib entity
 * reaches the method as null, so its own first test ({@code event != null}) returns at once; GeckoLib entities and event
 * subclasses run the original code unchanged (EntityGuards.filter). Guarded per target (TargetGuards). Our own logic only;
 * the mod's code stays in its jar.
 */
@Mixin(targets = "net.mcreator.borninchaosv.init.EntityAnimationFactory", remap = false)
public abstract class BornInChaosEntityGuardMixin {
    // A variable modifier instead of a cancellable injection: this runs per entity per tick and must not allocate.
    @ModifyVariable(method = "onEntityTick(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V", at = @At("HEAD"),
            argsOnly = true, remap = false)
    private static EntityTickEvent.Pre bons$skipNonGeckoEntity(EntityTickEvent.Pre event) {
        return EntityGuards.filter(event);
    }
}
