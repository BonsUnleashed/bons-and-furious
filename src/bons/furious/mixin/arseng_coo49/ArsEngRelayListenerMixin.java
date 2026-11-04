package bons.furious.mixin.arseng_coo49;

import bons.furious.patch.arseng_coo49.ArsEngRelays;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * arseng_relay_splitter_listener_leak (Ars Energistique 1.2.0, LGPL-3.0: no code carried; fix). Targets the method Ars
 * Energistique's own RelaySplitterTileMixin merges into Ars Nouveau's RelaySplitterTile (arseng$processStaleBlockPositions,
 * both list passes). Its LazyOptional.addListener call on a present optional no longer joins the optional's listener set:
 * the pass keeps the pair (one reused list per splitter, a @Unique field), and right before the pass walks its stale list
 * every kept listener whose optional was invalidated during the pass runs, as the optional's invalidate would have run it;
 * then the list is cleared. An absent optional runs the listener at once, as before (ArsEngRelays). Priority 1100: after
 * Ars Energistique's mixin. The method exists whenever Ars Energistique is loaded (its config plugin filters only its
 * aecapfix mixins); the guard on that mixin method keeps the switch off without Ars Energistique and for other builds.
 */
@Mixin(targets = "com.hollingsworth.arsnouveau.common.block.tile.RelaySplitterTile", remap = false, priority = 1100)
public abstract class ArsEngRelayListenerMixin {
    @Unique
    private ArrayList<Object> bons$arsengKept;

    @WrapOperation(method = "arseng$processStaleBlockPositions(Ljava/util/ArrayList;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/util/LazyOptional;addListener(Lnet/minecraftforge/common/util/NonNullConsumer;)V"),
            require = 1, allow = 1)
    private void bons$arsengRelayListen(LazyOptional<?> cap, NonNullConsumer<?> listener, Operation<Void> original) {
        ArrayList<Object> kept = this.bons$arsengKept;
        if (kept == null) this.bons$arsengKept = kept = new ArrayList<>(16);
        if (!ArsEngRelays.defer(cap, listener, kept)) original.call(cap, listener);
    }

    @Inject(method = "arseng$processStaleBlockPositions(Ljava/util/ArrayList;Z)V",
            at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;", ordinal = 1), require = 1, allow = 1)
    private void bons$arsengRelayStale(ArrayList<BlockPos> posList, boolean sendSource, CallbackInfo ci) {
        ArrayList<Object> kept = this.bons$arsengKept;
        if (kept != null && !kept.isEmpty()) ArsEngRelays.runInvalidated(kept);
    }
}
