package bons.furious.mixin.blockswap_c49;

import bons.furious.patch.blockswap_c49.RetroScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import corgitaco.blockswap.config.BlockSwapConfig;
import corgitaco.blockswap.swapper.Swapper;
import corgitaco.blockswap.util.TickHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * blockswap_retrogen_palette_scan (Block Swap 5.0.0.0, LGPLv3; server): MixinExtras @WrapOperation on the
 * TickHelper.markTickDirty() call in Swapper.runRetroGenerator, which Block Swap evaluates only when retro_gen is on (so
 * nothing changes, and nothing runs, with retro_gen off). When the chunk is not yet marked, RetroScan runs the walk
 * (eligible chunks only), marks the chunk as Block Swap's code after its loop does, and answers true so Block Swap's own
 * loop is skipped; otherwise Block Swap's answer is returned and its loop runs. The method's arguments and its config
 * local are read with MixinExtras @Local.
 */
@Mixin(value = Swapper.class, remap = false)
public abstract class RetroGenScanMixin {
    @WrapOperation(method = "runRetroGenerator", at = @At(value = "INVOKE", target = "Lcorgitaco/blockswap/util/TickHelper;markTickDirty()Z"))
    private static boolean bons$bsRetroScan(TickHelper helper, Operation<Boolean> original,
                                            @Local(argsOnly = true) Level world, @Local(argsOnly = true) LevelChunkSection[] sections,
                                            @Local(argsOnly = true) LevelChunk chunk, @Local(ordinal = 0) BlockSwapConfig config) {
        boolean dirty = original.call(helper);
        if (dirty || !RetroScan.run(world, sections, chunk, config)) return dirty;
        helper.setTickDirty();
        return true;
    }
}
