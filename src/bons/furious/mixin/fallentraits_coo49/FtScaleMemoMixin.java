package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtScaleMemo;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * fallentraits_render_scale_frame_memo (client side; trade-off, off by default): two fields on LivingEntity keep the last
 * Fallen Traits render scale with the entity tick it was taken in (FtScaleMemo; read and written only by FtScale while the
 * world renders). Adds no behaviour.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class FtScaleMemoMixin implements FtScaleMemo {
    @Unique
    private int bons$fallentraitsCoo49ScaleTickField;
    @Unique
    private float bons$fallentraitsCoo49ScaleField;

    @Override
    public int bons$fallentraitsCoo49ScaleTick() {
        return this.bons$fallentraitsCoo49ScaleTickField;
    }

    @Override
    public float bons$fallentraitsCoo49Scale() {
        return this.bons$fallentraitsCoo49ScaleField;
    }

    @Override
    public void bons$fallentraitsCoo49KeepScale(int tick, float scale) {
        this.bons$fallentraitsCoo49ScaleField = scale;
        this.bons$fallentraitsCoo49ScaleTickField = tick;
    }
}
