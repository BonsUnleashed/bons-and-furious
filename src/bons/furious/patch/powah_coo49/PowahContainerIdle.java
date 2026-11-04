package bons.furious.patch.powah_coo49;

import java.lang.reflect.Method;

/**
 * Bons and Furious 1.0.49 switch powah_container_charge_idle_skip (Powah 5.0.11, server; kind change, off by default; helper
 * of PowahContainerIdleMixin). No Powah code is carried.
 *
 * What Powah does. ForgeEnvHandler.chargeItemsInContainer walks every slot of the container in front of an Energy Hopper on
 * every tick, also while the hopper holds no energy or charges at rate 0 (every offer is then receiveEnergy(0, false)), and
 * calls container.setChanged() whether or not anything was charged: for a chest that is a chunk marked unsaved and a
 * comparator update every tick, forever.
 *
 * What the switch changes (trade-off, off by default). With no energy or no charge rate (maxPerSlot <= 0 or maxTotal <= 0)
 * the call answers 0 without touching the container (no slot read, no item is offered 0, no setChanged); otherwise the
 * same walk as powah_container_charge_streamless (PowahContainerCharge.charge, the same calls as Powah's) and setChanged only
 * when something was charged. Observable: items whose receiveEnergy has side effects even for 0 (Powah's own items rewrite
 * their energy tag) are not touched while the hopper is empty, and the container is not marked changed (no neighbour
 * update, no extra chunk save) when nothing moved.
 *
 * Runtime flag bons_and_furious.powahContainerChargeIdleSkip (the config switch decides whether the mixin is in the game).
 * The streamless switch's mixin steps aside while this one is in the game and on ({@link #active}), so the two never both
 * answer one call, whichever of the two HEAD handlers runs first.
 */
public final class PowahContainerIdle {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.powahContainerChargeIdleSkip", "true"));
    /** The name of the marker method PowahContainerIdleMixin adds to ForgeEnvHandler (present only when that mixin applied). */
    public static final String MARKER = "bons$powahContainerIdleMarker";
    private static volatile int applied;   // 0 not looked yet, 1 the idle mixin is in the game, -1 it is not

    private PowahContainerIdle() {
    }

    /** True while the idle mixin is in the game (merged into {@code handlerClass}) and its runtime flag is on. */
    public static boolean active(Class<?> handlerClass) {
        if (!enabled) return false;
        int a = applied;
        if (a == 0) {
            a = -1;
            try {
                for (Method m : handlerClass.getDeclaredMethods()) {
                    if (m.getName().contains(MARKER)) {
                        a = 1;
                        break;
                    }
                }
            } catch (Throwable t) {
                a = -1;
            }
            applied = a;
        }
        return a == 1;
    }

    /** For the harness: forget the cached answer of {@link #active}. */
    public static void forget() {
        applied = 0;
    }
}
