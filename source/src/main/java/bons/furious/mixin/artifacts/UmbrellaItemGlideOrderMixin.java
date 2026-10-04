package bons.furious.mixin.artifacts;

import artifacts.Artifacts;
import artifacts.equipment.EquipmentHelper;
import artifacts.item.UmbrellaItem;
import artifacts.registry.ModDataComponents;
import bons.furious.patch.artifacts.ArtifactsTickOrder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * artifacts_living_tick_order, umbrella half (Artifacts; 1.21.1 tested build: Artifacts 13.2.5 for NeoForge 1.21.1, MIT;
 * both sides).
 *
 * UmbrellaItem.shouldGlide(entity) is the umbrella glide test: ArtifactHooks.livingUpdate calls it (through
 * UmbrellaItem.onLivingUpdate, fall-distance reset) for every living entity that is not on the ground, every tick, and
 * the player gravity modifier (DynamicAttributeModifier) uses it too. The body below is Artifacts', with the water /
 * sinking operand (EquipmentHelper.hasAbilityActive(SINKING, entity, true) for an entity in water: a scan of its armor and
 * Curios slots) moved after isHoldingUmbrellaUpright (see ArtifactsTickOrder for why the result is identical). With the
 * runtime switch off the original order runs.
 *
 * Ported to 1.21.1: the 1.20.1 target was ArtifactEventsForge.onUmbrellaLivingUpdate, which computed the water / charm
 * of sinking test before all the glide tests; Artifacts 13.2.5 already tests it after the airborne, falling,
 * slow-falling and glider-option tests, so the remaining move is behind the umbrella-held test. The gravity modifier
 * and fall-distance reset that the 1.20.1 handler also contained now live in Artifacts' callers of shouldGlide and
 * follow its (unchanged) result.
 */
@Mixin(value = UmbrellaItem.class, remap = false)
public abstract class UmbrellaItemGlideOrderMixin {
    /**
     * @author BonsUnleashed
     * @reason Test water and the charm of sinking after the umbrella-held test (artifacts_living_tick_order).
     */
    @Overwrite
    public static boolean shouldGlide(LivingEntity entity) {
        if (!ArtifactsTickOrder.reorder()) {
            return !entity.onGround() && entity.getDeltaMovement().y < 0.0 && !entity.hasEffect(MobEffects.SLOW_FALLING)
                    && Artifacts.CONFIG.items.umbrella.isGlider.get()
                    && (!entity.isInWater() || EquipmentHelper.hasAbilityActive(ModDataComponents.SINKING.get(), entity, true))
                    && UmbrellaItem.isHoldingUmbrellaUpright(entity);
        }
        boolean glide = !entity.onGround() && entity.getDeltaMovement().y < 0.0 && !entity.hasEffect(MobEffects.SLOW_FALLING)
                && Artifacts.CONFIG.items.umbrella.isGlider.get()
                && UmbrellaItem.isHoldingUmbrellaUpright(entity)
                && (!entity.isInWater() || EquipmentHelper.hasAbilityActive(ModDataComponents.SINKING.get(), entity, true));
        if (ArtifactsTickOrder.SHADOW) {
            glide = ArtifactsTickOrder.shadow("umbrella", glide, !entity.onGround() && entity.getDeltaMovement().y < 0.0
                    && !entity.hasEffect(MobEffects.SLOW_FALLING) && Artifacts.CONFIG.items.umbrella.isGlider.get()
                    && (!entity.isInWater() || EquipmentHelper.hasAbilityActive(ModDataComponents.SINKING.get(), entity, true))
                    && UmbrellaItem.isHoldingUmbrellaUpright(entity));
        }
        return glide;
    }
}
