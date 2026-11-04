package bons.furious.mixin.companions_coo49;

import bons.furious.patch.companions_coo49.LazyTeslaNetwork;
import bons.furious.patch.companions_coo49.TeslaDistances;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.xylonity.companions.common.tesla.TeslaNetwork;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * companions_tesla_lazy_distances (Companions 1.3.5 / 1.3.6, server): the recalculateDistances() call in each of the six
 * mutation methods marks the network dirty instead (refreshRecallCaches still follows at once); bons$flushDistances() runs
 * the original rebuild once when something is pending; clearAll flushes every dirty network first, and so does a block
 * entity leaving the network (unregister, or another one registered at its position). See TeslaDistances.
 * COO 5.8 has an option for the same idea without a mixin on these methods in its ledger (companions.deferTeslaRebuild).
 */
@Mixin(value = TeslaNetwork.class, remap = false)
public abstract class TeslaNetworkLazyMixin implements LazyTeslaNetwork {
    @Shadow
    @Final
    private static Map<ResourceKey<Level>, TeslaNetwork> NETWORKS;

    @Shadow
    public abstract void recalculateDistances();

    @Shadow
    @Final
    private Map<net.minecraft.core.BlockPos, dev.xylonity.companions.common.blockentity.AbstractTeslaBlockEntity> blockEntities;

    @Unique
    private volatile boolean bons$distancesDirty;

    /**
     * A block entity that leaves the network (unregister, or another one registered at its position) keeps the distance
     * of the last rebuild it took part in; the original rebuilt at every mutation, so that was the rebuild of the
     * mutation just before. The pending rebuild runs first, so it leaves with the same number.
     */
    @Inject(method = "unregisterBlockEntity", at = @At("HEAD"))
    private void bons$flushBeforeLeave(dev.xylonity.companions.common.blockentity.AbstractTeslaBlockEntity be, CallbackInfo ci) {
        bons$flushDistances();
    }

    @Inject(method = "registerBlockEntity", at = @At("HEAD"))
    private void bons$flushBeforeReplace(dev.xylonity.companions.common.blockentity.AbstractTeslaBlockEntity be, CallbackInfo ci) {
        if (this.bons$distancesDirty) {
            Object there = this.blockEntities.get(be.m_58899_());
            if (there != null && there != be) bons$flushDistances();
        }
    }

    @WrapOperation(method = {"registerBlockEntity", "unregisterBlockEntity", "onConnectionAdded", "onConnectionRemoved",
            "indexEntityOutgoing", "removeEntityNode"},
            at = @At(value = "INVOKE", target = "Ldev/xylonity/companions/common/tesla/TeslaNetwork;recalculateDistances()V"),
            require = 6, allow = 6)
    private void bons$deferRebuild(TeslaNetwork network, Operation<Void> original) {
        if (TeslaDistances.enabled) {
            this.bons$distancesDirty = true;
            TeslaDistances.deferred();
            return;
        }
        original.call(network);
    }

    @Override
    public void bons$flushDistances() {
        if (this.bons$distancesDirty) {
            this.bons$distancesDirty = false;
            TeslaDistances.rebuilt();
            this.recalculateDistances();
        }
    }

    @Inject(method = "clearAll", at = @At("HEAD"))
    private static void bons$flushBeforeClear(CallbackInfo ci) {
        for (TeslaNetwork n : NETWORKS.values()) ((LazyTeslaNetwork) n).bons$flushDistances();
    }
}
