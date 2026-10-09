package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.github.L_Ender.lionfishapi.server.animation.LegSolver;
import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Ancient_Remnant.Ancient_Remnant_Entity;

/**
 * cataclysm_server_leg_solver (Ancient_Remnant_Entity, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; acts on
 * the server).
 *
 * tick() runs legSolver.update(this, yBodyRot, scale) on both sides: Lionfish API's leg solver probes the blocks under each
 * leg and stores the leg heights in its own objects. A pack-wide bytecode scan (client and server jars, jar-in-jar) finds
 * no reader of this entity's legSolver besides this call and Cataclysm's client model classes (the leg animation), and no
 * reader of the leg heights besides the solver itself and those models. On a server level the call is skipped: the only
 * difference is the server-side copy of leg heights nobody reads; the probes only read blocks next to the ticking entity.
 */
@Mixin(value = Ancient_Remnant_Entity.class, remap = false)
public abstract class AncientRemnantLegsMixin {
    @WrapOperation(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/lionfishapi/server/animation/LegSolver;update(Lnet/minecraft/world/entity/LivingEntity;FF)V"))
    private void bons$clientOnlyLegs(LegSolver solver, LivingEntity entity, float bodyYaw, float scale, Operation<Void> original) {
        if (CataclysmWorldScans.skipLegSolver(entity)) return;
        original.call(solver, entity, bodyYaw, scale);
    }
}
