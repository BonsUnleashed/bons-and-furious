package bons.furious.patch.benssharks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.mcreator.sharks.entity.BarracudaEntity;
import net.mcreator.sharks.procedures.DolphinEatSardineProcedure;
import net.mcreator.sharks.procedures.IfWearingArmorProcedure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): replaces Ben's Sharks' 62 global LivingTickEvent listeners. Stock, every
 * one of them ran for every living entity in the world, every tick, on both sides. Now each Ben's entity runs, from the head
 * of its own baseTick (immediately after the point where Forge posts LivingTickEvent), exactly the original handlers whose
 * guard can accept it, in the original priority/registration order. Three guards admit entities Ben's does not own and get
 * their own narrow hooks: IfWearingArmor (from the barracuda's side, see armorPass), DolphinEatSardine (Dolphin.tick) and the
 * two Bleeding handlers (LevelTicks/Bleed).
 */
public final class Dispatch {
    private Dispatch() {}

    record Plan(Proc[] procs, boolean armor) {}

    static volatile Proc[] ORDER = new Proc[0];
    static volatile boolean ARMOR;
    static volatile boolean DOLPHIN;
    private static final ConcurrentHashMap<Class<?>, Plan> PLANS = new ConcurrentHashMap<>();

    /** HEAD of baseTick of every Ben's living entity (tick for GreaterAxodileEntity). */
    public static void sharkTick(LivingEntity e) {
        if (!SharkState.LIVING) return;
        Plan p = PLANS.get(e.getClass());
        if (p == null) p = PLANS.computeIfAbsent(e.getClass(), c -> plan(e));
        Stats.SHARK_TICKS.increment();
        if (p.armor) armorPass(e);
        Proc[] ps = p.procs;
        if (ps.length == 0) return;
        LivingEvent.LivingTickEvent ev = new LivingEvent.LivingTickEvent(e);
        for (Proc x : ps) x.run.accept(ev);
        Stats.PROC_CALLS.add(ps.length);
    }

    private static Plan plan(LivingEntity e) {
        List<Proc> l = new ArrayList<>();
        for (Proc p : ORDER) if (p.matches(e)) l.add(p);
        return new Plan(l.toArray(new Proc[0]), ARMOR && e instanceof BarracudaEntity);
    }

    /** For test rigs: which original handlers an entity class runs, in order. */
    public static List<String> planFor(LivingEntity e) {
        List<String> out = new ArrayList<>();
        Plan p = PLANS.get(e.getClass());
        if (p == null) p = plan(e);
        for (Proc x : p.procs) out.add(x.cls.substring(x.cls.lastIndexOf('.') + 1));
        if (p.armor) out.add(0, "IfWearingArmorProcedure(barracuda side)");
        return out;
    }

    /**
     * IfWearingArmor, stock: every living entity, every tick: 20 armour-slot comparisons, then (if it wears any vanilla armour
     * in water) an entity search + sort for barracudas within 8 blocks, which it re-targets. It can only ever act near a
     * barracuda, so the barracuda looks around instead and runs the ORIGINAL handler for each living entity close enough to
     * matter, once per entity per tick. The box (barracuda bounds + 8.5) contains every position whose 8-block cube reaches
     * the barracuda.
     */
    static void armorPass(LivingEntity barracuda) {
        Level lvl = barracuda.m_9236_();
        LevelState st = ((LevelStateHolder) lvl).bons$sharksState();
        long now = lvl.m_46467_();
        if (st.armorTick != now) {
            st.armorTick = now;
            st.armorDone.clear();
        }
        AABB box = barracuda.m_20191_().m_82400_(8.5D);
        List<LivingEntity> near = lvl.m_6443_(LivingEntity.class, box, x -> x != barracuda);
        boolean server = !lvl.f_46443_;
        for (LivingEntity c : near) {
            if (!st.armorDone.add(c.m_19879_())) continue;
            // stock, the handler only ran for entities that tick
            if (server && !((ServerLevel) lvl).m_143340_(c.m_20183_())) continue;
            Stats.ARMOR_CHECKS.increment();
            IfWearingArmorProcedure.onEntityTick(new LivingEvent.LivingTickEvent(c));
        }
    }

    /** HEAD of Dolphin.tick: DolphinEatSardine (its body returns at once on the client). */
    public static void dolphinTick(Dolphin d) {
        if (!SharkState.LIVING || !DOLPHIN || d.m_9236_().f_46443_) return;
        Stats.DOLPHIN_TICKS.increment();
        DolphinEatSardineProcedure.onEntityTick(new LivingEvent.LivingTickEvent(d));
    }
}
