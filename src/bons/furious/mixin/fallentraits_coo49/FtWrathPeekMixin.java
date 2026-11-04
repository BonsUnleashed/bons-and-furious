package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * fallentraits_unset_persistent_data_skip (Fallen Traits & Relics 1.0.0; server side): @Redirect of the
 * entity.getPersistentData() of WrathOfFenrir.restoreExpiredTraits, whose answer is first asked
 * contains("ft.wrath_disabled_traits", 9) and the method returns when false: the existing compound, or a shared empty one
 * that nothing writes when the entity has none (FtPeek). Our own logic only.
 */
@Mixin(targets = "net.kayn.fallen_traits.content.item.curio.WrathOfFenrir", remap = false)
public abstract class FtWrathPeekMixin {
    @Redirect(method = "restoreExpiredTraits(Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;", ordinal = 0),
            require = 1, allow = 1)
    private static CompoundTag bons$fallentraitsCoo49WrathPeek(LivingEntity entity) {
        return FtPeek.readOnly(entity);
    }
}
