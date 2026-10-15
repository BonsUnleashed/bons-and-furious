package bons.furious.patch.benssharks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/**
 * benssharks_prey_search_merge: stands in for a run of plain NearestAttackableTargetGoals (a Ben's bull shark has 39, one per
 * prey class) that are consecutive among the mob's TARGET goals in both insertion order and priority.
 * <p>
 * Stock, every full AI tick each of those goals rolled its own randomInterval and, on a hit, ran its own entity search for
 * its one class. Here the same rolls happen in the same order on the mob's RandomSource (so random use is identical), but
 * every rolled class shares ONE search of the area all of them used (bounding box inflated by follow range, 4 vertically);
 * each class then picks with its own original TargetingConditions through the same getNearestEntity call. First success
 * wins, as in GoalSelector's start loop. While running with a target of priority p, the classes with a strictly lower
 * priority number may still take over each full tick (stop + start, as GoalSelector does when a higher-priority goal
 * pre-empts). canContinueToUse / start / stop are TargetGoal's.
 */
public final class MergedTargetGoal extends TargetGoal {
    final Class<? extends LivingEntity>[] types;
    final int[] intervals;
    final TargetingConditions[] conds;
    final int[] prios;
    private LivingEntity found;
    private int rank = -1;

    MergedTargetGoal(Mob mob, boolean mustSee, boolean mustReach, int unseenMemory,
                     Class<? extends LivingEntity>[] types, int[] intervals, TargetingConditions[] conds, int[] prios) {
        super(mob, mustSee, mustReach);
        this.m_26146_(unseenMemory);
        this.m_7021_(EnumSet.of(Goal.Flag.TARGET));
        this.types = types;
        this.intervals = intervals;
        this.conds = conds;
        this.prios = prios;
    }

    @Override
    public boolean m_8036_() {   // canUse
        return find(Integer.MAX_VALUE);
    }

    /** Roll the classes with priority < below, in order; one shared search; first class with a target wins. */
    private boolean find(int below) {
        Mob mob = this.f_26135_;
        RandomSource r = mob.m_217043_();
        List<LivingEntity> all = null;
        for (int i = 0; i < types.length; i++) {
            if (prios[i] >= below) break;
            int iv = intervals[i];
            if (iv > 0 && r.m_188503_(iv) != 0) continue;
            if (all == null) {
                double d = this.m_7623_();
                all = mob.m_9236_().m_6443_(LivingEntity.class, mob.m_20191_().m_82377_(d, 4.0D, d), e -> true);
                Stats.MERGED_SEARCHES.increment();
            }
            LivingEntity t = nearest(mob, all, types[i], conds[i]);
            if (t != null) {
                found = t;
                rank = i;
                return true;
            }
        }
        return false;
    }

    /** NearestAttackableTargetGoal.findTarget for one class, from the shared list (same order, same call). */
    static LivingEntity nearest(Mob mob, List<LivingEntity> all, Class<? extends LivingEntity> type, TargetingConditions c) {
        List<LivingEntity> sub = new ArrayList<>();
        for (LivingEntity e : all) if (type.isInstance(e)) sub.add(e);
        return mob.m_9236_().m_45982_(sub, c, mob, mob.m_20185_(), mob.m_20188_(), mob.m_20189_());
    }

    @Override
    public void m_8056_() {      // start = NearestAttackableTargetGoal.start
        this.f_26135_.m_6710_(this.found);
        super.m_8056_();
    }

    @Override
    public void m_8037_() {      // tick: higher-priority classes may still take over
        int r = this.rank;
        if (r <= 0) return;
        int limit = prios[r];
        if (prios[0] >= limit) return;
        LivingEntity keep = found;
        if (find(limit)) {
            LivingEntity t = found;
            int nr = rank;
            super.m_8041_();                 // stop the running class (TargetGoal.stop: target null)
            found = t;
            rank = nr;
            this.f_26135_.m_6710_(t);        // start the new one
            super.m_8056_();
            Stats.GOALS_REPLACED.increment();
        } else {
            found = keep;
            rank = r;
        }
    }

    public int size() {
        return types.length;
    }

    public double followDistance() {
        return this.m_7623_();
    }

    @Override
    public String toString() {
        return "BonsAndFurious.MergedTargetGoal[" + types.length + " prey classes, priorities " + prios[0] + ".." + prios[prios.length - 1] + "]";
    }
}
