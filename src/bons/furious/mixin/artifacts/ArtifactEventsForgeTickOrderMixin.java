package bons.furious.mixin.artifacts;

import artifacts.forge.event.ArtifactEventsForge;
import artifacts.item.UmbrellaItem;
import artifacts.item.wearable.WearableArtifactItem;
import artifacts.item.wearable.necklace.CharmOfSinkingItem;
import artifacts.registry.ModGameRules;
import artifacts.registry.ModItems;
import artifacts.registry.ModTags;
import bons.furious.patch.artifacts.ArtifactsTickOrder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * artifacts_living_tick_order (Artifacts 9.5.16, MIT, both sides).
 *
 * ArtifactEventsForge.onLivingUpdate (Forge LivingTickEvent: every living entity, every tick, client and server) calls
 * onKittySlippersLivingUpdate and onUmbrellaLivingUpdate. Both bodies below are Artifacts', with the operands of their
 * conditions in another order (see ArtifactsTickOrder for why the result is identical):
 *  - kitty slippers: the creeper tag test before KITTY_SLIPPERS.isEquippedBy(lastHurtByMob), the Curios scan of the
 *    attacker's inventory;
 *  - umbrella: the water / charm of sinking test (a Curios lookup and scan for every entity in water) after the other
 *    glide tests instead of before them.
 * With the runtime switch off the original order runs.
 */
@Mixin(value = ArtifactEventsForge.class, remap = false)
public abstract class ArtifactEventsForgeTickOrderMixin {
    @Shadow
    @Final
    private static AttributeModifier UMBRELLA_SLOW_FALLING;

    /**
     * @author BonsUnleashed
     * @reason Test the creeper type before the kitty slippers Curios scan (artifacts_living_tick_order).
     */
    @Overwrite
    private static void onKittySlippersLivingUpdate(LivingEvent.LivingTickEvent event) {
        boolean clear;
        if (!ArtifactsTickOrder.enabled) {
            clear = ModGameRules.KITTY_SLIPPERS_ENABLED.get().booleanValue()
                    && ((WearableArtifactItem) ModItems.KITTY_SLIPPERS.get()).isEquippedBy(event.getEntity().m_21188_())
                    && event.getEntity().m_6095_().m_204039_(ModTags.CREEPERS);
        } else {
            LivingEntity entity = event.getEntity();
            clear = entity.m_6095_().m_204039_(ModTags.CREEPERS)
                    && ModGameRules.KITTY_SLIPPERS_ENABLED.get().booleanValue()
                    && ((WearableArtifactItem) ModItems.KITTY_SLIPPERS.get()).isEquippedBy(entity.m_21188_());
            if (ArtifactsTickOrder.SHADOW) {
                clear = ArtifactsTickOrder.shadow("kitty slippers", clear, ModGameRules.KITTY_SLIPPERS_ENABLED.get().booleanValue()
                        && ((WearableArtifactItem) ModItems.KITTY_SLIPPERS.get()).isEquippedBy(entity.m_21188_())
                        && entity.m_6095_().m_204039_(ModTags.CREEPERS));
            }
        }
        if (clear) {
            event.getEntity().m_6703_(null);
        }
    }

    /**
     * @author BonsUnleashed
     * @reason Test water and the charm of sinking after the other umbrella glide tests (artifacts_living_tick_order).
     */
    @Overwrite
    private static void onUmbrellaLivingUpdate(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        AttributeInstance gravity = entity.m_21051_(ForgeMod.ENTITY_GRAVITY.get());
        if (gravity != null) {
            boolean glide;
            if (!ArtifactsTickOrder.enabled) {
                boolean isInWater = entity.m_20069_() && !CharmOfSinkingItem.shouldSink(entity);
                glide = ModGameRules.UMBRELLA_IS_GLIDER.get().booleanValue() && !entity.m_20096_() && !isInWater && entity.m_20184_().f_82480_ < 0.0
                        && !entity.m_21023_(MobEffects.f_19591_) && UmbrellaItem.isHoldingUmbrellaUpright(entity);
            } else {
                glide = ModGameRules.UMBRELLA_IS_GLIDER.get().booleanValue() && !entity.m_20096_() && entity.m_20184_().f_82480_ < 0.0
                        && !entity.m_21023_(MobEffects.f_19591_) && UmbrellaItem.isHoldingUmbrellaUpright(entity)
                        && !(entity.m_20069_() && !CharmOfSinkingItem.shouldSink(entity));
                if (ArtifactsTickOrder.SHADOW) {
                    boolean isInWater = entity.m_20069_() && !CharmOfSinkingItem.shouldSink(entity);
                    glide = ArtifactsTickOrder.shadow("umbrella", glide, ModGameRules.UMBRELLA_IS_GLIDER.get().booleanValue() && !entity.m_20096_() && !isInWater
                            && entity.m_20184_().f_82480_ < 0.0 && !entity.m_21023_(MobEffects.f_19591_) && UmbrellaItem.isHoldingUmbrellaUpright(entity));
                }
            }
            if (glide) {
                if (!gravity.m_22109_(UMBRELLA_SLOW_FALLING)) {
                    gravity.m_22118_(UMBRELLA_SLOW_FALLING);
                }
                entity.f_19789_ = 0.0f;
            } else if (gravity.m_22109_(UMBRELLA_SLOW_FALLING)) {
                gravity.m_22130_(UMBRELLA_SLOW_FALLING);
            }
        }
    }
}
