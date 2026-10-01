package bons.furious.mixin.terrain;

import bons.furious.patch.terrain.ColumnHeightShare;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_noise_column_cache (Minecraft 1.20.1 world generation).
 *
 * NoiseBasedChunkGenerator.getBaseHeight builds a NoiseChunk for one column on every call; structure placement repeats
 * the same columns, mostly from other worker threads. The method now asks ColumnHeightShare first and returns a stored
 * height without building anything; a height it does compute is stored on the way out. The helper answers "not stored"
 * wherever sharing could differ from computing (see its documentation), and then the original code runs. One call costs
 * milliseconds, so the callback object of the @Inject is irrelevant here.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class ColumnHeightShareMixin {
    /** Start of getBaseHeight: a stored height for this query replaces the computation. */
    @Inject(method = "m_214096_", at = @At("HEAD"), cancellable = true)
    private void bons$storedHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random, CallbackInfoReturnable<Integer> cir) {
        int stored = ColumnHeightShare.lookup((NoiseBasedChunkGenerator) (Object) this, x, z, type, level, random);
        if (stored != ColumnHeightShare.MISSING) cir.setReturnValue(stored);
    }

    /** Return of getBaseHeight: store the height and return it unchanged. */
    @ModifyReturnValue(method = "m_214096_", at = @At("RETURN"))
    private int bons$storeHeight(int height, int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return ColumnHeightShare.store((NoiseBasedChunkGenerator) (Object) this, x, z, type, level, random, height);
    }
}
