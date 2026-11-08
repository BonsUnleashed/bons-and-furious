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
 * deeperdarker_sculk_jaw_single_bite (gameplay fix, off by default; Deeper and Darker 1.4.1 for NeoForge 1.21.1): HEAD of
 * SculkJawBlock.entityInside returns while the jaw is biting, so an entity that fell into the open jaw is bitten once (by
 * stepOn) instead of on every tick of the bite. See DeeperDarkerFixes.
 *
 * Ported to 1.21.1: a bite is the 3 bite damage and, for a player, SculkJawBlockEntity.stealXP(player, 4); entityInside
 * repeats both on every tick inside the biting jaw (the experience is not limited by the damage cooldown), stepOn does them
 * once and schedules the 35-tick close.
 */
@Mixin(value = SculkJawBlock.class, remap = false)
public abstract class SculkJawSingleBiteMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void bons$jawBitesOnce(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (DeeperDarkerFixes.jawBiting(state)) ci.cancel();
    }
}
