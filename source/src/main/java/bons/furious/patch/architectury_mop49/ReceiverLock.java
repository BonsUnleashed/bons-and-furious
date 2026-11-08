package bons.furious.patch.architectury_mop49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch architectury_receiver_registration_lock (Architectury 13.0.11 for NeoForge 1.21.1, both sides;
 * since 1.0.49). No Architectury code here.
 *
 * On 1.21.1 Architectury keeps its packet types, receivers, codecs and packet transformers in eight plain HashMaps of
 * dev.architectury.impl.NetworkAggregator (C2S_ and S2C_ TYPE, RECEIVER, CODECS, TRANSFORMERS), written by its four public
 * registration methods (registerReceiver twice, registerS2CType twice, through the private registerC2SReceiver /
 * registerS2CReceiver). NeoForge constructs and sets up mods on several loading threads at once, so two mods registering
 * receivers in their constructors or setup at the same moment write those maps concurrently; a HashMap written by two
 * threads can lose an entry (that mod's packets then find no receiver or no codec) or break its table. The registration
 * methods now run one at a time under one lock (re-entered by registerS2CType(ResourceLocation, List), which calls the
 * other registerS2CType); what each registers, and everything else, is unchanged.
 */
public final class ReceiverLock {
    /** Runtime switch; -Dbons_and_furious.architecturyReceiverRegistrationLock=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.architecturyReceiverRegistrationLock", "true"));
    /** The one lock the registration methods take (ours: no other code synchronizes on it). */
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
