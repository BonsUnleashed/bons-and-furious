package bons.furious.mixin.goety_coo49;

import bons.furious.patch.goety_coo49.GoetyGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * goety_wight_scan_presence_gate (Goety 3.2.2, MIT: no code carried; both sides; exact). Wight.findWight(Entity,
 * Predicate) searches Wight +-64 blocks around the entity (from the fog events, the Dread overlay and the client tick, several
 * times per frame); while the level holds no Wight (vanilla_entity_class_count_layer) the search is marked so that
 * vanilla_entity_walk_skip leaves out its empty walk, and findWight returns null on the same empty list. See GoetyGates.
 */
@Mixin(targets = "com.Polarice3.Goety.common.entities.hostile.Wight", remap = false)
public abstract class GoetyWightGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "findWight(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lcom/Polarice3/Goety/common/entities/hostile/Wight;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$goetyWightScan(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return GoetyGates.scan(GoetyGates.wight, GoetyGates.WIGHT, level, searched, box, predicate, original);
    }
}
