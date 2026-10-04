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
 * vanilla_suffocation_scan_loop (Minecraft 1.20.1, both sides): Entity.isInWall (m_5830_), run for every living entity
 * every tick, streams BlockPos.betweenClosedStream(box) into anyMatch. The wrapped call returns BlockScans' stream,
 * whose anyMatch is a loop over the same positions in the same order with the call site's own predicate (see BlockScans
 * for why the answer is the same). Priority 1500 and require = 0: a mod that overwrites isInWall with a mixin of its own
 * (a Lithium port's suffocation check) applies first and simply leaves this call out, instead of failing the start.
 */
@Mixin(value = Entity.class, remap = false, priority = 1500)
public abstract class EntityInWallScanMixin {
    @WrapOperation(method = "m_5830_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;m_121921_(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> bons$wallScan(AABB box, Operation<Stream<BlockPos>> original) {
        return BlockScans.positions(box, original);
    }
}
