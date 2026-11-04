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
 * goety_summon_goal_scan_after_check (Goety 2.5.60.0, MIT: no code carried; server; exact). The five necromancer summon
 * goals (all direct subclasses of AbstractNecromancer$SummoningSpellGoal) search every LivingEntity around the necromancer
 * before they ask the parent test, and answer {@code super.canUse() && count < N}. While the flag is on, the parent test is
 * asked first (it only reads, and so does the search) and its answer is kept for the goal's own super.canUse() call, which
 * the second wrapper answers with it: the parent test runs once per canUse, as shipped, only earlier. When it answers false
 * the search is marked UNUSED so that vanilla_entity_walk_skip leaves out its walk (the call itself runs) and canUse answers
 * false as before. See GoetySummonScans.
 */
@Mixin(targets = {"com.Polarice3.Goety.common.entities.neutral.AbstractNecromancer$SummonZombieSpell",
        "com.Polarice3.Goety.common.entities.neutral.AbstractCairnNecromancer$SummonServantSpell",
        "com.Polarice3.Goety.common.entities.neutral.AbstractMossyNecromancer$SummonServantSpell",
        "com.Polarice3.Goety.common.entities.neutral.AbstractWitherNecromancer$SummonServantSpell",
        "com.Polarice3.Goety.common.entities.neutral.DrownedNecromancer$SummonServantSpell"}, remap = false)
public abstract class GoetySummonScanMixin extends AbstractNecromancer.SummoningSpellGoal {
    /** The parent test's answer asked before this canUse's search: 1 true, -1 false, 0 none kept (flag off). */
    @Unique
    private int bons$goetySummonParent;

    GoetySummonScanMixin(AbstractNecromancer outer) {
        outer.super();
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "m_8036_()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$goetySummonScan(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        if (!GoetySummonScans.goety) {
            bons$goetySummonParent = 0;
            return original.call(level, searched, box, predicate);
        }
        boolean parent = super.m_8036_();
        bons$goetySummonParent = parent ? 1 : -1;
        // the parent test answered true: the search runs as shipped, called right here (through the helper, whose one
        // call site serves six goal classes, the call kept its argument array and a virtual call whenever the JIT did
        // not inline the helper: about 25 ns)
        if (parent) return original.call(level, searched, box, predicate);
        return GoetySummonScans.idle(GoetySummonScans.GOETY, level, searched, box, predicate, original);
    }

    /**
     * The goal's own super.canUse() after the search: the answer asked before it (nothing between them writes). A super call's
     * receiver is the target class itself; @Coerce takes it as the shared parent type (one mixin, several targets).
     */
    @WrapOperation(method = "m_8036_()Z",
            at = @At(value = "INVOKE", target = "Lcom/Polarice3/Goety/common/entities/neutral/AbstractNecromancer$SummoningSpellGoal;m_8036_()Z"),
            require = 1, allow = 1)
    private boolean bons$goetySummonParentTest(@Coerce AbstractNecromancer.SummoningSpellGoal self, Operation<Boolean> original) {
        int kept = bons$goetySummonParent;
        bons$goetySummonParent = 0;
        if (kept != 0 && GoetySummonScans.goety) return kept > 0;
        return original.call(self);
    }
}
