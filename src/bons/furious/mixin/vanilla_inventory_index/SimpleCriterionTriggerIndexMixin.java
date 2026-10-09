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
 * vanilla_inventory_trigger_index (Minecraft 1.20.1 on Forge 47.4.16; server side): SimpleCriterionTrigger gets a
 * listener-map version (bumped at the start of addPlayerListener m_6467_, removePlayerListener m_6468_ and
 * removePlayerListeners m_5656_, the only writers of its listener map) and a weak per-player index cache; the iterator
 * trigger (m_66234_) walks its listener set with is asked from ListenerIndex, which hands back the set's own iterator for
 * every trigger except InventoryChangeTrigger during an inventory change. No Minecraft code.
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

    @Inject(method = {"m_6467_", "m_6468_", "m_5656_"}, at = @At("HEAD"))
    private void bons$listenersChange(CallbackInfo ci) {
        this.bons$listenerVersion++;
    }

    @WrapOperation(method = "m_66234_", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Iterator bons$indexedListeners(Set set, Operation<Iterator> original) {
        return ListenerIndex.iterator((SimpleCriterionTrigger) (Object) this, set, original);
    }
}
