package bons.furious.mixin.projectile_gates;

import bons.furious.patch.projectile_gates.ProjectileGates;
import com.bawnorton.mixinsquared.TargetHandler;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * immersiveaircraft_vehicle_trace_gate (Immersive Aircraft 1.1.8, GPL-3.0; both sides; since 1.0.46).
 *
 * Immersive Aircraft's ProjectileUtilMixin merges two ia$getEntityHitResult handlers into ProjectileUtil, one at the RETURN
 * of each getEntityHitResult overload (getEntityHitResult: Entity shooter, ..., double distance; getEntityHitResult: Level, Entity
 * projectile, ..., float inflation). Each calls ia$vehicleTrace: a level entity search over the hit-test box inflated by 16
 * blocks with the predicate VehicleEntity.class::isInstance, then a shape test of every vehicle the projectile's filter
 * accepts; only a hit replaces the return value. MixinSquared @TargetHandler injections at the HEAD of both merged handlers
 * (selected by name and descriptor) return from the handler at once when ProjectileGates.skipVehicleTrace says that search
 * can find nothing and has no other effect (see there); the return value then stands, exactly as when Immersive Aircraft's
 * loop finds nothing. Otherwise Immersive Aircraft's handler runs unchanged. Our own logic only. Priority 1500 (after
 * Immersive Aircraft's mixin), require = 0, expect = 0: without that mixin nothing is injected. The handler reads only its
 * arguments; the CallbackInfo is the only allocation (scalar-replaced once inlined).
 */
@Mixin(value = ProjectileUtil.class, priority = 1500, remap = false)
public abstract class ImmersiveAircraftTraceGateMixin {
    @TargetHandler(mixin = "immersive_aircraft.mixin.ProjectileUtilMixin",
            name = "ia$getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;DLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void bons$vehicleTraceByShooter(Entity shooter, Vec3 start, Vec3 end, AABB box, Predicate<Entity> filter, double distance,
                                                    CallbackInfoReturnable<EntityHitResult> theirCir, CallbackInfo ci) {
        if (shooter != null && ProjectileGates.skipVehicleTrace(shooter.level(), box)) ci.cancel();
    }

    @TargetHandler(mixin = "immersive_aircraft.mixin.ProjectileUtilMixin",
            name = "ia$getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void bons$vehicleTraceByProjectile(Level level, Entity projectile, Vec3 start, Vec3 end, AABB box, Predicate<Entity> filter,
                                                       float inflation, CallbackInfoReturnable<EntityHitResult> theirCir, CallbackInfo ci) {
        if (ProjectileGates.skipVehicleTrace(level, box)) ci.cancel();
    }
}
