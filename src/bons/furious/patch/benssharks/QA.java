package bons.furious.patch.benssharks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import bons.furious.guard.Guards;
import bons.furious.mixin.benssharks.MobAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/**
 * Ben's Sharks switches: read-only diagnostics for test rigs (called reflectively, so a rig also runs without this mod).
 * Nothing in the game calls these.
 */
public final class QA {
    private QA() {}

    public static Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String key : SharksSetup.KEYS) m.put(key, Guards.decide(key).state().name());
        m.put("living", SharkState.LIVING);
        m.put("level", SharkState.LEVEL);
        m.put("setup", SharksSetup.summary);
        m.put("stats", Stats.snapshot());
        return m;
    }

    public static List<String> plan(LivingEntity e) {
        return Dispatch.planFor(e);
    }

    /**
     * Oracle for the merged prey search: for every class of every MergedTargetGoal of this mob, the target the stock goal
     * would pick (its own getEntitiesOfClass(class, area) + getNearestEntity) against the one MergedTargetGoal picks from the
     * shared LivingEntity list. Returns {classes, mismatches, nonNull}.
     */
    public static int[] goalOracle(Mob mob) {
        int classes = 0, mismatch = 0, nonNull = 0;
        for (WrappedGoal w : ((MobAccessor) mob).bons$sharksTargetSelector().m_148105_()) {
            if (!(w.m_26015_() instanceof MergedTargetGoal g)) continue;
            double d = g.followDistance();
            var area = mob.m_20191_().m_82377_(d, 4.0D, d);
            List<LivingEntity> all = mob.m_9236_().m_6443_(LivingEntity.class, area, e -> true);
            for (int i = 0; i < g.types.length; i++) {
                classes++;
                List<? extends LivingEntity> own = mob.m_9236_().m_6443_(g.types[i], area, e -> true);
                LivingEntity stock = mob.m_9236_().m_45982_(new ArrayList<>(own), g.conds[i], mob, mob.m_20185_(), mob.m_20188_(), mob.m_20189_());
                LivingEntity ours = MergedTargetGoal.nearest(mob, all, g.types[i], g.conds[i]);
                if (stock != ours) mismatch++;
                if (stock != null) nonNull++;
            }
        }
        return new int[] {classes, mismatch, nonNull};
    }

    /** Number of TARGET-flag goals and merged prey classes on this mob. */
    public static int[] goalShape(Mob mob) {
        int goals = 0, merged = 0;
        for (WrappedGoal w : ((MobAccessor) mob).bons$sharksTargetSelector().m_148105_()) {
            goals++;
            if (w.m_26015_() instanceof MergedTargetGoal g) merged += g.size();
        }
        return new int[] {goals, merged};
    }
}
