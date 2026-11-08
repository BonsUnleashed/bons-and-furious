package bons.furious.mixin.adamsarsplus_coo49;

import bons.furious.patch.adamsarsplus_coo49.AapEffects;
import bons.furious.patch.adamsarsplus_coo49.AapPurgeTarget;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * adamsarsplus_disruption_clean_purge (gameplay fix, off by default; Minecraft 1.21.1 LivingEntity on NeoForge, no code
 * carried): a victim marked by AapDisruptionPurgeMixin loses every beneficial effect right after tickEffects' loop over the
 * effect map (nothing is removed while that loop iterates), at the first read of effectsDirty, so the effects-changed
 * update that follows (visibility, glowing, particle colour) sees the purge in the same tick. One boolean per living entity;
 * the members carry the group's name.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class AapPurgeTailMixin implements AapPurgeTarget {
    @Unique
    private boolean bons$adamsarsplusPurgePending;

    @Override
    public void bons$adamsarsplusMarkPurge() {
        this.bons$adamsarsplusPurgePending = true;
    }

    @Inject(method = "tickEffects()V", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;effectsDirty:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void bons$adamsarsplusPurgeAfterEffectLoop(CallbackInfo ci) {
        if (this.bons$adamsarsplusPurgePending) {
            this.bons$adamsarsplusPurgePending = false;
            AapEffects.purgeNow((LivingEntity) (Object) this);
        }
    }
}
