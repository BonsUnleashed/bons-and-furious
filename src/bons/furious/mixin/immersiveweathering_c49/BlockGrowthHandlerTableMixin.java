package bons.furious.mixin.immersiveweathering_c49;

import bons.furious.patch.immersiveweathering_c49.GrowthTable;
import com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler;
import com.ordana.immersive_weathering.data.block_growths.TickSource;
import com.ordana.immersive_weathering.data.block_growths.growths.IBlockGrowth;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * immersiveweathering_growth_table (+ immersiveweathering_growth_roll_batch) (Immersive Weathering 1.20.1-2.0.5, LGPLv3;
 * server side): a cancellable HEAD inject on BlockGrowthHandler.tickBlock runs the call from GrowthTable's per-source table
 * (the same growths in the same order, without the entries that cannot do anything) and cancels; GrowthTable answers
 * false, before doing anything, for the arguments Immersive Weathering's own body handles (then it runs as shipped).
 */
@Mixin(value = BlockGrowthHandler.class, remap = false)
public abstract class BlockGrowthHandlerTableMixin {
    @Shadow
    @Final
    private static Map<TickSource, Map<Block, Set<IBlockGrowth>>> GROWTH_FOR_BLOCK;
    @Shadow
    @Final
    private static Map<TickSource, Set<IBlockGrowth>> UNIVERSAL_GROWTHS;

    @Inject(method = "tickBlock(Lcom/ordana/immersive_weathering/data/block_growths/TickSource;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$iwC49TickBlock(TickSource source, BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (GrowthTable.enabled && source != null
                && GrowthTable.tick(source, state, level, pos, UNIVERSAL_GROWTHS.get(source), GROWTH_FOR_BLOCK.get(source))) ci.cancel();
    }
}
