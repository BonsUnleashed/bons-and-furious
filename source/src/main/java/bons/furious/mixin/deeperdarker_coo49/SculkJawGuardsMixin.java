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
 * deeperdarker_sculk_jaw_inside_guards (gameplay fix, off by default; Deeper and Darker 1.4.1 for NeoForge 1.21.1): HEAD of
 * SculkJawBlock.entityInside returns for creative or crouching players and monsters of the deeperdarker:sculk entity type
 * tag, the cases stepOn already spares. See DeeperDarkerFixes.
 *
 * Ported to 1.21.1: stepOn spares sculk monsters by the entity type tag DDTags.Misc.SCULK (DDMobType.SCULK in 1.3.3), and
 * entityInside now also takes 4 experience from a player into the jaw on each bite; the guards spare both.
 */
@Mixin(value = SculkJawBlock.class, remap = false)
public abstract class SculkJawGuardsMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void bons$jawSparesExempt(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (DeeperDarkerFixes.jawExempt(entity)) ci.cancel();
    }
}
