package bons.furious.mixin.vanilla_entity;

import bons.furious.patch.vanilla_entity.BlockScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_fire_scan_loop (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): the fire check at the end of
 * Entity.move, run for every moving entity every tick, streams level.getBlockStatesIfLoaded(box) into noneMatch(fire or
 * lava). The wrapped call returns BlockScans' stream, whose noneMatch asks hasChunksAt and reads the same blocks in the
 * same order through the level's getBlockState, with the call site's own predicate (see BlockScans). Priority 1500 and
 * require = 0, as for the suffocation check (Radium 0.13.1's experimental.entity.block_caching.fire_lava_touching
 * redirects this call; off by default).
 *
 * Ported to 1.21.1: unchanged; the call (on Level) and its descriptor are the same in the NeoForm and production Entity
 * classes.
 */
@Mixin(value = Entity.class, remap = false, priority = 1500)
public abstract class EntityFireScanMixin {
    @WrapOperation(method = "move", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockStatesIfLoaded(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;"))
    private Stream<BlockState> bons$fireScan(Level level, AABB box, Operation<Stream<BlockState>> original) {
        return BlockScans.statesIfLoaded(level, box, original);
    }
}
