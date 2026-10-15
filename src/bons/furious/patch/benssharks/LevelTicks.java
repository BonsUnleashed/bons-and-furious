package bons.furious.patch.benssharks;

import java.lang.invoke.MethodHandle;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): one LevelTickEvent listener (stock Ben's had six, four of which walked
 * every entity of every dimension every tick). START: the Bleeding pass (both sides). END, server: the original SmoothTurns
 * motion and SeekingHandler trident code, in the original listener order, over the tracked sets only.
 */
public final class LevelTicks {
    private LevelTicks() {}

    interface LevelProc {
        void run(ServerLevel sl, LevelState st);
    }

    static volatile LevelProc[] ORDER = new LevelProc[0];
    static volatile MethodHandle TICK_TRIDENT;

    private static final Class<?>[] ALL_SMOOTH = concat(Table.ST1, Table.ST2, Table.ST3, Table.ST5);
    static final ClassValue<Boolean> SMOOTH = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            for (Class<?> k : ALL_SMOOTH) if (k.isAssignableFrom(c)) return Boolean.TRUE;
            return Boolean.FALSE;
        }
    };

    public static void onLevelTick(TickEvent.LevelTickEvent ev) {
        Level lvl = ev.level;
        if (ev.phase == TickEvent.Phase.START) {
            if (SharkState.LIVING) Bleed.pass(lvl, ((LevelStateHolder) lvl).bons$sharksState());
            return;
        }
        if (!SharkState.LEVEL || !(lvl instanceof ServerLevel sl)) return;
        LevelState st = ((LevelStateHolder) sl).bons$sharksState();
        for (LevelProc p : ORDER) p.run(sl, st);
    }

    /** One SmoothTurns listener: same class filter, same isInWater check, same original apply method. */
    static LevelProc smooth(Class<?>[] classes, boolean water, Consumer<LivingEntity> apply) {
        return (sl, st) -> {
            if (st.sharks.isEmpty()) return;
            for (Entity e : st.sharks.toArray(new Entity[0])) {
                if (!isAny(e, classes)) continue;
                if (water && !e.m_20069_()) continue;
                if (e instanceof LivingEntity le) {
                    apply.accept(le);
                    Stats.SMOOTH_APPLIED.increment();
                }
            }
        };
    }

    /** SeekingHandler.onWorldTick: snapshot the tridents, run the original private tickTrident on each. */
    static final LevelProc SEEK = (sl, st) -> {
        if (st.tridents.isEmpty()) return;
        for (Entity e : st.tridents.toArray(new Entity[0])) {
            try {
                TICK_TRIDENT.invoke(sl, (ThrownTrident) e);
                Stats.TRIDENT_TICKS.increment();
            } catch (Throwable t) {
                if (t instanceof Exception x) x.printStackTrace();   // as the original: catch Exception, print
                else throw (Error) t;
            }
        }
    };

    private static boolean isAny(Entity e, Class<?>[] classes) {
        for (Class<?> c : classes) if (c.isInstance(e)) return true;
        return false;
    }

    private static Class<?>[] concat(Class<?>[]... parts) {
        int n = 0;
        for (Class<?>[] p : parts) n += p.length;
        Class<?>[] out = new Class<?>[n];
        int i = 0;
        for (Class<?>[] p : parts) for (Class<?> c : p) out[i++] = c;
        return out;
    }
}
