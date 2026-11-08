package bons.furious.mixin.lithostitched_c49;

import bons.furious.patch.lithostitched_c49.SwapTable;
import dev.worldgen.lithostitched.impl.worldgen.processor.BlockSwapStructureProcessor;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
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
 * lithostitched_block_swap_table (Lithostitched 1.8.1 for NeoForge 1.21.1, MIT; server side): a cancellable HEAD inject on
 * BlockSwapStructureProcessor.processBlock answers, for a ServerLevel or WorldGenRegion, from the processor's block table
 * (SwapTable, built on the first call and published through a volatile field): the same StructureBlockInfo (swapped: same
 * position, target block with the properties of the current state, same NBT object; not swapped: the current info object
 * itself). Other levels, a block with an unbound registry holder, a map that keeps Lithostitched's own path (see SwapTable)
 * and the switch off fall through to Lithostitched's body. Shadow mode: the body runs and its answer is compared at RETURN.
 */
@Mixin(value = BlockSwapStructureProcessor.class, remap = false)
public abstract class BlockSwapProcessorMixin {
    @Shadow
    @Final
    private Map<ResourceKey<Block>, ResourceKey<Block>> blockSwapMap;

    @Unique
    private volatile SwapTable bons$lsTable;

    @Unique
    private SwapTable bons$lsBuiltTable() {
        SwapTable t = this.bons$lsTable;
        if (t == null) this.bons$lsTable = t = SwapTable.build(this.blockSwapMap);
        return t;
    }

    @Inject(method = "processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;",
            at = @At("HEAD"), cancellable = true)
    private void bons$lsSwap(LevelReader level, BlockPos offset, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
                             StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                             CallbackInfoReturnable<StructureTemplate.StructureBlockInfo> cir) {
        if (!SwapTable.enabled || SwapTable.SHADOW || !SwapTable.serverRegistries(level)) return;
        SwapTable t = this.bons$lsBuiltTable();
        if (t == SwapTable.STOCK) return;
        Block block = current.state().getBlock();
        Block target = t.target(block);
        if (target != null) {
            cir.setReturnValue(new StructureTemplate.StructureBlockInfo(current.pos(), target.withPropertiesOf(current.state()), current.nbt()));
        } else if (block.builtInRegistryHolder().isBound()) {
            cir.setReturnValue(current);
        }
    }

    @Inject(method = "processBlock(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;",
            at = @At("RETURN"))
    private void bons$lsShadow(LevelReader level, BlockPos offset, BlockPos pivot, StructureTemplate.StructureBlockInfo original,
                               StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings,
                               CallbackInfoReturnable<StructureTemplate.StructureBlockInfo> cir) {
        if (!SwapTable.SHADOW || !SwapTable.enabled || !SwapTable.serverRegistries(level)) return;
        SwapTable t = this.bons$lsBuiltTable();
        if (t == SwapTable.STOCK) return;
        Block target = t.target(current.state().getBlock());
        Object ours = target == null ? current
                : new StructureTemplate.StructureBlockInfo(current.pos(), target.withPropertiesOf(current.state()), current.nbt());
        SwapTable.shadow(cir.getReturnValue(), ours, String.valueOf(current.pos()));
    }
}
