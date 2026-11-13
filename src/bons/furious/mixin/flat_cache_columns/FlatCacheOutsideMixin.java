package bons.furious.mixin.flat_cache_columns;

import bons.furious.patch.flat_cache_columns.OutsideColumns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_flat_cache_outside_columns (Minecraft 1.20.1 world generation, tested build 1.20.1 SRG; both sides), part 2 of 2:
 * NoiseChunk$FlatCache.
 *
 * FlatCache.compute (m_207386_) answers a position inside the NoiseChunk's grid from its values array and calls the
 * wrapped function (f_209326_) for every other position. The wrapper around that one call keeps the answers of this
 * FlatCache in a small table keyed by block x and z (OutsideColumns.Table), when the NoiseChunk's router passed
 * OutsideColumns' check, this FlatCache was made while its NoiseChunk was wrapping that router, the flat_cache marker
 * it was made from (told by MarkerOriginMixin) wraps a function of x and z alone, and the request does not come with
 * the NoiseChunk itself as its context (the inner cache_once wrappers answer such a request from the NoiseChunk's own
 * fill state). Every other FlatCache, every such request, and every call once a comparison failed, runs the original
 * call.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache", remap = false)
public abstract class FlatCacheOutsideMixin implements OutsideColumns.Flat {
    @Shadow @Final NoiseChunk f_209325_;                   // the owning NoiseChunk (this$0)

    /** Null until the first request outside the grid; then this FlatCache's table or OutsideColumns.Table.NONE. */
    @Unique
    private OutsideColumns.Table bons$outside;
    @Unique
    private boolean bons$fromRouter;
    /** True once a flat_cache marker record's mapAll has returned this FlatCache (MarkerOriginMixin): the first one counts. */
    @Unique
    private boolean bons$linked;
    /** That marker is one of the checked router's and wraps a function of x and z alone. No reference to the marker is kept. */
    @Unique
    private boolean bons$markerTabled;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$outsideMade(NoiseChunk owner, DensityFunction filler, boolean fill, CallbackInfo ci) {
        this.bons$fromRouter = (Object) owner instanceof OutsideColumns.Chunk c && c.bons$outsideWrapping();
    }

    @Override
    public void bons$origin(Object marker) {
        if (this.bons$linked) return;
        this.bons$linked = true;
        this.bons$markerTabled = this.bons$fromRouter && OutsideColumns.tabled(this.f_209325_, marker);
    }

    @WrapOperation(method = "m_207386_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunction;m_207386_(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D"))
    private double bons$outsideColumn(DensityFunction filler, DensityFunction.FunctionContext context, Operation<Double> original) {
        OutsideColumns.Table t = this.bons$outside;
        if (t == null) {
            t = OutsideColumns.table(this.f_209325_, this.bons$fromRouter, this.bons$linked, this.bons$markerTabled);
            if (t == null) {                                   // the NoiseChunk is still being constructed: no verdict yet
                OutsideColumns.countUntabled();
                return original.call(filler, context);
            }
            this.bons$outside = t;
        }
        if (t == OutsideColumns.Table.NONE || (Object) context == this.f_209325_ || !t.world.ok()) {
            OutsideColumns.countUntabled();
            return original.call(filler, context);
        }
        int x = context.m_207115_(), z = context.m_207113_();
        int slot = t.slot(x, z);
        if (t.has(slot, x, z)) {
            double served = t.value(slot);
            if (t.compare()) return OutsideColumns.compared(t, served, original.call(filler, context), x, z);
            OutsideColumns.countServed();
            return served;
        }
        double value = original.call(filler, context);
        t.put(slot, x, z, value);
        OutsideColumns.countComputed();
        return value;
    }
}
