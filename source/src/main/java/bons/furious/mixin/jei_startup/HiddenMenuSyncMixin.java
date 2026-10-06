package bons.furious.mixin.jei_startup;

import bons.furious.patch.jei_startup.HiddenMenuSync;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContainerMenu.class, remap = false)
public abstract class HiddenMenuSyncMixin implements HiddenMenuSync.Menu {
    @Shadow @Final private List<ContainerListener> containerListeners;
    @Shadow private ContainerSynchronizer synchronizer;
    @Unique private boolean bons$jeiSimulation, bons$syncDirty, bons$flushing;

    @Override public void bons$jeiSimulationMenu() { bons$jeiSimulation = true; }

    @Inject(method = "broadcastChanges", at = @At("HEAD"), cancellable = true)
    private void bons$unobservedSync(CallbackInfo callback) {
        if (bons$jeiSimulation && HiddenMenuSync.enabled && !bons$flushing
                && containerListeners.isEmpty() && synchronizer == null) {
            bons$syncDirty = true;
            HiddenMenuSync.SKIPPED.increment();
            callback.cancel();
        } else bons$syncDirty = false;
    }

    // If an addon starts observing a simulation menu, first bring its hidden snapshots up to date
    // while still unobserved. Then stock listener registration sees exactly its normal snapshots.
    @Inject(method = "addSlotListener", at = @At("HEAD"))
    private void bons$beforeListener(ContainerListener listener, CallbackInfo callback) { bons$flush(); }

    @Inject(method = "setSynchronizer", at = @At("HEAD"))
    private void bons$beforeSynchronizer(ContainerSynchronizer synchronizer, CallbackInfo callback) { bons$flush(); }

    @Unique private void bons$flush() {
        if (!bons$syncDirty) return;
        bons$flushing = true;
        try { ((AbstractContainerMenu)(Object)this).broadcastChanges(); }
        finally { bons$flushing = false; }
    }
}
