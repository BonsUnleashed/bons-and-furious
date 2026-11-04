package bons.furious.patch.arseng_coo49;

import java.lang.reflect.Method;
import java.util.ArrayList;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group arseng_coo49 (Ars Energistique 1.2.0 on Ars Nouveau 4.12.7). Helper of the group's
 * mixins; Ars Energistique is LGPL-3.0, no code of it is carried (the logic here is ours).
 *
 * <p>arseng_relay_splitter_listener_leak (fix, on by default): Ars Energistique replaces the relay splitter's two list
 * passes (once a second) with its own method, which asks every listed neighbour for its source capability and adds a
 * listener to that capability on every pass, so that a capability invalidated during the pass marks its position stale.
 * The listener is never removed: a neighbour's capability that stays valid collects one more listener every second, each
 * holding that pass's stale list, for as long as both blocks live, and runs them all when it is finally invalidated. With
 * the fix a present capability's listener is not added: the pass keeps it, and right before it walks its stale list it runs
 * every kept listener whose capability was invalidated during the pass, as invalidate would have run it (an absent
 * capability runs it at once, as before). The stale list gets the same positions; the capabilities' sets never grow. The kept
 * pairs live in one reused list per splitter.
 *
 * <p>arseng_generic_inv_wrapper_gate (trade-off, off by default): Ars Energistique attaches a second capability provider
 * to EVERY block entity, which answers the source capability by asking that block entity for AE2's generic internal
 * inventory. A block entity class that never overrides getCapability can only have that inventory through another mod's
 * attached capability; with the switch such block entities do not get the wrapper (one provider less per block entity, in
 * every capability lookup on it). What can change: a block entity that gets AE2's generic inventory only from another
 * mod's attached provider is no longer a source tile for Ars Energistique.
 *
 * <p>Runtime flags (=false: off): bons_and_furious.arsengRelaySplitterListenerLeak, .arsengGenericInvWrapperGate.
 */
public final class ArsEngRelays {
    static final String LEAK_KEY = "arseng_relay_splitter_listener_leak", GATE_KEY = "arseng_generic_inv_wrapper_gate";
    public static volatile boolean forgetListeners = flag("arsengRelaySplitterListenerLeak");
    public static volatile boolean gateInvWrapper = flag("arsengGenericInvWrapperGate");
    /** Statistics (plain fields): listeners kept out of the optionals' sets and run at the pass's end check, wrappers left
     *  out / attached by the gate. */
    public static long listenersDeferred, listenersRun, wrappersSkipped, wrappersKept;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announcedLeak, announcedGate;

    private ArsEngRelays() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /**
     * Instead of the pass's LazyOptional.addListener: for a present optional the pair goes to the splitter's kept list and
     * true is answered (the listener does not join the optional's set); false = run the original (an absent optional runs
     * the listener at once, as addListener does).
     */
    public static boolean defer(LazyOptional<?> cap, NonNullConsumer<?> listener, ArrayList<Object> kept) {
        if (!forgetListeners || cap == null || !cap.isPresent()) return false;
        kept.add(cap);
        kept.add(listener);
        listenersDeferred++;
        if (!announcedLeak) {
            announcedLeak = true;
            LOGGER.info("Bons and Furious: {}: relay splitter passes check their neighbours' capabilities instead of adding listeners", LEAK_KEY);
        }
        return true;
    }

    /**
     * Right before the pass walks its stale list: a kept listener whose optional was invalidated meanwhile runs now; the
     * list is cleared (a pass that threw before this point leaves pairs whose listeners can only reach that pass's own
     * stale list: they are checked and dropped here on the next pass).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void runInvalidated(ArrayList<Object> kept) {
        for (int i = 0; i + 1 < kept.size(); i += 2) {
            LazyOptional cap = (LazyOptional) kept.get(i);
            if (!cap.isPresent()) {
                ((NonNullConsumer) kept.get(i + 1)).accept(cap);
                listenersRun++;
            }
        }
        kept.clear();
    }
    /** Block entity classes that override getCapability (they may answer AE2's generic inventory themselves). */
    private static final ClassValue<Boolean> OWN_CAPS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                for (Class<?> k = c; k != null && k != BlockEntity.class; k = k.getSuperclass()) {
                    for (Method m : k.getDeclaredMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        if (m.getName().equals("getCapability") && p.length == 2 && p[0] == Capability.class && p[1] == Direction.class) return true;
                    }
                }
                return false;
            } catch (Throwable t) {
                return true;                        // unknown: keep the wrapper
            }
        }
    };

    /** At the generic wrapper's addCapability call: true = left out (the block entity class cannot answer it itself). */
    public static boolean skipInvWrapper(AttachCapabilitiesEvent<?> event) {
        if (!gateInvWrapper || event == null || !(event.getObject() instanceof BlockEntity be) || OWN_CAPS.get(be.getClass())) {
            if (gateInvWrapper) wrappersKept++;
            return false;
        }
        wrappersSkipped++;
        if (!announcedGate) {
            announcedGate = true;
            LOGGER.info("Bons and Furious: {}: the generic inventory source wrapper is attached only to block entities with their own capabilities", GATE_KEY);
        }
        return true;
    }
}
