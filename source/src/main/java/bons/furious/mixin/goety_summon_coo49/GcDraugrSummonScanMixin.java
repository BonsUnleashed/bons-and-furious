package bons.furious.mixin.goety_summon_coo49;

import bons.furious.patch.goety_summon_coo49.GoetySummonScans;
import com.Polarice3.Goety.common.entities.neutral.AbstractNecromancer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * goety_cataclysm_summon_goal_scan_after_check (Goety Cataclysm 1.21.1-1.8.4, all rights reserved: no code carried; server;
 * exact). AbstractDraugrNecromancer$SummonServantSpell.canUse searches every LivingEntity within 64 / 16 / 64 blocks before
 * it asks the parent test and answers {@code super.canUse() && count < 6}; while the flag is on, the parent test is asked
 * first (it only reads, and so does the search), its answer is kept for the goal's own super.canUse() call (the second
 * wrapper), and when it answers false the search is marked UNUSED so that vanilla_entity_walk_skip leaves out its walk (the
 * call itself runs); canUse answers false as before. See GoetySummonScans.
 */
@Mixin(targets = "com.Polarice3.goety_cataclysm.common.entities.neutral.AbstractDraugrNecromancer$SummonServantSpell", remap = false)
public abstract class GcDraugrSummonScanMixin extends AbstractNecromancer.SummoningSpellGoal {
    /** The parent test's answer asked before this canUse's search: 1 true, -1 false, 0 none kept (flag off). */
    @Unique
    private int bons$goetySummonDraugrParent;

    GcDraugrSummonScanMixin(AbstractNecromancer outer) {
        outer.super();
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "canUse()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$goetycataclysmDraugrSummonScan(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        if (!GoetySummonScans.cataclysm) {
            bons$goetySummonDraugrParent = 0;
            return original.call(level, searched, box, predicate);
        }
        boolean parent = super.canUse();
        bons$goetySummonDraugrParent = parent ? 1 : -1;
        // the parent test answered true: the search runs as shipped, called right here
        if (parent) return original.call(level, searched, box, predicate);
        return GoetySummonScans.idle(level, searched, box, predicate, original);
    }

    /**
     * The goal's own super.canUse() after the search: the answer asked before it (nothing between them writes). A super call's
     * receiver is the target class itself; @Coerce takes it as the shared parent type.
     */
    @WrapOperation(method = "canUse()Z",
            at = @At(value = "INVOKE", target = "Lcom/Polarice3/Goety/common/entities/neutral/AbstractNecromancer$SummoningSpellGoal;canUse()Z"),
            require = 1, allow = 1)
    private boolean bons$goetycataclysmDraugrParentTest(@Coerce AbstractNecromancer.SummoningSpellGoal self, Operation<Boolean> original) {
        int kept = bons$goetySummonDraugrParent;
        bons$goetySummonDraugrParent = 0;
        if (kept != 0 && GoetySummonScans.cataclysm) return kept > 0;
        return original.call(self);
    }
}
