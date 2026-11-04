package bons.furious.mixin.cataclysm_c49;

import bons.furious.patch.cataclysm_c49.MissileFuse;
import com.github.L_Ender.cataclysm.entity.projectile.Wither_Homing_Missile_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_missile_fuse_unsynced (L_Ender's Cataclysm 3.16 / 3.31; server; since 1.0.49; a trade-off, off by default): on
 * server levels the tick's getFuse / setFuse and the save's getFuse use the missile's own countdown (MissileFuse); the synced
 * fuse is read to start it and to notice outside changes, and is no longer written by the countdown. Our own logic only.
 */
@Mixin(value = Wither_Homing_Missile_Entity.class, remap = false)
public abstract class MissileFuseMixin {
    @Unique
    private int bons$cataclysmFuse = Integer.MIN_VALUE;
    @Unique
    private int bons$cataclysmFuseSynced;

    @WrapOperation(method = {"m_8119_()V", "m_7380_(Lnet/minecraft/nbt/CompoundTag;)V"},
            at = @At(value = "INVOKE", target = "Lcom/github/L_Ender/cataclysm/entity/projectile/Wither_Homing_Missile_Entity;getFuse()I", remap = false),
            require = 2, allow = 2, remap = false)
    private int bons$fuseRead(Wither_Homing_Missile_Entity missile, Operation<Integer> original) {
        int synced = original.call(missile);
        if (!MissileFuse.enabled || ((Entity) (Object) this).m_9236_().f_46443_) return synced;
        if (this.bons$cataclysmFuse == Integer.MIN_VALUE || synced != this.bons$cataclysmFuseSynced) {
            this.bons$cataclysmFuse = synced;
            this.bons$cataclysmFuseSynced = synced;
        }
        return this.bons$cataclysmFuse;
    }

    @WrapOperation(method = "m_8119_()V",
            at = @At(value = "INVOKE", target = "Lcom/github/L_Ender/cataclysm/entity/projectile/Wither_Homing_Missile_Entity;setFuse(I)V", remap = false),
            require = 1, allow = 1, remap = false)
    private void bons$fuseWrite(Wither_Homing_Missile_Entity missile, int fuse, Operation<Void> original) {
        if (!MissileFuse.enabled || ((Entity) (Object) this).m_9236_().f_46443_ || this.bons$cataclysmFuse == Integer.MIN_VALUE) {
            original.call(missile, fuse);
            return;
        }
        this.bons$cataclysmFuse = fuse;
        MissileFuse.announce();
    }
}
