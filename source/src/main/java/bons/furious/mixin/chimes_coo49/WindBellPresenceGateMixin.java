package bons.furious.mixin.chimes_coo49;

import bons.furious.patch.chimes_coo49.ChimesScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * chimes_phantom_scan_presence_gate (Chimes 2.1.1, both sides; no Chimes code carried). WindBellBE.tick (every wind bell,
 * every tick, both sides) starts with level.getEntitiesOfClass(Phantom.class, its block grown by 4). While
 * vanilla_entity_class_count_layer says the level holds no Phantom, the search is marked EMPTY, so
 * vanilla_entity_walk_skip leaves out its walk over the entity sections; the call still runs and returns its new empty
 * list. See ChimesScans.
 */
@Mixin(targets = "com.cicada.chimes.block.entity.WindBellBE", remap = false)
public abstract class WindBellPresenceGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$chimesBellPresenceScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ChimesScans.presence(level, searched, box, original);
    }
}
