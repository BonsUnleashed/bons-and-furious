package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.ChunkStatusNames;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_chunk_status_name_memo (Minecraft 1.20.1, both sides).
 *
 * ChunkStatus.toString() is {@code BuiltInRegistries.CHUNK_STATUS.getKey(this).toString()}: a registry reverse lookup
 * and a new string per call. C2ME's profiling mixin (MixinChunkHolder.postGetChunkAt, RETURN of getOrScheduleFuture)
 * evaluates it for every unfinished chunk request. The first string the original returns is kept and handed back
 * again. Why the result is identical: a status's registry key is fixed once it is registered, and the original builds
 * an equal string from it every time; a status asked before it has a key runs the original (which throws, as before)
 * and nothing is kept. A race between two threads only computes the same string twice.
 */
@Mixin(value = ChunkStatus.class, remap = false)
public abstract class ChunkStatusNameMixin {
    @Unique
    private String bons$name;

    @WrapMethod(method = "toString")
    private String bons$rememberName(Operation<String> original) {
        if (!ChunkStatusNames.enabled) {
            return original.call();
        }
        String name = this.bons$name;
        if (name == null) {
            name = original.call();
            this.bons$name = name;
        }
        return name;
    }
}
