package bons.furious.mixin.benssharks;

import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** benssharks_prey_search_merge: a TargetGoal's mustSee, mustReach and unseen-memory settings. */
@Mixin(value = TargetGoal.class, remap = false)
public interface TargetGoalAccessor {
    @Accessor(value = "f_26136_", remap = false)
    boolean bons$sharksMustSee();

    @Accessor(value = "f_26131_", remap = false)
    boolean bons$sharksMustReach();

    @Accessor(value = "f_26138_", remap = false)
    int bons$sharksUnseenMemory();
}
