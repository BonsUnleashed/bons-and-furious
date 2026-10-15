package bons.furious.mixin.benssharks;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** benssharks_prey_search_merge: the mob's target selector (Mob.targetSelector). */
@Mixin(value = Mob.class, remap = false)
public interface MobAccessor {
    @Accessor(value = "f_21346_", remap = false)
    GoalSelector bons$sharksTargetSelector();
}
