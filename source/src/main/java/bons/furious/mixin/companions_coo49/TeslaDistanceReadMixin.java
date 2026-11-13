package bons.furious.mixin.companions_coo49;

import bons.furious.patch.companions_coo49.LazyTeslaNetwork;
import bons.furious.patch.companions_coo49.TeslaDistances;
import dev.xylonity.companions.common.blockentity.AbstractTeslaBlockEntity;
import dev.xylonity.companions.common.tesla.ConnectionTarget;
import dev.xylonity.companions.common.tesla.TeslaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * companions_tesla_lazy_distances (Companions 1.3.6 for Minecraft 1.21.1, server): before every read of a tesla block
 * entity's distance (getDistance, saveAdditional, getUpdateTag) and before every change of its outgoing set that does not go
 * through the network (addOutgoing, removeOutgoing, loadAdditional), its network's pending rebuild runs
 * (TeslaNetworkLazyMixin). Client block entities have no network: nothing happens there. See TeslaDistances.
 */
@Mixin(value = AbstractTeslaBlockEntity.class, remap = false)
public abstract class TeslaDistanceReadMixin extends BlockEntity {
    private TeslaDistanceReadMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private void bons$flushNetwork() {
        if (!TeslaDistances.enabled) return;
        // Only on the server thread. C2ME writes block entities on its own save thread (saveAdditional reaches this from there),
        // and Companions' rebuild was written for the server thread: run beside it, a tick's sync or a wrench could read a
        // half-rebuilt distance (bug sweep 2026-11-12, found by review). A save made there writes the distance as it stands,
        // as it does when Companions' own rebuild is running at that moment; the next read on the server thread runs the
        // pending rebuild, and a block entity that loads registers again, which rebuilds too.
        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel && !serverLevel.getServer().isSameThread()) return;
        TeslaNetwork n = TeslaDistances.existing(this.level);
        if (n != null) ((LazyTeslaNetwork) n).bons$flushDistances();
    }

    @Inject(method = "getDistance()I", at = @At("HEAD"))
    private void bons$distanceRead(CallbackInfoReturnable<Integer> cir) {
        bons$flushNetwork();
    }

    @Inject(method = "saveAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("HEAD"))
    private void bons$distanceSaved(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        bons$flushNetwork();
    }

    @Inject(method = "getUpdateTag(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"))
    private void bons$distanceSent(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        bons$flushNetwork();
    }

    @Inject(method = {"addOutgoing(Ldev/xylonity/companions/common/tesla/ConnectionTarget;)V",
            "removeOutgoing(Ldev/xylonity/companions/common/tesla/ConnectionTarget;)V"}, at = @At("HEAD"))
    private void bons$outgoingChanges(ConnectionTarget target, CallbackInfo ci) {
        bons$flushNetwork();
    }

    @Inject(method = "loadAdditional(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("HEAD"))
    private void bons$outgoingLoaded(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        bons$flushNetwork();
    }
}
