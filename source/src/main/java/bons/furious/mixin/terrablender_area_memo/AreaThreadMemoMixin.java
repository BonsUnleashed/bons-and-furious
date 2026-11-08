package bons.furious.mixin.terrablender_area_memo;

import bons.furious.patch.terrablender_area_memo.AreaMemo;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrablender.worldgen.noise.Area;

/**
 * terrablender_area_thread_memo (TerraBlender 4.1.0.8 for NeoForge 1.21.1, LGPL-3.0; both sides). terrablender.worldgen.noise.Area
 * get(int, int): a cancellable HEAD injection answers from this thread's table when it holds (area, x, z), so the
 * StampedLock is not taken; a MixinExtras @ModifyReturnValue at the method's returns keeps the answer TerraBlender's own
 * get computed or found. The Area's code runs unchanged on every miss. Only our own logic is carried. See AreaMemo.
 */
@Mixin(value = Area.class, remap = false)
public abstract class AreaThreadMemoMixin {
    /** This area's number in the per-thread tables (0 until its first lookup). */
    @Unique
    private int bons$areaId;

    @Inject(method = "get(II)I", at = @At("HEAD"), cancellable = true, remap = false)
    private void bons$keptAnswer(int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (!AreaMemo.enabled) return;
        int id = this.bons$areaId;
        if (id == 0) this.bons$areaId = id = AreaMemo.newId();
        long kept = AreaMemo.lookup(id, x, z);
        if (kept != Long.MIN_VALUE) cir.setReturnValue((int) kept);
    }

    @ModifyReturnValue(method = "get(II)I", at = @At("RETURN"), remap = false)
    private int bons$keepAnswer(int value, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int z) {
        int id = this.bons$areaId;
        if (id != 0 && AreaMemo.enabled) AreaMemo.keep(id, x, z, value);
        return value;
    }
}
