package bons.furious.mixin.terrain;

import bons.furious.patch.terrain.WrapPresize;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_noise_wrap_presize (Minecraft 1.20.1 world generation), part 2 of 2: each RandomState remembers how many
 * entries its NoiseChunks' wrap tables reach, so the next NoiseChunk can start with a table of that size. A plain
 * int field; it changes no behaviour of RandomState.
 */
@Mixin(value = RandomState.class, remap = false)
public abstract class RandomStateWrapSizeMixin implements WrapPresize.Holder {
    @Unique
    private int bons$wrapSize;

    @Override
    public int bons$wrapSize() {
        return this.bons$wrapSize;
    }

    @Override
    public void bons$wrapSize(int size) {
        this.bons$wrapSize = size;
    }
}
