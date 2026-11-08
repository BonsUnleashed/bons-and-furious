package bons.furious.mixin.supplementaries_lean;

import bons.furious.patch.supplementaries_lean.MapTintSlot;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * supplementaries_map_tint_memo (Minecraft 1.21.1, both sides): one memo slot per Block for Supplementaries' map tint
 * lookup (bons.furious.patch.supplementaries_lean.MapTints). Adds a field and its two accessors, nothing else; no
 * Minecraft code is carried.
 */
@Mixin(value = Block.class, remap = false)
public abstract class BlockMapTintSlotMixin implements MapTintSlot {
    @Unique
    private Object bons$mapTint;

    @Override
    public Object bons$mapTintMemo() {
        return this.bons$mapTint;
    }

    @Override
    public void bons$setMapTintMemo(Object memo) {
        this.bons$mapTint = memo;
    }
}
