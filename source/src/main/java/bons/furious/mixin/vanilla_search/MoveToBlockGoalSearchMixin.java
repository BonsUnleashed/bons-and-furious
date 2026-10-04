package bons.furious.mixin.vanilla_search;

import bons.furious.patch.vanilla_search.TurtleEggSearch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_turtle_egg_search_sections (Minecraft 1.21.1 with NeoForge 21.1.252; server side): wraps
 * MoveToBlockGoal.findNearestBlock. For RemoveBlockGoal searches that TurtleEggSearch.eligible accepts (decided once per
 * goal class and mob class), TurtleEggSearch.search visits the same positions in the same order, makes the same getChunk
 * calls and runs the goal's own isValidTarget wherever a section may hold the target block or a block with its own
 * canEntityDestroy; sections that cannot are skipped without reading their blocks (isValidTarget is a pure false there).
 * The found position is stored exactly as the original stores it (the search's own MutableBlockPos). Every other goal,
 * and every search the switch cannot prove, runs the original. Our own logic only; no Minecraft code is carried.
 *
 * Ported to 1.21.1: Mojang names; findNearestBlock and RemoveBlockGoal.isValidTarget have the same bodies; Forge's
 * canEntityDestroy extension is NeoForge's IBlockExtension / IBlockStateExtension (same name and descriptor).
 */
@Mixin(value = MoveToBlockGoal.class, remap = false)
public abstract class MoveToBlockGoalSearchMixin implements TurtleEggSearch.Goal {
    @Shadow
    @Final
    protected PathfinderMob mob;
    @Shadow
    @Final
    private int searchRange;
    @Shadow
    @Final
    private int verticalSearchRange;
    @Shadow
    protected int verticalSearchStart;
    @Shadow
    protected BlockPos blockPos;

    @Shadow
    protected abstract boolean isValidTarget(LevelReader level, BlockPos pos);

    @WrapMethod(method = "findNearestBlock")
    private boolean bons$findNearestBlock(Operation<Boolean> original) {
        Object self = this;
        if (!TurtleEggSearch.enabled || !(self instanceof RemoveBlockGoal) || !TurtleEggSearch.eligible((MoveToBlockGoal) self, this.mob)) {
            return original.call();
        }
        BlockPos found = TurtleEggSearch.search(this, this.mob, this.searchRange, this.verticalSearchRange, this.verticalSearchStart,
                ((RemoveBlockGoalAccessor) self).bons$blockToRemove());
        if (found == TurtleEggSearch.FALLBACK) return original.call();
        TurtleEggSearch.FAST.incrementAndGet();
        TurtleEggSearch.announce();
        if (TurtleEggSearch.SHADOW) {
            boolean originalFound = original.call();
            TurtleEggSearch.shadow(found, originalFound, originalFound ? this.blockPos : null);
            return originalFound;
        }
        if (found == null) return false;
        this.blockPos = found;
        return true;
    }

    @Override
    public boolean bons$isValidTarget(LevelReader level, BlockPos pos) {
        return this.isValidTarget(level, pos);
    }
}
