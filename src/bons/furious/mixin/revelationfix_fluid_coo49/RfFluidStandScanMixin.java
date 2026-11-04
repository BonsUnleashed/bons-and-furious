package bons.furious.mixin.revelationfix_fluid_coo49;

import bons.furious.patch.revelationfix_fluid_coo49.RfFluidWalk;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * revelationfix_fluid_walk_scan (RevelationFix 4.8 and 4.7, the Jar-in-Jar of Goety Revelation; both sides; RevelationFix
 * is all rights reserved: no code of it is carried). A MixinSquared @TargetHandler @ModifyExpressionValue inside
 * RevelationFix's EntityMixin.fluidCollision handler (merged into Entity.move after its collide call) on its first read
 * of the movement's y, the "moves up" test that follows its "not living" return: for a living entity that does not move
 * up and for which RfFluidWalk.skip says no listener can make it stand on a fluid, the test reads a positive y, so the
 * handler takes its own early return of the movement it was given, which is what its scan ends in then (RfFluidWalk
 * explains why). Nothing is allocated; the y the handler keeps for its scan is a later read and stays untouched. Priority
 * 1500: applied after RevelationFix's mixin, which has the default mixin priority 1000 (its config's "priority"
 * 2147483647 orders configs, not mixins). require = 0 and expect = 0: where RevelationFix's handler is not merged (the mod
 * absent) nothing happens; the switch's guard lines pin the handler, and the group's proof applies RevelationFix's own
 * mixin and checks the merged handler calls ours at that read.
 */
@Mixin(value = Entity.class, priority = 1500, remap = false)
public abstract class RfFluidStandScanMixin {
    @TargetHandler(mixin = "com.mega.revelationfix.mixin.EntityMixin", name = "fluidCollision")
    @ModifyExpressionValue(method = "@MixinSquared:Handler",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/phys/Vec3;f_82480_:D", opcode = Opcodes.GETFIELD, ordinal = 0),
            require = 0, expect = 0, allow = 1)
    private double bons$revelationfixFluidCoo49Skip(double y) {
        return y <= 0.0 && RfFluidWalk.skip((Entity) (Object) this) ? 1.0 : y;
    }
}
