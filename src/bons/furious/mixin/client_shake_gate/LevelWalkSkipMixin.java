package bons.furious.mixin.client_shake_gate;

import bons.furious.patch.client_shake_gate.EntityWalks;
import bons.furious.patch.client_shake_gate.ShakeGate;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_entity_walk_skip (Minecraft 1.20.1 Level, Forge 47.4.28 jar; since 1.0.49 both sides, was client only).
 *
 * Level.getEntities(EntityTypeTest, AABB, Predicate, List, int) (m_260826_, the end of every getEntitiesOfClass) counts the
 * profiler's "getEntities", walks the level's entity sections (LevelEntityGetter.get), then runs Forge's part-entity pass.
 * This decides whether the walk call runs: it is left out only for the one search a presence gate marked on this thread, in
 * this level, for this base class: a camera-shake gate's mark (ShakeGate, 1.0.45, unchanged) while
 * vanilla_entity_class_count_layer still says no entity section of the level holds a match, or (since 1.0.49) another
 * group's EMPTY / UNUSED mark (EntityWalks). Every other call walks exactly as before; the profiler count, Forge's pass and
 * every other mod's hook on this method run in both cases. Since 1.0.49 MixinExtras' plain @WrapWithCondition (the
 * injector.WrapWithCondition one, a condition around the call; was a @WrapOperation): the same decision without an
 * Operation object per search (an unmarked server search measured 72 bytes more with the @WrapOperation and 48 bytes more
 * with the Operation-based v2 @WrapWithCondition). See ShakeGate and EntityWalks. No Minecraft code is carried.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelWalkSkipMixin {
    @WrapWithCondition(method = "m_260826_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/entity/LevelEntityGetter;m_142137_(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V"),
            require = 1, allow = 1)
    private boolean bons$walk(LevelEntityGetter<?> getter, EntityTypeTest<?, ?> test, AABB box, AbortableIterationConsumer<?> consumer) {
        return !ShakeGate.skipWalk(this, test) && !EntityWalks.leaveOut(this, test);
    }
}
