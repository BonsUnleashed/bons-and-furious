package bons.furious.patch.benssharks;

import java.util.function.Consumer;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): BleedingImmunity (full chainmail removes Bleeding) and SharkBleed (a
 * bleeding shark takes 1 damage, then waits 60 ticks) were global LivingTickEvent listeners: 4 armour reads, or a fresh
 * ResourceLocation + TagKey, for every living entity every tick. Both only matter for entities that HAVE Bleeding. Bleeding is
 * ticked every tick by its own effect (isDurationEffectTick = true), so each entity whose Bleeding ticked (or was just
 * applied) is recorded and the two original handlers run for it, in their original order, at the start of the next level
 * tick: still before that entity's next effect tick, as stock. SharkBleed runs server-side only (client-side it only ever
 * reached a no-op hurt and a work queue the client never drains).
 */
public final class Bleed {
    private Bleed() {}

    record Step(Consumer<LivingEvent.LivingTickEvent> run, boolean serverOnly) {}

    static volatile MobEffect BLEEDING;
    static volatile Step[] ORDER = new Step[0];

    public static void onAdded(LivingEntity e, MobEffectInstance inst) {
        if (SharkState.LIVING && inst.m_19544_() == BLEEDING) state(e).bleedNow.add(e);
    }

    public static void onActiveTick(LivingEntity e) {
        if (SharkState.LIVING) state(e).bleedNow.add(e);
    }

    static boolean has(LivingEntity e) {
        MobEffect b = BLEEDING;
        return b != null && e.m_21023_(b);
    }

    private static LevelState state(LivingEntity e) {
        return ((LevelStateHolder) e.m_9236_()).bons$sharksState();
    }

    static void pass(Level lvl, LevelState st) {
        if (st.bleedNow.isEmpty()) return;
        LivingEntity[] list = st.bleedNow.toArray(new LivingEntity[0]);
        st.bleedNow.clear();
        boolean client = lvl.f_46443_;
        for (LivingEntity e : list) {
            if (e.m_213877_() || e.m_9236_() != lvl || !has(e)) continue;
            Stats.BLEED_PASSES.increment();
            LivingEvent.LivingTickEvent ev = new LivingEvent.LivingTickEvent(e);
            for (Step s : ORDER) {
                if (s.serverOnly && client) continue;
                s.run.accept(ev);
            }
        }
    }
}
