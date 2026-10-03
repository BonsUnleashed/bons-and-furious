package bons.furious.mixin.spawn;

import bons.furious.patch.spawn.SeaLifeStandIns;
import com.ninni.spawn.server.block.entity.SeaBunnyBlockEntity;
import com.ninni.spawn.server.block.entity.SeaLifeDecoBlockEntity;
import com.ninni.spawn.server.block.entity.SeaStarBlockEntity;
import com.ninni.spawn.server.block.entity.SeaUrchinBlockEntity;
import java.util.Random;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * spawn_sealife_tick (Spawn 4.0.7).
 *
 * The sea-life decorations (sea bunny, sea star, sea urchin) built a new java.util.Random seeded from their block
 * position on every client tick just to draw one movement interval, and moved with setMovePos(new Vector2f(x, z)).
 * The interval now comes from ac$interval, which computes the same first nextInt(120) draw of a Random with that seed
 * for the three known classes (any other subclass still gets a fresh Random and its own getInterval), and the moves
 * write x and z directly through ac$move. The results are identical; tick no longer allocates the Random or the vectors
 * it passed to setMovePos.
 */
@Mixin(value = SeaLifeDecoBlockEntity.class, remap = false)
public abstract class SeaLifeDecoTickMixin {
    @Shadow float x;
    @Shadow float z;

    @Shadow protected abstract int getInterval(Random random);

    /**
     * What getInterval(new Random(seed)) returns. SeaBunnyBlockEntity draws 60 + nextInt(120), sea stars and urchins
     * inherit 120 + nextInt(120); for them the draw is computed directly: java.util.Random scrambles the seed, then
     * nextInt(120) takes next(31) and repeats while the draw falls into the incomplete last block of 120.
     */
    @Unique
    private int ac$interval(long seed) {
        Class<?> type = getClass();
        int base;
        if (type == SeaBunnyBlockEntity.class) {
            base = 60;
        } else if (type == SeaStarBlockEntity.class || type == SeaUrchinBlockEntity.class) {
            base = 120;
        } else {
            return getInterval(new Random(seed));
        }
        long state = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
        int bits, value;
        do {
            state = (state * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            bits = (int) (state >>> 17);
            value = bits % 120;
        } while (bits - value + 119 < 0);
        return base + value;
    }

    /** What setMovePos(new Vector2f(x, z)) does, without the vector. */
    @Unique
    private void ac$move(float x, float z) {
        this.x = x;
        this.z = z;
    }

    // tick() is static and runs for every decoration on every client tick, so only allocation-free redirects are used.
    // Mixin requires a constructor redirect to return an object; SeaLifeStandIns holds shared placeholders that the
    // redirected call receiving them ignores.

    /** {@code new Random(getBlockPos().asLong())}: no longer built; it only fed getInterval, redirected below. */
    @Redirect(method = "tick", at = @At(value = "NEW", target = "(J)Ljava/util/Random;"))
    private static Random bons$noRandom(long seed) {
        return SeaLifeStandIns.UNUSED_RANDOM;
    }

    /** {@code be.getInterval(random)} becomes {@code be.ac$interval(seed)} with the same seed. */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;getInterval(Ljava/util/Random;)I"))
    private static int bons$interval(SeaLifeDecoBlockEntity be, Random unused) {
        return ((SeaLifeDecoTickMixin) (Object) be).ac$interval(be.getBlockPos().asLong());   // getBlockPos().asLong()
    }

    /** First tick: {@code be.setMovePos(new Vector2f(x, z))} with two random offsets becomes {@code be.ac$move(x, z)}. */
    @Redirect(method = "tick", at = @At(value = "NEW", target = "(FF)Lorg/joml/Vector2f;", ordinal = 0))
    private static Vector2f bons$firstMove(float x, float z, SeaLifeDecoBlockEntity be) {
        ((SeaLifeDecoTickMixin) (Object) be).ac$move(x, z);
        return SeaLifeStandIns.MOVE_APPLIED;
    }

    /** Each step towards the target: {@code be.setMovePos(new Vector2f(newX, newZ))} becomes {@code be.ac$move(newX, newZ)}. */
    @Redirect(method = "tick", at = @At(value = "NEW", target = "(FF)Lorg/joml/Vector2f;", ordinal = 2))
    private static Vector2f bons$stepMove(float x, float z, SeaLifeDecoBlockEntity be) {
        ((SeaLifeDecoTickMixin) (Object) be).ac$move(x, z);
        return SeaLifeStandIns.MOVE_APPLIED;
    }

    /** Both setMovePos calls: the move was already written by the redirect of their vector argument. */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;setMovePos(Lorg/joml/Vector2f;)V"))
    private static void bons$moveAlreadyApplied(SeaLifeDecoBlockEntity be, Vector2f moveApplied) {
    }
}
