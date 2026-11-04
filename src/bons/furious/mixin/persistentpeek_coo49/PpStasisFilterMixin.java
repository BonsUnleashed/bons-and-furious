package bons.furious.mixin.persistentpeek_coo49;

import bons.furious.patch.persistentpeek_coo49.PersistentPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * industrialforegoing_stasis_filter_peek (Industrial Foregoing 3.5.22; both sides): @Redirect of the getPersistentData()
 * call in the Stasis Chamber's LivingTickEvent filter ModuleMisc.lambda$generateFeatures$21 (entity instanceof Mob &&
 * entity.getPersistentData().contains("StasisChamberTime")), whose compound is only asked that one question: the entity's
 * own compound, or null when it has none (PersistentPeek.stasis), and @Redirect of that contains(key), which answers false
 * for null (PersistentPeek.has). Our own logic only.
 */
@Mixin(targets = "com.buuz135.industrial.module.ModuleMisc", remap = false)
public abstract class PpStasisFilterMixin {
    @Redirect(method = "lambda$generateFeatures$21(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;"),
            require = 1, allow = 1)
    private static CompoundTag bons$persistentpeekCoo49StasisPeek(LivingEntity entity) {
        return PersistentPeek.stasis(entity);
    }

    @Redirect(method = "lambda$generateFeatures$21(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;m_128441_(Ljava/lang/String;)Z", ordinal = 0),
            require = 1, allow = 1)
    private static boolean bons$persistentpeekCoo49StasisHas(CompoundTag tag, String key) {
        return PersistentPeek.has(tag, key);
    }
}
