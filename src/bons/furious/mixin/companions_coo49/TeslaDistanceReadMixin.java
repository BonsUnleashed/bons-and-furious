package bons.furious.mixin.companions_coo49;

import bons.furious.patch.companions_coo49.LazyTeslaNetwork;
import bons.furious.patch.companions_coo49.TeslaDistances;
import dev.xylonity.companions.common.blockentity.AbstractTeslaBlockEntity;
import dev.xylonity.companions.common.tesla.TeslaNetwork;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * companions_tesla_lazy_distances (Companions 1.3.5 / 1.3.6, server): before every read of a tesla block entity's distance
 * (getDistance, saveAdditional, getUpdateTag) and before every change of its outgoing set that does not go through the
 * network (addOutgoing, removeOutgoing, load), its network's pending rebuild runs (TeslaNetworkLazyMixin). Client block
 * entities have no network: nothing happens there. See TeslaDistances.
 */
@Mixin(value = AbstractTeslaBlockEntity.class, remap = false)
public abstract class TeslaDistanceReadMixin extends BlockEntity {
    private TeslaDistanceReadMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @org.spongepowered.asm.mixin.Unique
    private void bons$flushNetwork() {
        if (!TeslaDistances.enabled) return;
        // Only on the server thread. C2ME writes block entities on its own save thread (saveAdditional reaches this from there),
        // and Companions' rebuild was written for the server thread: run beside it, a tick's sync or a wrench could read a
        // half-rebuilt distance (bug sweep 2026-11-12, found by review). A save made there writes the distance as it stands,
        // as it does when Companions' own rebuild is running at that moment; the next read on the server thread runs the
        // pending rebuild, and a block entity that loads registers again, which rebuilds too.
        if (this.f_58857_ instanceof net.minecraft.server.level.ServerLevel level && !level.m_7654_().m_18695_()) return;
        TeslaNetwork n = TeslaDistances.existing(this.f_58857_);
        if (n != null) ((LazyTeslaNetwork) n).bons$flushDistances();
    }

    @Inject(method = "getDistance", at = @At("HEAD"))
    private void bons$distanceRead(CallbackInfoReturnable<Integer> cir) {
        bons$flushNetwork();
    }

    @Inject(method = "m_183515_", at = @At("HEAD"))
    private void bons$distanceSaved(CompoundTag tag, CallbackInfo ci) {
        bons$flushNetwork();
    }

    @Inject(method = "m_5995_", at = @At("HEAD"))
    private void bons$distanceSent(CallbackInfoReturnable<CompoundTag> cir) {
        bons$flushNetwork();
    }

    @Inject(method = {"addOutgoing", "removeOutgoing"}, at = @At("HEAD"))
    private void bons$outgoingChanges(dev.xylonity.companions.common.tesla.ConnectionTarget target, CallbackInfo ci) {
        bons$flushNetwork();
    }

    @Inject(method = "m_142466_", at = @At("HEAD"))
    private void bons$outgoingLoaded(CompoundTag tag, CallbackInfo ci) {
        bons$flushNetwork();
    }
}
