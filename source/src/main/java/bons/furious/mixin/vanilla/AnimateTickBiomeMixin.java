package bons.furious.mixin.vanilla;

import bons.furious.patch.vanilla.AnimateTickBiome;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_animate_tick_uniform_biome (Minecraft 1.20.1 client): the ambient-particle biome lookup in
 * ClientLevel.doAnimateTick (doAnimateTick) goes through AnimateTickBiome, which answers from a one-biome chunk section without
 * the fiddled-distance search. A @Redirect: 1,334 calls per tick. Blueprint redirects the two animateTick calls of the
 * same method and YUNG's Cave Biomes injects at its end; neither touches this call.
 */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class AnimateTickBiomeMixin {
    @Redirect(method = "doAnimateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> bons$sectionBiome(ClientLevel level, BlockPos pos) {
        return AnimateTickBiome.biome(level, pos);
    }
}
