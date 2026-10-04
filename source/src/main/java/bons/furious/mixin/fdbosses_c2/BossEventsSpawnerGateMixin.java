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
 * fdbosses_spawner_presence_gate (FD Bosses 3.1.0.3 for 1.21.1, fdbosses-3.1.0.3-1.21.1.jar, on NeoForge 21.1.252; both
 * sides; the listeners act on the server), part 1 of 3: the four
 * BossSpawnerEntity searches of BossEvents (preventArenaDestruction for BreakEvent, EntityPlaceEvent and
 * ExplosionEvent.Detonate: +-100 blocks; deathEvent for LivingDropsEvent: +-50). While the level holds no boss spawner
 * (vanilla_entity_class_count_layer) the search returns a new empty list after the profiler's "getEntities" line, exactly
 * what the original search returns then; the listener's own code runs unchanged on it. See SpawnerPresence.
 * The two block events call through LevelAccessor (interface call), the other two through Level.
 *
 * Ported to 1.21.1: the four listeners take NeoForge's events (net.neoforged.neoforge.event...) and make the same calls
 * (same owners and descriptors, javap of the 1.21.1 jar; the explosion box is centred on Explosion.center()).
 */
@Mixin(value = BossEvents.class, remap = false)
public abstract class BossEventsSpawnerGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = {"preventArenaDestruction(Lnet/neoforged/neoforge/event/level/BlockEvent$BreakEvent;)V",
            "preventArenaDestruction(Lnet/neoforged/neoforge/event/level/BlockEvent$EntityPlaceEvent;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 2, allow = 2)
    private static List bons$blockEventSpawners(LevelAccessor level, Class<?> searched, AABB box, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "preventArenaDestruction(Lnet/neoforged/neoforge/event/level/ExplosionEvent$Detonate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$explosionSpawners(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "deathEvent(Lnet/neoforged/neoforge/event/entity/living/LivingDropsEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$dropSpawners(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, predicate, original);
    }
}
