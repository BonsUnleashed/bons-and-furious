package bons.furious.mixin.terrain;

import java.util.List;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_climate_rtree_flat_bounds (Minecraft 1.20.1 world generation).
 *
 * Climate.RTree.Node.distance sums, over the seven climate dimensions, the squared distance from the target to the
 * node's range in that dimension, reading each range from its own Climate.Parameter object. Each node now also keeps
 * the seven (min, max) pairs in one flat long array, filled when the node is built from those same parameters (they
 * never change), and distance reads that array. The method is a pure integer computation called for every node a biome
 * search visits, so an @Overwrite is used: the body is written from the method's behaviour (for each dimension the
 * amount by which the target lies above max, else below min, else 0, squared and summed in dimension order), not copied.
 * The results are the same 64-bit values in the same order, including overflow behaviour.
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree$Node", remap = false)
public abstract class ClimateNodeBoundsMixin {
    @Shadow
    @Final
    protected Climate.Parameter[] f_186956_;

    /** min0, max0, min1, max1, ... for the seven dimensions, in parameter order. */
    @Unique
    private long[] bons$bounds;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$flattenBounds(List<Climate.Parameter> parameters, CallbackInfo ci) {
        this.bons$bounds = bons$flatten(this.f_186956_);
    }

    @Unique
    private static long[] bons$flatten(Climate.Parameter[] space) {
        long[] b = new long[space.length * 2];
        for (int i = 0; i < space.length; i++) {
            b[2 * i] = space[i].f_186813_();
            b[2 * i + 1] = space[i].f_186814_();
        }
        return b;
    }

    /**
     * @author Bons and Furious (vanilla_climate_rtree_flat_bounds)
     * @reason the same squared range-distance sum, read from one flat array instead of seven Parameter objects
     */
    @Overwrite
    protected long m_186959_(long[] target) {
        long[] b = this.bons$bounds;
        if (b == null) b = this.bons$bounds = bons$flatten(this.f_186956_);   // a node built before the patch applied
        long sum = 0L;
        for (int i = 0; i < 7; i++) {
            long v = target[i];
            long above = v - b[2 * i + 1];
            long below = b[2 * i] - v;
            long d = above > 0L ? above : Math.max(below, 0L);
            sum += d * d;
        }
        return sum;
    }
}
