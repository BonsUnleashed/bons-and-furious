package bons.furious.mixin.benssharks;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** benssharks_prey_search_merge: a NearestAttackableTargetGoal's prey class, random interval and targeting conditions. */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public interface NatgAccessor {
    @Accessor(value = "f_26048_", remap = false)
    Class<? extends LivingEntity> bons$sharksPreyType();

    @Accessor(value = "f_26049_", remap = false)
    int bons$sharksPreyInterval();

    @Accessor(value = "f_26051_", remap = false)
    TargetingConditions bons$sharksPreyConditions();
}
