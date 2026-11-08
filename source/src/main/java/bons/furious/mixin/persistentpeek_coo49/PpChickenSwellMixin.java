package bons.furious.mixin.persistentpeek_coo49;

import bons.furious.patch.persistentpeek_coo49.PpChickenPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * mobgrindingutils_chicken_tag_peek (Mob Grinding Utils 1.1.10+mc1.21.1; client half): @Redirect of the FIRST
 * getPersistentData() of RenderChickenSwell.renderChickenSwell (every rendered chicken), whose compound is only asked
 * contains("shouldExplode") before the method returns when false: the entity's own compound, or null when it has none
 * (PpChickenPeek.swell), and @Redirect of that first contains(key), which answers false for null (PpChickenPeek.has). Our
 * own logic only.
 */
@Mixin(targets = "mob_grinding_utils.events.RenderChickenSwell", remap = false)
public abstract class PpChickenSwellMixin {
    @Redirect(method = "renderChickenSwell(Lnet/neoforged/neoforge/client/event/RenderLivingEvent$Post;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;", ordinal = 0),
            require = 1, allow = 1)
    private CompoundTag bons$persistentpeekCoo49SwellPeek(LivingEntity entity) {
        return PpChickenPeek.swell(entity);
    }

    @Redirect(method = "renderChickenSwell(Lnet/neoforged/neoforge/client/event/RenderLivingEvent$Post;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;contains(Ljava/lang/String;)Z", ordinal = 0),
            require = 1, allow = 1)
    private boolean bons$persistentpeekCoo49SwellHas(CompoundTag tag, String key) {
        return PpChickenPeek.has(tag, key);
    }
}
