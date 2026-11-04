package bons.furious.mixin.tome_of_wonders_coo49;

import bons.furious.patch.tome_of_wonders_coo49.TowSchools;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tome_of_wonders_squill_school_main_thread (Tome of Wonders 1.0.0, MIT; server; fix): MixinExtras @WrapOperation on the
 * Level.addFreshEntity (m_7967_) call in Squill.spawnSchool: called off the server thread (a squill spawned while its chunk
 * generates, on a world generation worker), the add of each school member is handed to the server thread
 * (MinecraftServer.execute) and the call answers true (spawnSchool discards the answer); on the server thread the
 * original call. Members, their positions and the random draws stay exactly as the original makes them on the worker.
 * Our own logic only.
 */
@Mixin(targets = "com.platypushasnohat.tome_of_wonders.entities.Squill", remap = false)
public abstract class SquillSchoolThreadMixin {
    @WrapOperation(method = "spawnSchool()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, allow = 1)
    private boolean bons$towCoo49SchoolOnServerThread(Level level, Entity member, Operation<Boolean> original) {
        return TowSchools.add(level, member, original);
    }
}
