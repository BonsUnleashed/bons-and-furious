package bons.furious.patch.fallentraits_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.xkmc.l2hostility.content.capability.mob.MobTraitCap;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.kayn.fallen_traits.init.FTTraits;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious trade-off switch fallentraits_devourer_index (Fallen Traits & Relics 1.0.0, LGPL-2.1-or-later, with L2
 * Hostility 2.5.19; server side; kind "change", ships off). Ours. Idea: Collections Of Optimizations
 * (fallentraits.indexDevourers), idea text only.
 *
 * FTEvents.onDevourerHeal runs for every LivingHealEvent on a server level (natural regeneration of every player and mob):
 * it searches every LivingEntity in a box of devourerRadiusPerLevel x the maximum trait level around the healed entity and
 * resolves each one's MobTraitCap; the first one with the Devourer trait in its radius takes the heal as overheal. Devourers
 * are rare legendary-trait mobs, so nearly every search finds none.
 *
 * The index: every server-side MobTraitCap.tick ends with seen(cap, mob), which records the mob (by identity, weakly) while
 * its Devourer level is above 0 and forgets it otherwise; dead, unloaded and collected mobs drop out. search() runs the heal
 * search as shipped when an indexed mob other than the healed entity is in the same level, not removed, and its bounding
 * box meets the search box; otherwise it answers an empty list (the heal goes through untouched, as when the search finds no
 * Devourer). What differs: a Devourer that has not ticked since it got the trait or was loaded takes no heals until its
 * first tick.
 *
 * Flag: -Dbons_and_furious.fallentraitsDevourerIndex=false.
 */
public final class FtDevourers {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fallentraitsDevourerIndex", "true"));
    private static final List<WeakReference<LivingEntity>> SEEN = new ArrayList<>();
    private static volatile int size;
    private static volatile boolean announced;

    private FtDevourers() {
    }

    /** Called at the end of every MobTraitCap.tick. */
    public static void seen(MobTraitCap cap, LivingEntity mob) {
        if (!enabled || mob == null || mob.m_9236_().f_46443_) return;
        MobTrait devourer = FTTraits.DEVOURER.get();
        boolean is = cap.getTraitLevel(devourer) > 0;
        if (!is && size == 0) return;
        synchronized (SEEN) {
            int at = -1;
            for (int i = SEEN.size() - 1; i >= 0; i--) {
                LivingEntity e = SEEN.get(i).get();
                if (e == null || e.m_213877_()) {
                    SEEN.remove(i);
                    if (at > i) at--;
                } else if (e == mob) {
                    at = i;
                }
            }
            if (is && at < 0) SEEN.add(new WeakReference<>(mob));
            else if (!is && at >= 0) SEEN.remove(at);
            size = SEEN.size();
        }
    }

    /** Indexed mobs (harness). */
    public static int indexed() {
        synchronized (SEEN) {
            return SEEN.size();
        }
    }

    /** True when an indexed Devourer could be found by a search of {@code box} in {@code level} (the healed entity itself
     * included: the search's own predicate leaves it out, so a Devourer's own heal simply runs the search as shipped). */
    public static boolean mayFind(Level level, AABB box) {
        if (size == 0) return false;
        synchronized (SEEN) {
            for (WeakReference<LivingEntity> r : SEEN) {
                LivingEntity d = r.get();
                if (d != null && d.m_9236_() == level && !d.m_213877_() && d.m_20191_().m_82381_(box)) return true;
            }
        }
        return false;
    }

    /** The wrapped Level.getEntitiesOfClass(LivingEntity, box, predicate) of FTEvents.onDevourerHeal. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List search(Level level, Class<?> type, AABB box, Predicate pred, Operation<List> original) {
        if (!enabled || level.f_46443_ || mayFind(level, box)) return original.call(level, type, box, pred);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fallentraits_devourer_index applies (heals search for Devourers only near mobs seen with the trait)");
        }
        return Collections.emptyList();
    }
}
