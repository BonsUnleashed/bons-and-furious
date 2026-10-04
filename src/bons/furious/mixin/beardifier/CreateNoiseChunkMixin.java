package bons.furious.mixin.beardifier;

import bons.furious.patch.beardifier.EmptyBeardifiers;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * worldgen_empty_beardifier_marker (Minecraft 1.20.1 world generation, server side; Forge 47.4.16).
 *
 * NoiseBasedChunkGenerator.createNoiseChunk builds the chunk's Beardifier (Beardifier.forStructuresInChunk, with every
 * other mod's handlers) and hands it to NoiseChunk.forChunk. The value of that call is passed through
 * EmptyBeardifiers.mark, which returns the very same object and only flags it when it provably adds nothing anywhere.
 * Runs once per NoiseChunk built (once per generated chunk), so the cost of the check is irrelevant.
 */
@Mixin(value = NoiseBasedChunkGenerator.class, remap = false)
public abstract class CreateNoiseChunkMixin {
    @ModifyExpressionValue(method = "m_224256_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/Beardifier;m_223937_(Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/levelgen/Beardifier;"))
    private Beardifier bons$flagEmptyBeardifier(Beardifier beardifier) {
        return EmptyBeardifiers.mark(beardifier);
    }
}
