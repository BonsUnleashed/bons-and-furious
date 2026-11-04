package bons.furious.mixin.structurify_height_c49;

import bons.furious.patch.structurify_height_c49.HeightTable;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * structurify_height_cache_primitive (Structurify 2.0.34+mc1.20.1, server side incl. the integrated server).
 *
 * MixinSquared @TargetHandler injections into Structurify's ChunkGeneratorMixin handlers structurify$getFirstFreeHeight
 * and structurify$getFirstOccupiedHeight (its @WrapMethod handlers, merged into ChunkGenerator m_223221_ / m_223235_):
 * the four calls into Structurify's ChunkGeneratorHeightCache (get and put of each handler) are wrapped and answered by
 * {@link HeightTable}, a primitive per-thread table holding the same mapping. Structurify's handler logic (look up, on a
 * miss run the original and store) is untouched and stays Structurify's; no Structurify code is carried (CC BY-NC-ND).
 * Why the answers are identical: HeightTable's javadoc. Priority 1500: applied after Structurify's mixin (default 1000),
 * so the handlers exist. require = 0: without the handlers nothing is injected (the guard steps the switch aside first).
 */
@Mixin(value = ChunkGenerator.class, priority = 1500, remap = false)
public abstract class HeightCacheHandlerMixin {
    @TargetHandler(mixin = "com.faboslav.structurify.common.mixin.ChunkGeneratorMixin", name = "structurify$getFirstFreeHeight")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache;getFirstFreeHeight(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;"))
    private Integer bons$shcFreeLookup(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                       RandomState random, Operation<Integer> original) {
        return HeightTable.lookupFree(generator, x, z, type, accessor, random, original);
    }

    @TargetHandler(mixin = "com.faboslav.structurify.common.mixin.ChunkGeneratorMixin", name = "structurify$getFirstFreeHeight")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache;putFirstFreeHeight(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V"))
    private void bons$shcFreeStore(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                   RandomState random, int height, Operation<Void> original) {
        HeightTable.storeFree(generator, x, z, type, accessor, random, height, original);
    }

    @TargetHandler(mixin = "com.faboslav.structurify.common.mixin.ChunkGeneratorMixin", name = "structurify$getFirstOccupiedHeight")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache;getFirstOccupiedHeight(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;"))
    private Integer bons$shcOccupiedLookup(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                           RandomState random, Operation<Integer> original) {
        return HeightTable.lookupOccupied(generator, x, z, type, accessor, random, original);
    }

    @TargetHandler(mixin = "com.faboslav.structurify.common.mixin.ChunkGeneratorMixin", name = "structurify$getFirstOccupiedHeight")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache;putFirstOccupiedHeight(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V"))
    private void bons$shcOccupiedStore(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor accessor,
                                       RandomState random, int height, Operation<Void> original) {
        HeightTable.storeOccupied(generator, x, z, type, accessor, random, height, original);
    }
}
