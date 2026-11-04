package bons.furious.mixin.vanilla_lookup_loops;

import bons.furious.patch.vanilla_lookup_loops.FaceLoops;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_multiface_face_loop (Minecraft 1.20.1, SRG client jar 1.20.1-20230612.114412, not patched by Forge 47.4.28; both
 * sides). Cancellable HEAD injections into the static MultifaceBlock.hasAnyFace (m_153960_) and hasAnyVacantFace
 * (m_153962_): with the runtime switch on they answer with FaceLoops' plain loop over the same direction array (f_153806_)
 * and the same static hasFace (m_153900_) calls, stopping at the first match as the stock anyMatch does; with it off the
 * stock stream runs. Only our own logic is carried. See FaceLoops.
 */
@Mixin(value = MultifaceBlock.class, remap = false)
public abstract class MultifaceBlockFaceLoopMixin {
    @Shadow
    @Final
    protected static Direction[] f_153806_;

    @Inject(method = "m_153960_", at = @At("HEAD"), cancellable = true)
    private static void bons$hasAnyFace(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (FaceLoops.enabled) cir.setReturnValue(FaceLoops.any(state, f_153806_, true));
    }

    @Inject(method = "m_153962_", at = @At("HEAD"), cancellable = true)
    private static void bons$hasAnyVacantFace(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (FaceLoops.enabled) cir.setReturnValue(FaceLoops.any(state, f_153806_, false));
    }
}
