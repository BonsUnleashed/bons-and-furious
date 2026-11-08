package bons.furious.mixin.mod_scan_presence_gates;

import bons.furious.patch.mod_scan_presence_gates.CncCaribouDashGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cnc_caribou_dash_presence_gate (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, all rights reserved: no code carried; both
 * sides). CaribouDashDamageProcedure.execute runs for every entity every tick (EntityTickEvent.Pre) and searches
 * CaribouEntity in a 0.5 block box around it: a dashing caribou there hurts and pushes it. While the level holds no
 * CaribouEntity (vanilla_entity_class_count_layer) the search is answered the way it ends when it finds nothing (the
 * profiler's "getEntities" count and a new empty list); the procedure runs unchanged on that list. See
 * CncCaribouDashGate.scan.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.CaribouDashDamageProcedure", remap = false)
public abstract class CncCaribouDashGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$caribouScan(LevelAccessor level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return CncCaribouDashGate.scan(level, searched, box, predicate, original);
    }
}
