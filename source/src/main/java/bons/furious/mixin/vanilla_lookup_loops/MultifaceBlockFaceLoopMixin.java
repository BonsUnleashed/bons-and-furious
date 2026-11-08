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
 * vanilla_multiface_face_loop (Minecraft 1.21.1, both sides; MultifaceBlock is not patched by NeoForge 21.1.252).
 * Cancellable HEAD injections into the static MultifaceBlock.hasAnyFace and hasAnyVacantFace: with the runtime switch on
 * they answer with FaceLoops' plain loop over the same direction array (DIRECTIONS) and the same static hasFace calls,
 * stopping at the first match as the stock anyMatch does; with it off the stock stream runs. Only our own logic is carried.
 * See FaceLoops.
 */
@Mixin(value = MultifaceBlock.class, remap = false)
public abstract class MultifaceBlockFaceLoopMixin {
    @Shadow
    @Final
    protected static Direction[] DIRECTIONS;

    @Inject(method = "hasAnyFace(Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private static void bons$hasAnyFace(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (FaceLoops.enabled) cir.setReturnValue(FaceLoops.any(state, DIRECTIONS, true));
    }

    @Inject(method = "hasAnyVacantFace(Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private static void bons$hasAnyVacantFace(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (FaceLoops.enabled) cir.setReturnValue(FaceLoops.any(state, DIRECTIONS, false));
    }
}
