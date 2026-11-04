package bons.furious.mixin.create_outline_coo49;

import bons.furious.patch.create_outline_coo49.OutlineSteps;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.block.BigOutlines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * create_big_outline_probe_dedupe (Create 6.0.8, client): HEAD of BigOutlines.pick starts a new ray; HEAD of the pick's
 * per-step test (lambda$pick$0) starts a step; in that test each neighbourhood read is skipped (AIR) when OutlineSteps
 * knows the cell was tested at the previous step of this ray. See OutlineSteps.
 */
@Mixin(value = BigOutlines.class, remap = false)
public abstract class BigOutlineDedupeMixin {
    @Inject(method = "pick", at = @At("HEAD"))
    private static void bons$newRay(CallbackInfo ci) {
        OutlineSteps.begin();
    }

    @Inject(method = "lambda$pick$0", at = @At("HEAD"))
    private static void bons$newStep(Minecraft mc, Vec3 origin, Vec3 target, double range, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        OutlineSteps.step(pos, origin, target, range);
    }

    @WrapOperation(method = "lambda$pick$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 1, allow = 1)
    private static BlockState bons$readOnce(ClientLevel level, BlockPos cell, Operation<BlockState> original) {
        if (OutlineSteps.seen(cell)) {
            if (OutlineSteps.SHADOW) OutlineSteps.shadow(original.call(level, cell), level, cell);
            return OutlineSteps.SKIPPED;
        }
        return original.call(level, cell);
    }
}
