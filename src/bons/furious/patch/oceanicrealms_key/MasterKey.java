package bons.furious.patch.oceanicrealms_key;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import javax.crypto.SecretKey;
import org.slf4j.Logger;

/**
 * Bons and Furious switch oceanicrealms_master_key_once (Oceanic Realms 1.0.7, All Rights Reserved; both sides, the work is on the
 * client's resource reload).
 *
 * <p>Oceanic Realms decrypts its own encrypted assets (111 files with an "OCEANICR" header, AES/GCM) on every resource reload,
 * and every decryption first calls AESUtil.getMasterKey, which derives the same key from a password and a salt that are
 * constants of the class, with PBKDF2WithHmacSHA256 (65,536 iterations): 111 identical derivations per reload (0.9 to 3.1 s of
 * resource-reload worker time). The key is a function of those constants alone, so the first key derived is kept and handed
 * out again; a SecretKeySpec is immutable and the callers only use it to initialise a cipher. A call that throws is not kept.
 *
 * <p>-Dbons_and_furious.oceanicKey=false keeps the original; .metrics=true counts derivations avoided; .verify=true (rigs)
 * derives every key as shipped and compares its bytes with the kept one.
 */
public final class MasterKey {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.oceanicKey.verify");
    public static final boolean ACTIVE = !VERIFY && !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oceanicKey", "true"));
    public static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.oceanicKey.metrics");
    public static final LongAdder KEPT = new LongAdder(), DERIVED = new LongAdder(), VERIFIED = new LongAdder(), WRONG = new LongAdder(),
            DERIVE_NANOS = new LongAdder();
    private static volatile SecretKey key;

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious oceanicrealms_master_key_once: keys derived " + DERIVED.sum()
                    + " (" + DERIVE_NANOS.sum() / 1_000_000 + " ms)"
                    + ", keys " + (VERIFY ? "that would come from memory " : "from memory ") + KEPT.sum()
                    + (VERIFY ? ", derivations compared " + VERIFIED.sum() + ", that differ " + WRONG.sum() : "")),
                    "Bons and Furious Oceanic Realms key summary"));
        }
    }

    private MasterKey() {
    }

    public static SecretKey get(Operation<SecretKey> original) {
        SecretKey k = key;
        if (k != null && ACTIVE) {
            if (METRICS) KEPT.increment();
            return k;
        }
        long t0 = METRICS ? System.nanoTime() : 0L;
        SecretKey fresh = original.call();
        if (METRICS) {
            DERIVED.increment();
            DERIVE_NANOS.add(System.nanoTime() - t0);
        }
        if (k == null) {
            if (fresh != null && (ACTIVE || VERIFY)) {
                key = fresh;
                LOGGER.info("Bons and Furious: oceanicrealms_master_key_once applies (Oceanic Realms' asset key is derived once){}",
                        VERIFY ? " - verify mode, every key still derived and compared" : "");
            }
        } else if (VERIFY) {
            if (METRICS) KEPT.increment();
            VERIFIED.increment();
            if (fresh == null || !Arrays.equals(fresh.getEncoded(), k.getEncoded()) || !fresh.getAlgorithm().equals(k.getAlgorithm())) {
                WRONG.increment();
                LOGGER.error("Bons and Furious: oceanicrealms_master_key_once verify: a derived key differs from the kept one");
            }
        }
        return fresh;
    }
}
