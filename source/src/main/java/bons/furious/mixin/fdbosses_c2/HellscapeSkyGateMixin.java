package bons.furious.mixin.fdbosses_c2;

import bons.furious.patch.fdbosses_c2.SpawnerPresence;
import com.finderfeed.fdbosses.BossClientEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fdbosses_spawner_presence_gate (FD Bosses 3.1.0.3, CLIENT), part 3 of 3: BossClientEvents.tickHellscapeSky searches +-30
 * blocks around the local player for a Malkuth boss spawner every client tick at night. While the client level holds none
 * (vanilla_entity_class_count_layer) the search returns a new empty list after the profiler's "getEntities" line, as the
 * original search does then, and the sky fades out exactly as before (backward()). See SpawnerPresence.
 *
 * Ported to 1.21.1: tickHellscapeSky (FD Bosses 3.1.0.3 for 1.21.1) makes the same Level.getEntitiesOfClass(Class, AABB,
 * Predicate) call.
 */
@Mixin(value = BossClientEvents.class, remap = false)
public abstract class HellscapeSkyGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "tickHellscapeSky",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$malkuthSpawners(Level level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, predicate, original);
    }
}
