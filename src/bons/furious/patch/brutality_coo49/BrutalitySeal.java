package bons.furious.patch.brutality_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.goo.brutality.util.SealUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch brutality_seal_type_no_throw (Brutality 0.8.5, MIT; both sides; since 1.0.49). Idea: Collections
 * Of Optimizations (brutality.sealTypeNoThrow), idea text only.
 *
 * EntityCapabilities$EntitySealTypeCap.serializeNBT writes the seal under "boosterType" while deserializeNBT reads
 * "seal_type", so the name it reads is always "" (CompoundTag.getString answers "" for a missing key): SEAL_TYPE.valueOf("")
 * throws IllegalArgumentException (a new exception with its stack trace) and the catch block sets NONE. That happens on
 * every load of an entity with the capability and, on the client, for every entity in every capability sync packet.
 *
 * What changes: the wrapped valueOf call answers NONE at once for the empty name. No enum constant can have an empty name
 * (a Java identifier is never empty), so valueOf("") throws for every build of the enum, and the try block then calls
 * setSealType(NONE), the call the catch block makes: same field value, same calls, no exception built. Every other name
 * (including null, which valueOf answers with NullPointerException) goes to the original call.
 *
 * -Dbons_and_furious.brutalitySealTypeNoThrow=false runs the original call for every name.
 */
public final class BrutalitySeal {
    public static final String KEY = "brutality_seal_type_no_throw";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalitySealTypeNoThrow", "true"));
    private static volatile boolean announced;

    private BrutalitySeal() {
    }

    /** The wrapped SEAL_TYPE.valueOf(name) of EntitySealTypeCap.deserializeNBT. */
    public static SealUtils.SEAL_TYPE valueOf(String name, Operation<SealUtils.SEAL_TYPE> original) {
        if (enabled && name != null && name.isEmpty()) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (Brutality's seal type capability reads an empty name without building an exception)", KEY);
            }
            return SealUtils.SEAL_TYPE.NONE;
        }
        return original.call(name);
    }
}
