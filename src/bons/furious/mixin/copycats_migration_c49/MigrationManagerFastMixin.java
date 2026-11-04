package bons.furious.mixin.copycats_migration_c49;

import bons.furious.patch.copycats_migration_c49.MigrationFastPath;
import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.MigrationManager;
import com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateCopycatBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * copycats_migration_fast_path (Copycats+ 3.0.10, All Rights Reserved; both sides): cancellable HEAD injects on
 * MigrationManager.migrateBlockEntity (returns the block entity when its type is not create:copycat) and
 * MigrationManager.migrateStructure (returns the info when its block is neither a MultiStateCopycatBlock nor a
 * CCCopycatBlock). Why both answers equal Copycats+' own: MigrationFastPath.
 */
@Mixin(value = MigrationManager.class, remap = false)
public abstract class MigrationManagerFastMixin {
    @Inject(method = "migrateBlockEntity(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$copycatsC49BlockEntity(LevelChunk chunk, BlockEntity blockEntity, CallbackInfoReturnable<BlockEntity> cir) {
        if (MigrationFastPath.enabled && blockEntity != null && MigrationFastPath.notCopycat(blockEntity.m_58903_())) cir.setReturnValue(blockEntity);
    }

    @Inject(method = "migrateStructure(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$copycatsC49Structure(StructureTemplate.StructureBlockInfo info, CallbackInfoReturnable<StructureTemplate.StructureBlockInfo> cir) {
        if (!MigrationFastPath.enabled || info == null) return;
        BlockState state = info.f_74676_();
        if (state == null) return;
        Block block = state.m_60734_();
        if (!(block instanceof MultiStateCopycatBlock) && !(block instanceof CCCopycatBlock)) cir.setReturnValue(info);
    }
}
