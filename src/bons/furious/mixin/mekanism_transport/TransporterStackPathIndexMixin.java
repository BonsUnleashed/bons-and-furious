package bons.furious.mixin.mekanism_transport;

import bons.furious.patch.mekanism_transport.PathIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import mekanism.common.content.transporter.TransporterStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mekanism_transporter_path_index (Mekanism 1.20.1-10.4.13.69, logical server): the pathToTarget.indexOf(tilePos) call
 * in each of TransporterStack.write(LogisticalTransporterBase, FriendlyByteBuf), writeToUpdateTag, isFinal, getNext and
 * getPrev (one per method). PathIndex answers from a first-occurrence index of the current route list, rebuilt when the
 * list object or its size changes; otherwise the original indexOf. require = 0: a build that rewrites these methods
 * leaves the switch out.
 */
@Mixin(value = TransporterStack.class, remap = false)
public abstract class TransporterStackPathIndexMixin implements PathIndex.Holder {
    @Unique
    private PathIndex.Slot bons$pathIndexSlot;

    @Override
    public PathIndex.Slot bons$pathIndexSlot() {
        PathIndex.Slot s = this.bons$pathIndexSlot;
        if (s == null) this.bons$pathIndexSlot = s = new PathIndex.Slot();
        return s;
    }

    @WrapOperation(method = {"write(Lmekanism/common/content/network/transmitter/LogisticalTransporterBase;Lnet/minecraft/network/FriendlyByteBuf;)V",
            "writeToUpdateTag(Lmekanism/common/content/network/transmitter/LogisticalTransporterBase;Lnet/minecraft/nbt/CompoundTag;)V",
            "isFinal(Lmekanism/common/content/network/transmitter/LogisticalTransporterBase;)Z",
            "getNext(Lmekanism/common/content/network/transmitter/LogisticalTransporterBase;)Lnet/minecraft/core/BlockPos;",
            "getPrev(Lmekanism/common/content/network/transmitter/LogisticalTransporterBase;)Lnet/minecraft/core/BlockPos;"},
            require = 0, at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"))
    private int bons$routeIndex(List<?> list, Object pos, Operation<Integer> original) {
        return PathIndex.indexOf(this, list, pos, original);
    }
}
