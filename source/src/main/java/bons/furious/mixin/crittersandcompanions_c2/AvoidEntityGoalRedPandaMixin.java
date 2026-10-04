package bons.furious.mixin.crittersandcompanions_c2;

import bons.furious.patch.crittersandcompanions_c2.RedPandaGate;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * crittersandcompanions_red_panda_gate (Critters and Companions 2.7.0 on Minecraft 1.21.1 + NeoForge 21.1.252; the vanilla
 * AvoidEntityGoal is not patched by NeoForge). Wraps AvoidEntityGoal.canUse: for a goal that avoids C&C's RedPandaEntity,
 * while its mob's level holds no red panda (vanilla_entity_class_count_layer), canUse does what it does after an empty
 * search (profiler "getEntities" +1, toAvoid = null, false) without searching. Every other goal, and every call while a red
 * panda may exist, runs the original canUse (including any foreign redirect inside it). The goal's kind is decided once per
 * goal (avoidClass is final). See RedPandaGate.
 * Ported to 1.21.1: canUse has the same body (getNearestEntity of getEntitiesOfClass(avoidClass, box inflated by maxDist,
 * 3, maxDist, e -> true)); the red-panda class is matched under its 2.7.0 name (EntityClassCounts.TRACKED).
 */
@Mixin(value = AvoidEntityGoal.class, remap = false)
public abstract class AvoidEntityGoalRedPandaMixin {
    @Shadow
    @Final
    protected PathfinderMob mob;
    @Shadow
    protected LivingEntity toAvoid;
    @Shadow
    @Final
    protected Class<?> avoidClass;
    @Unique
    private byte bons$redPandaGoal;

    @WrapMethod(method = "canUse")
    private boolean bons$redPandaGate(Operation<Boolean> original) {
        if (RedPandaGate.enabled) {
            byte kind = this.bons$redPandaGoal;
            if (kind == 0) this.bons$redPandaGoal = kind = RedPandaGate.kind(this.avoidClass);
            if (kind == RedPandaGate.RED_PANDA_GOAL) {
                Level level = this.mob.level();
                if (RedPandaGate.nothingToAvoid(level)) {
                    if (RedPandaGate.SHADOW) return RedPandaGate.shadow(original, () -> this.toAvoid, level);
                    level.getProfiler().incrementCounter("getEntities");
                    this.toAvoid = null;
                    RedPandaGate.skipped++;
                    return false;
                }
            }
        }
        return original.call();
    }
}
