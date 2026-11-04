package bons.furious.mixin.subtleeffects_c49;

import bons.furious.patch.subtleeffects_c49.DisplayTickPlan;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * subtleeffects_display_tick_state_plan (Subtle Effects 1.15.1-hotfix.1, client): in ClientLevel.doAnimateTick (m_233612_),
 * right before Block.animateTick, the block state the method just read is remembered for Subtle Effects' handler at the
 * same point (DisplayTickPlan.capture). Priority 999: applied before Subtle Effects' mixin, so this callback runs first.
 * Our own logic only (an injection into Minecraft's method; no Mojang code is carried).
 */
@Mixin(value = ClientLevel.class, priority = 999, remap = false)
public abstract class DisplayTickStateCaptureMixin {
    @Inject(method = "m_233612_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;m_214162_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"),
            require = 0, expect = 0, allow = 1)
    private void bons$seCaptureSampledState(int x, int y, int z, int range, RandomSource random, Block markerBlock, BlockPos.MutableBlockPos pos,
                                            CallbackInfo ci, @Local(ordinal = 0) BlockState state) {
        DisplayTickPlan.capture(this, pos, state);
    }
}
