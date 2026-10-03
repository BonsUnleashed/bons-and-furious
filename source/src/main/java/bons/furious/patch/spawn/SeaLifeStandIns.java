package bons.furious.patch.spawn;

import java.util.Random;
import org.joml.Vector2f;

/**
 * Placeholders for the spawn_sealife_tick redirects (bons.furious.mixin.spawn.SeaLifeDecoTickMixin).
 *
 * Mixin requires a constructor redirect to return a non-null object. Where 1.0.19 removed an allocation from
 * SeaLifeDecoBlockEntity.tick, the redirect hands back one of these shared objects instead of building a new one.
 * Each is passed only to a call that is itself redirected and ignores it, so neither is ever read or changed.
 */
public final class SeaLifeStandIns {
    /** Stands in for tick's {@code new Random(seed)}; its only use, getInterval, is redirected to ac$interval. */
    public static final Random UNUSED_RANDOM = new Random(0L);

    /** Stands in for tick's {@code new Vector2f(x, z)} after ac$move wrote x and z; its only use, setMovePos, is dropped. */
    public static final Vector2f MOVE_APPLIED = new Vector2f();

    private SeaLifeStandIns() {}
}
