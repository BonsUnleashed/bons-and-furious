package bons.furious.mixin.deeperdarker_coo49;

import bons.furious.patch.deeperdarker_coo49.DeeperDarkerFixes;
import com.kyanite.deeperdarker.content.blocks.SculkJawBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * deeperdarker_sculk_jaw_single_bite (gameplay fix, off by default; Deeper and Darker 1.3.3): HEAD of
 * SculkJawBlock.entityInside returns while the jaw is biting, so an entity that fell into the open jaw is bitten once (by
 * stepOn) instead of on every tick of the bite. See DeeperDarkerFixes. COO 5.8 hooks the same method
 * (deeperdarker.MixinSculkJawBlock, option deeperdarker.fixSculkJawRepeatBite).
 */
@Mixin(value = SculkJawBlock.class, remap = false)
public abstract class SculkJawSingleBiteMixin {
    @Inject(method = "m_7892_", at = @At("HEAD"), cancellable = true)
    private void bons$jawBitesOnce(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (DeeperDarkerFixes.jawBiting(state)) ci.cancel();
    }
}
