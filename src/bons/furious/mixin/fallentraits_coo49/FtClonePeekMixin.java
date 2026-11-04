package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * fallentraits_unset_persistent_data_skip (Fallen Traits & Relics 1.0.0; server side): @Redirect of the first
 * entity.getPersistentData() of FTEvents.onCloneTick, onCloneDamaged and onCloneDeath, whose answer is only asked
 * getBoolean("fallen_traits_is_clone") before the handler returns when false: the existing compound, or a shared empty one
 * that nothing writes when the entity has none (FtPeek). Our own logic only.
 */
@Mixin(targets = "net.kayn.fallen_traits.events.FTEvents", remap = false)
public abstract class FtClonePeekMixin {
    @Redirect(method = {"onCloneTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            "onCloneDamaged(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
            "onCloneDeath(Lnet/minecraftforge/event/entity/living/LivingDeathEvent;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;", ordinal = 0),
            require = 3, allow = 3)
    private static CompoundTag bons$fallentraitsCoo49ClonePeek(LivingEntity entity) {
        return FtPeek.readOnly(entity);
    }
}
