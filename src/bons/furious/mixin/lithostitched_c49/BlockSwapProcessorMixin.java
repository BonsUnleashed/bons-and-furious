package bons.furious.mixin.lithostitched_c49;

import bons.furious.patch.lithostitched_c49.SwapTable;
import dev.worldgen.lithostitched.worldgen.processor.BlockSwapStructureProcessor;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lithostitched_block_swap_table (Lithostitched 1.4.11, MIT; server side): a cancellable HEAD inject on
 * BlockSwapStructureProcessor.processBlock (m_7382_) answers from the processor's block table (SwapTable, built on the first
 * call and published through a volatile field): the same StructureBlockInfo (swapped: same position, target block with the
 * properties of the current state, same NBT object; not swapped: the current info object itself). A map that keeps
 * Lithostitched's own path (see SwapTable) and the switch off fall through to Lithostitched's body. Shadow mode: the body
 * runs and its answer is compared at RETURN.
 */
@Mixin(value = BlockSwapStructureProcessor.class, remap = false)
public abstract class BlockSwapProcessorMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, ResourceLocation> blockSwapMap;

    @Unique
    private volatile SwapTable bons$lsTable;

    @Inject(method = "m_7382_", at = @At("HEAD"), cancellable = true)
    private void bons$lsSwap(LevelReader level, BlockPos offset, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
                             StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                             CallbackInfoReturnable<StructureTemplate.StructureBlockInfo> cir) {
        if (!SwapTable.enabled || SwapTable.SHADOW) return;
        SwapTable t = this.bons$lsTable;
        if (t == null) this.bons$lsTable = t = SwapTable.build(this.blockSwapMap);
        if (t == SwapTable.STOCK) return;
        Block target = t.target(current.f_74676_().m_60734_());
        cir.setReturnValue(target == null ? current
                : new StructureTemplate.StructureBlockInfo(current.f_74675_(), target.m_152465_(current.f_74676_()), current.f_74677_()));
    }

    @Inject(method = "m_7382_", at = @At("RETURN"))
    private void bons$lsShadow(LevelReader level, BlockPos offset, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
                               StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                               CallbackInfoReturnable<StructureTemplate.StructureBlockInfo> cir) {
        if (!SwapTable.SHADOW || !SwapTable.enabled) return;
        SwapTable t = this.bons$lsTable;
        if (t == null) this.bons$lsTable = t = SwapTable.build(this.blockSwapMap);
        if (t == SwapTable.STOCK) return;
        Block target = t.target(current.f_74676_().m_60734_());
        Object ours = target == null ? current
                : new StructureTemplate.StructureBlockInfo(current.f_74675_(), target.m_152465_(current.f_74676_()), current.f_74677_());
        SwapTable.shadow(cir.getReturnValue(), ours, String.valueOf(current.f_74675_()));
    }
}
