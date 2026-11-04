package bons.furious.patch.architectury_mop49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch architectury_receiver_registration_lock (Architectury 9.2.14 for Forge, both sides; since 1.0.49).
 * No Architectury code here.
 *
 * Architectury's Forge NetworkManagerImpl keeps its packet receivers and packet transformers in four plain HashMaps that
 * registerS2CReceiver / registerC2SReceiver write. Forge constructs and sets up mods on several loading threads at once,
 * so two mods registering receivers in their constructors or setup at the same moment write those maps concurrently; a
 * HashMap written by two threads can lose an entry (that mod's packets then find no receiver) or break its table. The
 * two register methods now run one at a time under one lock; what each registers, and everything else, is unchanged.
 */
public final class ReceiverLock {
    /** Runtime switch; -Dbons_and_furious.architecturyReceiverRegistrationLock=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.architecturyReceiverRegistrationLock", "true"));
    /** The one lock both register methods take (ours: no other code synchronizes on it). */
    public static final Object LOCK = new Object();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private ReceiverLock() {
    }

    public static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: architectury_receiver_registration_lock: Architectury packet receivers register one at a time");
    }
}
