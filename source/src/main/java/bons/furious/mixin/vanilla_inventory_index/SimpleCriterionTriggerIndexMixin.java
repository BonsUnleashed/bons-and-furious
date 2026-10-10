package bons.furious.mixin.vanilla_inventory_index;

import bons.furious.patch.vanilla_inventory_index.ListenerIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_inventory_trigger_index (Minecraft 1.21.1 with NeoForge 21.1.252; server side incl. the integrated server):
 * SimpleCriterionTrigger gets a listener-map version (bumped at the start of addPlayerListener, removePlayerListener and
 * removePlayerListeners, the only writers of its listener map players) and a weak per-player index cache; the iterator
 * trigger walks its listener set with is asked from ListenerIndex, which hands back the set's own iterator for every
 * trigger except InventoryChangeTrigger during an inventory change. No Minecraft code.
 *
 * Ported to 1.21.1: unchanged. trigger(ServerPlayer, Predicate) still walks the player's listener HashSet with one
 * Set.iterator() call (it now reads each listener's instance through the Listener record's trigger() and the player
 * condition through the instance's Optional player()).
 */
@Mixin(value = SimpleCriterionTrigger.class, remap = false)
public abstract class SimpleCriterionTriggerIndexMixin implements ListenerIndex.Versioned {
    @Unique
    private volatile int bons$listenerVersion;
    @Unique
    private final Map<Object, ListenerIndex.Index> bons$listenerIndexes = new WeakHashMap<>();

    @Override
    public int bons$listenerVersion() {
        return this.bons$listenerVersion;
    }

    @Override
    public Map<Object, ListenerIndex.Index> bons$listenerIndexes() {
        return this.bons$listenerIndexes;
    }

    @Inject(method = {"addPlayerListener", "removePlayerListener", "removePlayerListeners"}, at = @At("HEAD"))
    private void bons$listenersChange(CallbackInfo ci) {
        this.bons$listenerVersion++;
    }

    @WrapOperation(method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Iterator bons$indexedListeners(Set set, Operation<Iterator> original) {
        return ListenerIndex.iterator((SimpleCriterionTrigger) (Object) this, set, original);
    }
}
