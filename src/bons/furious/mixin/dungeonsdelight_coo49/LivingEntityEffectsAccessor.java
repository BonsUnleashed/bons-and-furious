package bons.furious.mixin.dungeonsdelight_coo49;

import java.util.Map;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * dungeonsdelight_monster_effect_lookup: read access to vanilla LivingEntity.activeEffects (the map getActiveEffects()
 * returns the values of), so the check does not go through hasEffect, which other mods may hook. Changes nothing in
 * LivingEntity.
 */
@Mixin(value = LivingEntity.class, remap = false)
public interface LivingEntityEffectsAccessor {
    @Accessor("f_20945_")
    Map<MobEffect, MobEffectInstance> bons$activeEffectMap();
}
