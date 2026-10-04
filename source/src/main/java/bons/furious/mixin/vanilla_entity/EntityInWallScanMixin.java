package bons.furious.mixin.vanilla_entity;

import bons.furious.patch.vanilla_entity.BlockScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_suffocation_scan_loop (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): Entity.isInWall, run for every
 * living entity every tick, streams BlockPos.betweenClosedStream(box) into anyMatch. The wrapped call returns BlockScans'
 * stream, whose anyMatch is a loop over the same positions in the same order with the call site's own predicate (see
 * BlockScans for why the answer is the same). Priority 1500 and require = 0: a mod that answers isInWall with a mixin of
 * its own applies first (Radium 0.13.1's experimental.entity.block_caching.suffocation injects a cancellable callback
 * just before this call; off by default) and either answers before the call or leaves the call to this wrap.
 *
 * Ported to 1.21.1: unchanged; the call and its descriptor are the same in the NeoForm and production Entity classes.
 */
@Mixin(value = Entity.class, remap = false, priority = 1500)
public abstract class EntityInWallScanMixin {
    @WrapOperation(method = "isInWall", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;betweenClosedStream(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> bons$wallScan(AABB box, Operation<Stream<BlockPos>> original) {
        return BlockScans.positions(box, original);
    }
}
