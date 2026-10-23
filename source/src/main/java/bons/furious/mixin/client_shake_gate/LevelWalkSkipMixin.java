package bons.furious.mixin.client_shake_gate;

import bons.furious.patch.client_shake_gate.ShakeGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_entity_walk_skip (Minecraft 1.20.1 Level, Forge 47.4.16 jar; client JVM).
 *
 * Level.getEntities(EntityTypeTest, AABB, Predicate, List, int) (getEntities, the end of every getEntitiesOfClass) counts the
 * profiler's "getEntities", walks the level's entity sections (LevelEntityGetter.get), then runs Forge's part-entity pass.
 * This wraps the walk call: it is left out only for the one search a camera-shake presence gate marked on this thread, in
 * this level, for this base class, while vanilla_entity_class_count_layer still says no entity section of the level holds a
 * match (so the walk would find nothing). Every other call walks exactly as before; the profiler count, Forge's pass and
 * every other mod's hook on this method run in both cases. See ShakeGate. No Minecraft code is carried.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelWalkSkipMixin {
    @WrapOperation(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V"),
            require = 1, allow = 1)
    private void bons$walk(LevelEntityGetter<?> getter, EntityTypeTest<?, ?> test, AABB box, AbortableIterationConsumer<?> consumer, Operation<Void> original) {
        if (ShakeGate.skipWalk(this, test)) return;
        original.call(getter, test, box, consumer);
    }
}
