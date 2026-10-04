package bons.furious.mixin.fdbosses_c2;

import bons.furious.patch.fdbosses_c2.SpawnerPresence;
import com.finderfeed.fdbosses.client.BossCommonMixinHandle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fdbosses_spawner_presence_gate (FD Bosses 3.1.0.3, both sides), part 2 of 3: BossCommonMixinHandle.entityCollidersMixin
 * (FD Bosses' hook in Entity.collideBoundingBox for players and thrown ender pearls) searches +-20 blocks for Chesed
 * kinetic fields on every move. While the level holds none (vanilla_entity_class_count_layer) the search returns a new
 * empty list after the profiler's "getEntities" line, as the original search does then; no collision shape is added either
 * way. See SpawnerPresence.
 *
 * Ported to 1.21.1: entityCollidersMixin (FD Bosses 3.1.0.3 for 1.21.1) makes the same Level.getEntitiesOfClass(Class,
 * AABB) call.
 */
@Mixin(value = BossCommonMixinHandle.class, remap = false)
public abstract class KineticFieldGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "entityCollidersMixin",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$kineticFields(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return SpawnerPresence.search(level, searched, box, original);
    }
}
