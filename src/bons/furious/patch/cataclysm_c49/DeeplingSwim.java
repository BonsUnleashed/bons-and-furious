package bons.furious.patch.cataclysm_c49;

import bons.furious.mixin.cataclysm_c49.CataclysmEntityDimensionsAccessor;
import com.github.L_Ender.cataclysm.entity.Deepling.AbstractDeepling;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_deepling_swim_refresh_skip (L_Ender's Cataclysm 3.16, 3.31 the same; both sides; since
 * 1.0.49; a trade-off, off by default). Idea: Collections Of Optimizations (cataclysm.leanDeeplingSwimRefresh), idea text
 * only. Our own logic; no Cataclysm code (CC-BY-NC-ND-4.0, injectors only).
 *
 * Every tick a deepling (the Deepling, Angler, Brute, Priest and Warlock, and the Lionfish) tests whether its swimming box
 * (in water) or its normal box (out of water) is free of blocks with Level.noCollision, and when it is, sets its swim flag
 * to that state and calls refreshDimensions (a Forge EntityEvent.Size post, the size from the pose, the bounding box made
 * again from the position).
 *
 * What changes: when the swim flag already has the state the branch would set and the deepling's size already equals
 * getDimensions(getPose()) (width, height and fixed flag), the box test answers "not free", so the tick neither tests the
 * box nor refreshes: refreshing would set the same flag and the same size. Otherwise the test and the refresh run as
 * before. The difference: on those ticks no EntityEvent.Size is posted and the bounding box is not made again from the
 * position (a listener that changes a deepling's size on that event, or a box moved away from the position by other code,
 * is picked up only on the next tick that changes the flag or the pose).
 *
 * -Dbons_and_furious.cataclysmDeeplingSwimRefreshSkip=false runs the test every tick even with the switch on.
 */
public final class DeeplingSwim {
    public static final String KEY = "cataclysm_deepling_swim_refresh_skip";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmDeeplingSwimRefreshSkip", "true"));
    private static volatile boolean announced;

    private DeeplingSwim() {
    }

    /** The wrapped Level.noCollision of AbstractDeepling.tick; {@code swimming} = the in-water branch (swimming box). */
    public static boolean room(AbstractDeepling deepling, boolean swimming, Level level, Entity entity, AABB box, Operation<Boolean> original) {
        if (enabled && deepling.getDeeplingSwim() == swimming && settled(deepling)) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (deeplings refresh their size only when their swim state or pose changes)", KEY);
            }
            return false;
        }
        return original.call(level, entity, box);
    }

    /** True when the deepling's size already is the one its pose gives. */
    static boolean settled(AbstractDeepling deepling) {
        EntityDimensions now = ((CataclysmEntityDimensionsAccessor) deepling).bons$cataclysmDimensions();
        EntityDimensions want = deepling.m_6972_(deepling.m_20089_());
        return now != null && want != null && now.f_20377_ == want.f_20377_ && now.f_20378_ == want.f_20378_ && now.f_20379_ == want.f_20379_;
    }
}
