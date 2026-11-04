package bons.furious.mixin.mekanism_mop49;

import bons.furious.patch.mekanism_mop49.TransmitterBoxes;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * mekanism_transmitter_render_box_keep (Mekanism 10.4.13.69, client; since 1.0.49).
 *
 * TileEntityTransmitter.getRenderBoundingBox returns new AABB(worldPosition, worldPosition.offset(1, 1, 1)): a new BlockPos
 * and a new AABB on every call, and the renderer's culling calls it for every transmitter in view every frame.
 * worldPosition is final in BlockEntity (the constructor stores pos.immutable()) and AABB is immutable, so every call
 * builds an equal box: the first one is kept in a field of this transmitter and returned again (exact variant: the same
 * box object on every call instead of an equal new one; no caller keeps or compares box identity). A MixinExtras wrap of
 * the whole method, not an overwrite (Collections Of Optimizations overwrites the same method; priority 1100 lets this
 * wrap apply on top of such an overwrite as well).
 */
@Mixin(value = TileEntityTransmitter.class, priority = 1100, remap = false)
public abstract class TransmitterRenderBoxMixin {
    @Unique
    private AABB bons$mekanismMop49Box;

    @WrapMethod(method = "getRenderBoundingBox()Lnet/minecraft/world/phys/AABB;")
    private AABB bons$mekanismMop49KeepBox(Operation<AABB> original) {
        if (!TransmitterBoxes.enabled) return original.call();
        AABB kept = this.bons$mekanismMop49Box;
        if (kept == null) {
            kept = original.call();
            this.bons$mekanismMop49Box = kept;
            TransmitterBoxes.announce();
            return kept;
        }
        if (TransmitterBoxes.SHADOW) return TransmitterBoxes.shadow(kept, original.call(), this);
        return kept;
    }
}
