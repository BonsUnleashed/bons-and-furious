package bons.furious.mixin.mcreator;

import net.mcreator.underthemoon.init.EntityAnimationFactory;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * moon_animation_entity_guard (Under the Moon 1.0.5).
 *
 * EntityAnimationFactory.onEntityTick runs for every living entity on every tick and tests the entity against each of
 * the mod's 22 animated entity classes, all of which are GeckoLib entities (GeoEntity). For a plain LivingTickEvent
 * whose entity is not a GeoEntity none of those tests can match, so the event is handed to the method as null and its
 * own first check ({@code event != null}) returns at once. Custom event subclasses and GeckoLib entities (the Night
 * Crawler included) run the original code unchanged.
 */
@Mixin(value = EntityAnimationFactory.class, remap = false)
public abstract class MoonAnimationGuardMixin {
    // A variable modifier instead of a cancellable injection: this runs per entity per tick and must not allocate.
    @ModifyVariable(method = "onEntityTick", at = @At("HEAD"), argsOnly = true)
    private static LivingEvent.LivingTickEvent bons$skipNonGeckoEntity(LivingEvent.LivingTickEvent event) {
        boolean skip = event != null && event.getClass() == LivingEvent.LivingTickEvent.class
                && !(event.getEntity() instanceof GeoEntity);
        return skip ? null : event;
    }
}
