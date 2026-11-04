package bons.furious.mixin.persistentpeek_coo49;

import bons.furious.patch.persistentpeek_coo49.PersistentPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * mobgrindingutils_chicken_tag_peek (Mob Grinding Utils 1.1.0; server half): @Redirect of the FIRST getPersistentData() of
 * ChickenFuseEvent.startChickenFuse (every living event of every chicken on a server level), whose compound is asked
 * contains("shouldExplode") and the method returns when false; every later read or write sits behind that test, which
 * only an existing compound can pass: the entity's own compound, or a shared empty one that nothing writes when it has
 * none (PersistentPeek.chicken). Our own logic only.
 */
@Mixin(targets = "mob_grinding_utils.events.ChickenFuseEvent", remap = false)
public abstract class PpChickenFuseMixin {
    @Redirect(method = "startChickenFuse(Lnet/minecraftforge/event/entity/living/LivingEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;", ordinal = 0),
            require = 1, allow = 1)
    private CompoundTag bons$persistentpeekCoo49FusePeek(LivingEntity entity) {
        return PersistentPeek.chicken(entity);
    }
}
