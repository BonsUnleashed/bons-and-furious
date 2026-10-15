package bons.furious.patch.benssharks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;

/**
 * benssharks_tick_listeners: server entity tracking (ServerLevel.EntityCallbacks onTrackingStart / onTrackingEnd): exactly
 * the span in which an entity is in the level's EntityLookup, i.e. in ServerLevel.getAllEntities(). The SmoothTurns and
 * SeekingHandler listeners walked getAllEntities() every tick looking for a handful of classes; these sets hold just those
 * entities, in the same (insertion) order.
 */
public final class Tracking {
    private Tracking() {}

    public static void start(Entity e) {
        Level lvl = e.m_9236_();
        if (lvl == null || lvl.f_46443_) return;
        LevelState st = ((LevelStateHolder) lvl).bons$sharksState();
        if (SharkState.LEVEL) {
            if (LevelTicks.SMOOTH.get(e.getClass())) st.sharks.add(e);
            if (e instanceof ThrownTrident) st.tridents.add(e);
        }
        // entities loaded from disk carry their effects without onEffectAdded
        if (SharkState.LIVING && e instanceof LivingEntity le && Bleed.has(le)) st.bleedNow.add(le);
    }

    public static void end(Entity e) {
        Level lvl = e.m_9236_();
        if (lvl == null || lvl.f_46443_) return;
        LevelState st = ((LevelStateHolder) lvl).bons$sharksState();
        st.sharks.remove(e);
        st.tridents.remove(e);
        if (e instanceof LivingEntity le) st.bleedNow.remove(le);
    }
}
