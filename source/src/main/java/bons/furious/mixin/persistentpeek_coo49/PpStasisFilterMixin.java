package bons.furious.mixin.persistentpeek_coo49;

import bons.furious.patch.persistentpeek_coo49.StasisPeek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * industrialforegoing_stasis_filter_peek (Industrial Foregoing, MIT; 1.21.1 tested build 1.21-3.6.39; both sides): a
 * redirect of the getPersistentData() call in the Stasis Chamber's EntityTickEvent.Pre filter
 * ModuleMisc.lambda$generateFeatures$21 (getEntity() instanceof Mob && getEntity().getPersistentData()
 * .contains("StasisChamberTime")), whose compound is only asked that one question: the entity's own compound, or null when
 * it has none (StasisPeek.stasis), and a redirect of that contains(key), which answers false for null (StasisPeek.has).
 * Our own logic only.
 *
 * Ported to 1.21.1: the filter listens to EntityTickEvent.Pre (was LivingTickEvent) and calls Entity.getPersistentData()
 * (was LivingEntity's); the compound is NeoForge's NeoForgeData.
 */
@Mixin(targets = "com.buuz135.industrial.module.ModuleMisc", remap = false)
public abstract class PpStasisFilterMixin {
    @Redirect(method = "lambda$generateFeatures$21(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;"),
            require = 1, allow = 1)
    private static CompoundTag bons$persistentpeekCoo49StasisPeek(Entity entity) {
        return StasisPeek.stasis(entity);
    }

    @Redirect(method = "lambda$generateFeatures$21(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;contains(Ljava/lang/String;)Z", ordinal = 0),
            require = 1, allow = 1)
    private static boolean bons$persistentpeekCoo49StasisHas(CompoundTag tag, String key) {
        return StasisPeek.has(tag, key);
    }
}
