package bons.portprobe;

import bons.furious.patch.benssharks.Goals;
import bons.furious.patch.benssharks.MergedTargetGoal;

import bons.furious.mixin.benssharks.MobAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/** Probe-only comparisons against actual vanilla goals in the production runtime. */
public final class GoalOracle42 {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map<String, Object> run(ServerLevel level) {
        Mob hunter = EntityType.ZOMBIE.create(level);
        if (hunter == null) throw new AssertionError("No hunter");
        int x = level.getSharedSpawnPos().getX();
        int z = level.getSharedSpawnPos().getZ();
        level.getChunk(x >> 4, z >> 4);
        hunter.moveTo(x, 300, z, 0, 0);
        hunter.setNoAi(true);
        List<LivingEntity> prey = new ArrayList<>();
        int trials = 0, positive = 0;
        try {
            Class<? extends LivingEntity>[] types = new Class[]{
                    net.minecraft.world.entity.animal.Cow.class,
                    net.minecraft.world.entity.animal.Pig.class,
                    net.minecraft.world.entity.animal.Sheep.class};
            EntityType<?>[] factories = {EntityType.COW, EntityType.PIG, EntityType.SHEEP};
            for (int scene = 0; scene < 8; scene++) {
                for (LivingEntity e : prey) e.discard();
                prey.clear();
                for (int i = 0; i < types.length; i++) {
                    if ((scene & (1 << i)) == 0) continue;
                    Mob e = (Mob) factories[i].create(level);
                    if (e == null) throw new AssertionError("No prey");
                    e.setNoAi(true);
                    level.getChunk((x + 2 + i) >> 4, z >> 4);
                    e.moveTo(x + 2 + i, 300, z, 0, 0);
                    if (!level.addFreshEntity(e)) throw new AssertionError("Prey insertion failed");
                    prey.add(e);
                }
                if (level.getEntitiesOfClass(LivingEntity.class, hunter.getBoundingBox().inflate(16, 4, 16)).size() < prey.size())
                    throw new AssertionError("Fixture prey not query-visible");
                for (int interval : new int[]{0, 1, 10}) {
                    List<NearestAttackableTargetGoal<?>> vanilla = new ArrayList<>();
                    TargetingConditions[] conditions = new TargetingConditions[types.length];
                    int[] intervals = new int[types.length];
                    int[] priorities = {1, 2, 3};
                    for (int i = 0; i < types.length; i++) {
                        var goal = new NearestAttackableTargetGoal(hunter, types[i], interval, false, false, null);
                        vanilla.add(goal);
                        var accessor = (bons.furious.mixin.benssharks.NatgAccessor) goal;
                        conditions[i] = accessor.bons$sharksPreyConditions();
                        intervals[i] = accessor.bons$sharksPreyInterval();
                    }
                    var selector = ((MobAccessor) hunter).bons$sharksTargetSelector();
                    selector.getAvailableGoals().clear();
                    for (int i = 0; i < vanilla.size(); i++) selector.addGoal(priorities[i], vanilla.get(i));
                    Goals.merge(hunter);
                    if (selector.getAvailableGoals().size() != 1) throw new AssertionError("Goals did not merge");
                    var merged = (MergedTargetGoal) selector.getAvailableGoals().iterator().next().getGoal();
                    for (int seed = 0; seed < 128; seed++) {
                        hunter.setTarget(null);
                        hunter.getRandom().setSeed(seed);
                        LivingEntity expected = null;
                        for (var goal : vanilla) {
                            if (goal.canUse()) {
                                goal.start();
                                expected = hunter.getTarget();
                                goal.stop();
                                break;
                            }
                        }
                        long randomAfter = hunter.getRandom().nextLong();
                        hunter.setTarget(null);
                        hunter.getRandom().setSeed(seed);
                        boolean found = merged.canUse();
                        if (found) merged.start();
                        if (hunter.getTarget() != expected || found != (expected != null))
                            throw new AssertionError("Target mismatch scene=" + scene + " interval=" + interval + " seed=" + seed);
                        if (hunter.getRandom().nextLong() != randomAfter)
                            throw new AssertionError("Random consumption mismatch");
                        if (found) {
                            positive++;
                            merged.stop();
                            if (hunter.getTarget() != null) throw new AssertionError("Stop retained target");
                        }
                        trials++;
                    }
                }
            }
            if (positive == 0) throw new AssertionError("Vacuous oracle");
            int schedulerChecks = scheduler(level, hunter, prey, x, z);
            int sharks = 0, mergedGroups = 0;
            for (EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("benssharks")) continue;
                var entity = type.create(level);
                if (entity instanceof Mob mob) {
                    sharks++;
                    for (var wrapped : ((MobAccessor) mob).bons$sharksTargetSelector().getAvailableGoals())
                        if (wrapped.getGoal() instanceof MergedTargetGoal group) {
                            if (group.size() < 2 || !group.getFlags().contains(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET))
                                throw new AssertionError("Invalid Sharks merged group");
                            mergedGroups++;
                        }
                }
                if (entity != null) entity.discard();
            }
            if (sharks < 20 || mergedGroups == 0) throw new AssertionError("Sharks layouts not exercised");
            return Map.of("passed", true, "trials", trials, "positive_targets", positive,
                    "scheduler_checks", schedulerChecks, "sharks_types", sharks, "sharks_merged_groups", mergedGroups,
                    "scope", "selection, RNG, stop and full selector ticks; actual Sharks construction");
        } finally {
            for (LivingEntity e : prey) e.discard();
            hunter.discard();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int scheduler(ServerLevel level, Mob hunter, List<LivingEntity> prey, int x, int z) {
        for (LivingEntity e : prey) e.discard();
        prey.clear();
        Mob reference = EntityType.ZOMBIE.create(level);
        if (reference == null) throw new AssertionError("No reference hunter");
        reference.moveTo(x, 300, z, 0, 0);
        Class[] types = {net.minecraft.world.entity.animal.Cow.class, net.minecraft.world.entity.animal.Pig.class,
                net.minecraft.world.entity.animal.Sheep.class};
        EntityType<?>[] factories = {EntityType.COW, EntityType.PIG, EntityType.SHEEP};
        int checks = 0;
        try {
            for (int interval : new int[]{0, 10}) for (int seed = 0; seed < 16; seed++) {
                var candidate = ((MobAccessor) hunter).bons$sharksTargetSelector();
                var baseline = ((MobAccessor) reference).bons$sharksTargetSelector();
                for (GoalSelector selector : new GoalSelector[]{candidate, baseline}) {
                    for (var goal : selector.getAvailableGoals()) if (goal.isRunning()) goal.stop();
                    selector.getAvailableGoals().clear();
                }
                hunter.setTarget(null);
                reference.setTarget(null);
                for (int i = 0; i < types.length; i++) {
                    candidate.addGoal(i + 1, new NearestAttackableTargetGoal(hunter, types[i], interval, false, false, null));
                    baseline.addGoal(i + 1, new NearestAttackableTargetGoal(reference, types[i], interval, false, false, null));
                }
                Goals.merge(hunter);
                for (int tick = 0; tick < 40; tick++) {
                    if (tick % 10 == 0) {
                        if (tick == 0 || tick == 30) {
                            for (LivingEntity e : prey) e.discard();
                            prey.clear();
                        }
                        int mask = new int[]{4, 6, 7, 0}[tick / 10];
                        for (int i = 0; i < 3; i++) if ((mask & (1 << i)) != 0) {
                            boolean present = false;
                            for (LivingEntity existing : prey) if (types[i].isInstance(existing)) present = true;
                            if (present) continue;
                            Mob e = (Mob) factories[i].create(level);
                            if (e == null) throw new AssertionError("No scheduler prey");
                            e.setNoAi(true);
                            e.moveTo(x + 2 + i, 300, z, 0, 0);
                            if (!level.addFreshEntity(e)) throw new AssertionError("Scheduler insertion failed");
                            prey.add(e);
                        }
                    }
                    long randomSeed = seed * 1000L + tick;
                    hunter.getRandom().setSeed(randomSeed);
                    reference.getRandom().setSeed(randomSeed);
                    baseline.tick();
                    candidate.tick();
                    if (hunter.getTarget() != reference.getTarget())
                        throw new AssertionError("Selector target mismatch interval=" + interval + " seed=" + seed + " tick=" + tick);
                    if (hunter.getRandom().nextLong() != reference.getRandom().nextLong())
                        throw new AssertionError("Selector RNG mismatch interval=" + interval + " seed=" + seed + " tick=" + tick);
                    baseline.tickRunningGoals(false);
                    candidate.tickRunningGoals(false);
                    if (hunter.getTarget() != reference.getTarget()
                            || hunter.getRandom().nextLong() != reference.getRandom().nextLong())
                        throw new AssertionError("Intermediate selector tick mismatch");
                    checks++;
                }
            }
            return checks;
        } finally { reference.discard(); }
    }
}