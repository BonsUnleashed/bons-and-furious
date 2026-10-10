package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.AcropolisMonsters.Clawdian_Entity;
import com.github.L_Ender.lionfishapi.server.animation.LegSolverQuadruped;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_server_leg_solver (Clawdian_Entity; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried; 1.21.1
 * tested build: L_Ender's Cataclysm 1.21.1-3.33 with Lionfish API 3.1; acts on the server).
 *
 * tick() runs legSolver.update(this, yBodyRot, scale) on both sides: Lionfish API's leg solver probes the blocks under each
 * leg and stores the leg heights in its own objects. No reader of this entity's legSolver exists besides this call and
 * Cataclysm's client model class (the leg animation), and no reader of the leg heights besides the solver itself and that
 * model. On a server level the call is skipped: the only difference is the server-side copy of leg heights nobody reads;
 * the probes only read blocks next to the ticking entity.
 *
 * Ported to 1.21.1: unchanged. 3.33's tick() still calls legSolver.update unconditionally; Lionfish API 3.1's
 * LegSolver / Leg.update / settle / getDistance are 2.8's code (BlockPos.containing instead of the floor constructor,
 * same position) and write only Leg.height / prevHeight. A constant-pool scan of the 3.33 jar and every pinned 1.21.1
 * target finds the Clawdian's legSolver read only here and in Clawdian_Model.
 */
@Mixin(value = Clawdian_Entity.class, remap = false)
public abstract class ClawdianLegsMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/lionfishapi/server/animation/LegSolverQuadruped;update(Lnet/minecraft/world/entity/LivingEntity;FF)V"))
    private void bons$clientOnlyLegs(LegSolverQuadruped solver, LivingEntity entity, float bodyYaw, float scale, Operation<Void> original) {
        if (CataclysmWorldScans.skipLegSolver(entity)) return;
        original.call(solver, entity, bodyYaw, scale);
    }
}
