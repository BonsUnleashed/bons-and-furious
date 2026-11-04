package bons.furious.mixin.immersiveweathering_c49;

import bons.furious.patch.immersiveweathering_c49.AreaScan;
import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * immersiveweathering_area_scan_table (Immersive Weathering 1.20.1-2.0.5, LGPLv3; server side): a cancellable HEAD inject
 * on AreaCheck.test answers from AreaScan (the same positions in the same shuffled order, the same reads, RuleTest calls
 * and early returns) with the box's offsets kept on the AreaCheck (its radii are final record components).
 */
@Mixin(targets = "com.ordana.immersive_weathering.data.block_growths.area_condition.AreaCheck", remap = false)
public abstract class AreaCheckTableMixin {
    @Shadow @Final private int rX;
    @Shadow @Final private int rY;
    @Shadow @Final private int rZ;
    @Shadow @Final private int requiredAmount;
    @Shadow @Final private Optional<Integer> yOffset;
    @Shadow @Final private Optional<RuleTest> mustHavePredicate;
    @Shadow @Final private Optional<RuleTest> mustNotHavePredicate;
    @Shadow @Final private Optional<HolderSet<Block>> extraIncluded;

    @Unique
    private volatile int[] bons$iwC49AreaOffsets;

    @Inject(method = "test(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lcom/ordana/immersive_weathering/data/block_growths/growths/ConfigurableBlockGrowth;)Z",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$iwC49AreaTest(BlockPos pos, Level level, ConfigurableBlockGrowth config, CallbackInfoReturnable<Boolean> cir) {
        if (!AreaScan.enabled) return;
        int[] offsets = this.bons$iwC49AreaOffsets;
        if (offsets == null) this.bons$iwC49AreaOffsets = offsets = AreaScan.offsets(this.rX, this.rY, this.rZ);
        cir.setReturnValue(AreaScan.test(offsets, pos, level, config, this.requiredAmount, this.yOffset, this.mustHavePredicate,
                this.mustNotHavePredicate, this.extraIncluded));
    }
}
