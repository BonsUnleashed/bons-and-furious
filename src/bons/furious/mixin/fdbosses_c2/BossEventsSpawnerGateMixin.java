package bons.furious.mixin.fdbosses_c2;

import bons.furious.patch.fdbosses_c2.SpawnerPresence;
import com.finderfeed.fdbosses.BossEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fdbosses_spawner_presence_gate (FD Bosses 3.1.0.3, both sides; the listeners act on the server), part 1 of 3: the four
 * BossSpawnerEntity searches of BossEvents (preventArenaDestruction for BreakEvent, EntityPlaceEvent and
 * ExplosionEvent.Detonate: +-100 blocks; deathEvent for LivingDropsEvent: +-50). While the level holds no boss spawner
 * (vanilla_entity_class_count_layer) the search returns a new empty list after the profiler's "getEntities" line, exactly
 * what the original search returns then; the listener's own code runs unchanged on it. See SpawnerPresence.
 * The two block events call through LevelAccessor (interface call), the other two through Level.
 */
@Mixin(value = BossEvents.class, remap = false)
public abstract class BossEventsSpawnerGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = {"preventArenaDestruction(Lnet/minecraftforge/event/level/BlockEvent$BreakEvent;)V",
            "preventArenaDestruction(Lnet/minecraftforge/event/level/BlockEvent$EntityPlaceEvent;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 2, allow = 2)
    private static List bons$blockEventSpawners(LevelAccessor level, Class<?> searched, AABB box, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "preventArenaDestruction(Lnet/minecraftforge/event/level/ExplosionEvent$Detonate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$explosionSpawners(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "deathEvent(Lnet/minecraftforge/event/entity/living/LivingDropsEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$dropSpawners(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, predicate, original);
    }
}
