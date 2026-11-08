package bons.furious.mixin.terrain_router_c49;

import bons.furious.patch.terrain_router_c49.RouterMemo;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * terrain_router_shared_node_memo (Minecraft 1.21.1 world generation, both sides), part 1 of 2: the visitor of NoiseChunk's
 * first density pass (router.mapAll(this::wrap), the constructor's first NoiseRouter.mapAll) is wrapped in RouterMemo's
 * first-pass visitor, which applies exactly as the wrap visitor does and lets HolderHolderMemoMixin hand back a shared
 * node's result the second time (RouterMemo explains why the pass result is the same). The second pass (the final density
 * with the beardifier marker), the climate sampler's mappings and wrap itself are untouched. require = 0: another shape of
 * the constructor leaves the pass as it is. Injectors only (Mojang code is no-copy).
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkFirstPassMemoMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"),
            require = 0, expect = 0)
    private DensityFunction.Visitor bons$routerFirstPassMemo(DensityFunction.Visitor visitor, @Local(argsOnly = true) RandomState random) {
        return RouterMemo.firstPass(visitor, random);
    }
}
