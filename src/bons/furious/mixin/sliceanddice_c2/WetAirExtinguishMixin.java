package bons.furious.mixin.sliceanddice_c2;

import bons.furious.patch.sliceanddice_c2.WetAirGate;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * sliceanddice_wet_air_gate (Slice & Dice 3.6.0, both sides; the gate answers on the server thread), part 2 of 3.
 *
 * Slice & Dice's EntityMixin.baseTick handler (merged into Entity, HEAD of baseTick) reads the block at every entity's
 * position each tick and calls clearFire() when it is wet air. This redirect skips that Level.getBlockState when
 * WetAirGate proves from the loaded chunk's section that the block cannot be wet air, and hands the handler AIR instead
 * (the handler only compares the state with wet air, so it does nothing, as it would for the real block); otherwise the
 * read happens exactly as before. See WetAirGate for the proof and the documented internal difference.
 */
@Mixin(value = Entity.class, priority = 1500, remap = false)
public abstract class WetAirExtinguishMixin {
    @TargetHandler(mixin = "com.possible_triangle.sliceanddice.mixins.EntityMixin", name = "baseTick")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState bons$gatedWetAirRead(Level level, BlockPos pos) {
        if (WetAirGate.cellHidden(level, pos)) {
            if (WetAirGate.SHADOW) WetAirGate.shadowCell(level, pos);
            return WetAirGate.NOT_WET;
        }
        return level.m_8055_(pos);
    }
}
