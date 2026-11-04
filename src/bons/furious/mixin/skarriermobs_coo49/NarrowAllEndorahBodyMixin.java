package bons.furious.mixin.skarriermobs_coo49;

import bons.furious.patch.skarriermobs_coo49.SkarrierScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/**
 * skarriermobs_region_scan_narrowing_all (Skarrier Mobs 1.1.2 and 1.0.6, both sides; trade-off, off by default): in
 * EndorahBodyOnEntityTickUpdateProcedure, searches 1, 3 and 5 (+-10, after the main, melee and ranged head checks) feed
 * loops that skip every element whose saved data string EndorahBindingTag is not this body's UUID; each search runs with
 * that test as its predicate, read without creating the saved data (SkarrierScans.narrowBound).
 */
@Mixin(targets = "net.mcreator.skarriermobs.procedures.EndorahBodyOnEntityTickUpdateProcedure", remap = false)
public abstract class NarrowAllEndorahBodyMixin {
    @WrapOperation(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 1),
            require = 1, allow = 1)
    private static List<?> bons$skarriermobsCoo49NarrowAll1(LevelAccessor world, Class<?> type, AABB box, Predicate<?> pred, Operation<List<?>> original,
                                                           @Local(argsOnly = true) Entity entity) {
        return original.call(world, type, box, SkarrierScans.narrowBound(pred, entity, "EndorahBindingTag"));
    }

    @WrapOperation(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 3),
            require = 1, allow = 1)
    private static List<?> bons$skarriermobsCoo49NarrowAll3(LevelAccessor world, Class<?> type, AABB box, Predicate<?> pred, Operation<List<?>> original,
                                                           @Local(argsOnly = true) Entity entity) {
        return original.call(world, type, box, SkarrierScans.narrowBound(pred, entity, "EndorahBindingTag"));
    }

    @WrapOperation(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 5),
            require = 1, allow = 1)
    private static List<?> bons$skarriermobsCoo49NarrowAll5(LevelAccessor world, Class<?> type, AABB box, Predicate<?> pred, Operation<List<?>> original,
                                                           @Local(argsOnly = true) Entity entity) {
        return original.call(world, type, box, SkarrierScans.narrowBound(pred, entity, "EndorahBindingTag"));
    }
}