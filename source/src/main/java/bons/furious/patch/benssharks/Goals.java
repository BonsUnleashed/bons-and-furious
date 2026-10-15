package bons.furious.patch.benssharks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import bons.furious.mixin.benssharks.MobAccessor;
import bons.furious.mixin.benssharks.NatgAccessor;
import bons.furious.mixin.benssharks.TargetGoalAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/**
 * benssharks_prey_search_merge (Ben's Sharks 1.2.6): called at the end of every Ben's entity's registerGoals. Finds runs of
 * plain (not subclassed, non-player) NearestAttackableTargetGoals that are consecutive among the TARGET goals in insertion
 * order, have non-decreasing priorities, share mustSee / mustReach / unseen-memory, and that no other TARGET goal sits inside
 * (in priority) - then replaces each run of 2+ with one {@link MergedTargetGoal} in the slot of its first member. Anything
 * that does not fit stays exactly as Ben's built it. Formerly part of the separate mod Bons Sharks Fix 1.0.0.
 */
public final class Goals {
    private Goals() {}

    public static void merge(Mob mob) {
        GoalSelector sel = ((MobAccessor) mob).bons$sharksTargetSelector();
        Set<WrappedGoal> set = sel.getAvailableGoals();
        List<WrappedGoal> order = new ArrayList<>(set);
        List<WrappedGoal> targets = new ArrayList<>();
        for (WrappedGoal w : order) if (w.getGoal().getFlags().contains(Goal.Flag.TARGET)) targets.add(w);

        List<List<WrappedGoal>> runs = new ArrayList<>();
        List<WrappedGoal> cur = new ArrayList<>();
        for (WrappedGoal w : targets) {
            if (mergeable(w) && (cur.isEmpty() || compatible(cur.get(cur.size() - 1), w))) {
                cur.add(w);
            } else {
                if (cur.size() >= 2) runs.add(cur);
                cur = new ArrayList<>();
                if (mergeable(w)) cur.add(w);
            }
        }
        if (cur.size() >= 2) runs.add(cur);
        if (runs.isEmpty()) return;

        // no other TARGET goal may have a priority inside [first, last] of a run
        List<List<WrappedGoal>> ok = new ArrayList<>();
        for (List<WrappedGoal> run : runs) {
            int lo = run.get(0).getPriority(), hi = run.get(run.size() - 1).getPriority();
            boolean clean = true;
            for (WrappedGoal w : targets) {
                if (run.contains(w)) continue;
                int p = w.getPriority();
                if (p >= lo && p <= hi) {
                    clean = false;
                    break;
                }
            }
            if (clean) ok.add(run);
        }
        if (ok.isEmpty()) return;

        List<WrappedGoal> rebuilt = new ArrayList<>(order.size());
        for (WrappedGoal w : order) {
            List<WrappedGoal> run = null;
            for (List<WrappedGoal> r : ok) if (r.contains(w)) run = r;
            if (run == null) {
                rebuilt.add(w);
            } else if (run.get(0) == w) {
                rebuilt.add(new WrappedGoal(w.getPriority(), build(mob, run)));
            }
        }
        set.clear();
        set.addAll(rebuilt);
        Stats.GOALS_MERGED.increment();
    }

    @SuppressWarnings("unchecked")
    private static MergedTargetGoal build(Mob mob, List<WrappedGoal> run) {
        int n = run.size();
        Class<? extends LivingEntity>[] types = new Class[n];
        int[] intervals = new int[n];
        TargetingConditions[] conds = new TargetingConditions[n];
        int[] prios = new int[n];
        for (int i = 0; i < n; i++) {
            WrappedGoal w = run.get(i);
            NatgAccessor g = (NatgAccessor) w.getGoal();
            types[i] = g.bons$sharksPreyType();
            intervals[i] = g.bons$sharksPreyInterval();
            conds[i] = g.bons$sharksPreyConditions();
            prios[i] = w.getPriority();
        }
        TargetGoalAccessor first = (TargetGoalAccessor) run.get(0).getGoal();
        return new MergedTargetGoal(mob, first.bons$sharksMustSee(), first.bons$sharksMustReach(), first.bons$sharksUnseenMemory(),
                types, intervals, conds, prios);
    }

    private static boolean mergeable(WrappedGoal w) {
        Goal g = w.getGoal();
        if (g.getClass() != NearestAttackableTargetGoal.class) return false;
        Class<?> t = ((NatgAccessor) g).bons$sharksPreyType();
        return t != Player.class && t != ServerPlayer.class;
    }

    private static boolean compatible(WrappedGoal prev, WrappedGoal next) {
        if (next.getPriority() < prev.getPriority()) return false;
        TargetGoalAccessor a = (TargetGoalAccessor) prev.getGoal();
        TargetGoalAccessor b = (TargetGoalAccessor) next.getGoal();
        return a.bons$sharksMustSee() == b.bons$sharksMustSee() && a.bons$sharksMustReach() == b.bons$sharksMustReach()
                && a.bons$sharksUnseenMemory() == b.bons$sharksUnseenMemory();
    }
}
