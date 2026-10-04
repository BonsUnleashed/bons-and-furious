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
 * crittersandcompanions_red_panda_gate (Critters and Companions 2.3.5 on Minecraft 1.20.1; the vanilla AvoidEntityGoal is
 * the SRG class, not patched by Forge 47.4.16). Wraps AvoidEntityGoal.canUse (m_8036_): for a goal that avoids C&C's
 * RedPandaEntity, while its mob's level holds no red panda (vanilla_entity_class_count_layer), canUse does what it does
 * after an empty search (profiler "getEntities" +1, toAvoid = null, false) without searching. Every other goal, and every
 * call while a red panda may exist, runs the original canUse (including Radium's redirects in it, when that Radium option
 * is on). The goal's kind is decided once per goal (avoidClass is final). See RedPandaGate.
 */
@Mixin(value = AvoidEntityGoal.class, remap = false)
public abstract class AvoidEntityGoalRedPandaMixin {
    @Shadow
    @Final
    protected PathfinderMob f_25015_;
    @Shadow
    protected LivingEntity f_25016_;
    @Shadow
    @Final
    protected Class<?> f_25020_;
    @Unique
    private byte bons$redPandaGoal;

    @WrapMethod(method = "m_8036_")
    private boolean bons$redPandaGate(Operation<Boolean> original) {
        if (RedPandaGate.enabled) {
            byte kind = this.bons$redPandaGoal;
            if (kind == 0) this.bons$redPandaGoal = kind = RedPandaGate.kind(this.f_25020_);
            if (kind == RedPandaGate.RED_PANDA_GOAL) {
                Level level = this.f_25015_.m_9236_();
                if (RedPandaGate.nothingToAvoid(level)) {
                    if (RedPandaGate.SHADOW) return RedPandaGate.shadow(original, () -> this.f_25016_, level);
                    level.m_46473_().m_6174_("getEntities");
                    this.f_25016_ = null;
                    RedPandaGate.skipped++;
                    return false;
                }
            }
        }
        return original.call();
    }
}
