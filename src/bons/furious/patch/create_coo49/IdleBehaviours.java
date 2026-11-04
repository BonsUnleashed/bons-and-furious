package bons.furious.patch.create_coo49;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch create_idle_behaviour_tick_skip (Create 6.0.8, both sides). Exact variant.
 *
 * SmartBlockEntity.tick (every Create block entity, every tick, both sides) ends with forEachBehaviour(
 * BlockEntityBehaviour::tick): one megamorphic tick() call per behaviour. BlockEntityBehaviour's own tick() only counts a
 * private lazyTickCounter down and, every lazyTickRate + 1 ticks, calls lazyTick(), which is empty in the base class.
 * Most behaviour classes override neither (scroll values, filters, links, brackets, advancements, computer access, ...):
 * for them the call does nothing but move that private counter.
 *
 * The switch walks the same collection (getAllBehaviours().forEach, so order, a behaviour list changed during the walk and
 * exceptions behave exactly as before) and calls tick() only on behaviours whose runtime class overrides tick() or
 * lazyTick() somewhere below BlockEntityBehaviour (decided once per class, kept in a byte on each behaviour). Applies only
 * to block entity classes that keep SmartBlockEntity's forEachBehaviour and getAllBehaviours (decided once per class, kept
 * in a byte on each block entity); others run the original.
 * The one internal difference (exact variant): an idle behaviour's private lazy-tick countdown no longer moves. Only
 * BlockEntityBehaviour.tick reads it, and what it gates (the base lazyTick) is empty, so nothing observable changes.
 * Runtime flag -Dbons_and_furious.createIdleBehaviourTickSkip=false. Create is MIT; no Create code is carried.
 */
public final class IdleBehaviours {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.createIdleBehaviourTickSkip", "true"));
    private static volatile boolean announced;

    /** True when the behaviour class (or a superclass below BlockEntityBehaviour) declares tick() or lazyTick(). */
    private static final ClassValue<Boolean> ACTIVE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> c = type; c != null && c != BlockEntityBehaviour.class; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods())
                        if (m.getParameterCount() == 0 && (m.getName().equals("tick") || m.getName().equals("lazyTick"))) return true;
                }
                return false;
            } catch (Throwable t) {          // a class whose methods cannot be listed here: always ticked, as before
                return true;
            }
        }
    };

    /** True when the block entity class (or a superclass below SmartBlockEntity) declares forEachBehaviour or getAllBehaviours. */
    private static final ClassValue<Boolean> OWN_WALK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> c = type; c != null && c != SmartBlockEntity.class; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (m.getName().equals("forEachBehaviour") && m.getParameterCount() == 1) return true;
                        if (m.getName().equals("getAllBehaviours") && m.getParameterCount() == 0) return true;
                    }
                }
                return false;
            } catch (Throwable t) {          // methods cannot be listed: the original walk runs
                return true;
            }
        }
    };

    /** The tick walk: active behaviours tick, idle ones are passed over. */
    public static final Consumer<BlockEntityBehaviour> GATED_TICK = b -> {
        if (!((IdleBehaviourState) b).bons$idleBehaviour()) b.tick();
    };

    private IdleBehaviours() {
    }

    /** 1 = idle class, 2 = active class (cached in the behaviour). */
    public static byte classify(Class<?> behaviour) {
        if (!announced) announce();
        return ACTIVE.get(behaviour) ? (byte) 2 : (byte) 1;
    }

    /** 1 = keeps SmartBlockEntity's walk (the switch applies), 2 = its own walk (the original runs). */
    public static byte classifyEntity(Class<?> blockEntity) {
        return OWN_WALK.get(blockEntity) ? (byte) 2 : (byte) 1;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: create_idle_behaviour_tick_skip: Create behaviours that do nothing on tick are no longer ticked");
    }
}
