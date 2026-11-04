package bons.furious.mixin.alexsmobs_crow_c49;

import bons.furious.patch.alexsmobs_crow_c49.CrowCropSearch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * alexsmobs_crow_crop_scan_palette_check (Alex's Mobs 1.22.9, LGPL; both sides, the goal runs on the server): wraps
 * MoveToBlockGoal.findNearestBlock (m_25626_). For Alex's Mobs' crow crop goal (exactly CrowAICircleCrops, which keeps
 * vanilla's findNearestBlock) CrowCropSearch.search visits the same positions in the same order, makes the same chunk
 * lookups and runs the goal's own isValidTarget wherever a chunk section may hold a crow food block; sections that cannot
 * are skipped without reading their blocks (isValidTarget is a pure false there). The found position is stored exactly as
 * the original stores it (the search's own MutableBlockPos). Every other goal, and every search the switch cannot prove,
 * runs the original. Our own logic only; no Alex's Mobs or Minecraft code is carried.
 */
@Mixin(value = MoveToBlockGoal.class, remap = false)
public abstract class CrowCropSearchMixin implements CrowCropSearch.Goal {
    @Shadow
    @Final
    protected PathfinderMob f_25598_;
    @Shadow
    @Final
    private int f_25606_;
    @Shadow
    @Final
    private int f_25607_;
    @Shadow
    protected int f_25603_;
    @Shadow
    protected BlockPos f_25602_;

    @Shadow
    protected abstract boolean m_6465_(LevelReader level, BlockPos pos);

    @WrapMethod(method = "m_25626_")
    private boolean bons$alexsmobs_crow_c49Find(Operation<Boolean> original) {
        if (!CrowCropSearch.eligible(this, this.f_25598_)) return original.call();
        BlockPos found = CrowCropSearch.search(this, this.f_25598_, this.f_25606_, this.f_25607_, this.f_25603_);
        if (found == CrowCropSearch.FALLBACK) return original.call();
        CrowCropSearch.FAST.incrementAndGet();
        CrowCropSearch.announce();
        if (CrowCropSearch.SHADOW) {
            boolean originalFound = original.call();
            CrowCropSearch.shadow(found, originalFound, originalFound ? this.f_25602_ : null);
            return originalFound;
        }
        if (found == null) return false;
        this.f_25602_ = found;
        return true;
    }

    @Override
    public boolean bons$alexsmobs_crow_c49Valid(LevelReader level, BlockPos pos) {
        return this.m_6465_(level, pos);
    }
}
