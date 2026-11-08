package bons.furious.mixin.persistentpeek_coo49;

import bons.furious.patch.persistentpeek_coo49.PpChickenPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * mobgrindingutils_chicken_tag_peek (Mob Grinding Utils 1.1.10+mc1.21.1; server half): @Redirect of the FIRST
 * getPersistentData() of ChickenFuseEvent.startChickenFuse (an EntityTickEvent.Post listener: every tick of every chicken
 * on a server level reaches it), whose compound is asked contains("shouldExplode") and the method returns when false; every
 * later read or write sits behind that test, which only an existing compound can pass: the entity's own compound, or a
 * shared empty one that nothing writes when it has none (PpChickenPeek.chicken). Our own logic only.
 */
@Mixin(targets = "mob_grinding_utils.events.ChickenFuseEvent", remap = false)
public abstract class PpChickenFuseMixin {
    @Redirect(method = "startChickenFuse(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Post;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;", ordinal = 0),
            require = 1, allow = 1)
    private CompoundTag bons$persistentpeekCoo49FusePeek(Entity entity) {
        return PpChickenPeek.chicken(entity);
    }
}
