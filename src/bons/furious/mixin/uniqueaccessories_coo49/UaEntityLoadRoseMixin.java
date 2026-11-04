package bons.furious.mixin.uniqueaccessories_coo49;

import bons.furious.patch.uniqueaccessories_coo49.UaRoseCandidates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * uniqueaccessories_rose_cleanup_candidates (Unique Accessories 1.3.3; server side): @Inject at RETURN of Entity.load
 * (m_20258_), after Forge has read ForgeData: a mob that comes back with a Rose of Temptation boost value is noted in its
 * level's candidate set (UaRoseCandidates.loaded: one field read and, for mobs with ForgeData, one key lookup). Also the
 * per-entity mark "the candidate set I am in" (UaRoseCandidates.Mark: one reference field, written only under that set's
 * lock), so a repeated boost of a lured mob costs two field reads. Applied only where Unique Accessories is installed (the
 * switch's guard lines).
 */
@Mixin(value = Entity.class, remap = false)
public abstract class UaEntityLoadRoseMixin implements UaRoseCandidates.Mark {
    @Unique
    private Object bons$uniqueaccessoriesCoo49RoseMarkField;

    @Override
    public Object bons$uniqueaccessoriesCoo49RoseMark() {
        return this.bons$uniqueaccessoriesCoo49RoseMarkField;
    }

    @Override
    public void bons$uniqueaccessoriesCoo49SetRoseMark(Object set) {
        this.bons$uniqueaccessoriesCoo49RoseMarkField = set;
    }

    @Inject(method = "m_20258_(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), require = 1, remap = false)
    private void bons$uniqueaccessoriesCoo49RoseLoaded(CompoundTag tag, CallbackInfo ci) {
        UaRoseCandidates.loaded((Entity) (Object) this);
    }
}
