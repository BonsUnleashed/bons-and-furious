package bons.furious.patch.assetsprotector_decrypt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import javax.crypto.IllegalBlockSizeException;
import org.slf4j.Logger;

/**
 * Bons and Furious switch assetsprotector_undecryptable_skip (Assets Protector 1.0.0, mod id aes, GPL-3.0; both sides).
 *
 * <p>Assets Protector hooks every Resource.open: it reads the resource and, when the 44 bytes before its last 16 match a Base64
 * pattern, calls AESUtil.decrypt, which first derives a key with PBKDF2WithHmacSHA256 (65,536 iterations, slow while the JIT
 * is cold at boot) and then decrypts those 44 characters with AES/CBC/PKCS5Padding. AES/CBC can only decrypt input whose
 * length is a multiple of 16 bytes: when the 44 characters decode to any other length (33 bytes without padding, 31 with
 * "=="), the decryption always throws IllegalBlockSizeException after the key derivation, and Assets Protector's hook
 * catches it and hands out the original bytes. In this pack 30 of 389,209 resources trip the pattern and none is encrypted;
 * every one decodes to 33 bytes (10 of them data files, so 10 key derivations per data load: 5.1 s of data-loading worker time
 * on the profiling worker).
 *
 * <p>{@link #cannotDecrypt} gives that answer before the key derivation (the same characters, decoded the same way); the
 * switch then throws the same exception type at once. Input that is not valid Base64, too short to have a tail, or decodes
 * to a multiple of 16 bytes runs the original. The resource that comes out of Assets Protector's hook is the same in every
 * case.
 *
 * <p>The hook also compiles its Base64 regular expression again for every resource longer than 76 bytes (nearly all of the
 * 389,209). {@link #pattern} compiles that one expression once and hands the same Pattern back (java.util.regex.Pattern is
 * immutable and safe for concurrent use; the hook only calls matcher(...) on it, which makes a new Matcher each time). Any
 * other expression is compiled as shipped.
 *
 * <p>-Dbons_and_furious.assetsProtector=false keeps the original; .metrics=true counts derivations avoided and patterns reused;
 * .verify=true (rigs) lets every call run as shipped and counts, for the inputs the switch would refuse, any call that did not
 * end with IllegalBlockSizeException.
 */
public final class Undecryptable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.assetsProtector.verify");
    public static final boolean ACTIVE = !VERIFY && !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.assetsProtector", "true"));
    public static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.assetsProtector.metrics");
    public static final LongAdder REFUSED = new LongAdder(), PASSED = new LongAdder(), VERIFIED = new LongAdder(), WRONG = new LongAdder(),
            PATTERNS_REUSED = new LongAdder(), REFUSABLE_NANOS = new LongAdder();
    /** The expression Assets Protector 1.0.0's resource hook compiles on every call. */
    public static final String TAIL_PATTERN = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$";
    private static volatile Pattern tailPattern;
    private static volatile boolean announced;

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious assetsprotector_undecryptable_skip: calls "
                    + (VERIFY ? "the switch would refuse " : "refused before the key derivation ") + REFUSED.sum() + ", calls run as shipped " + PASSED.sum()
                    + ", compiled patterns reused " + PATTERNS_REUSED.sum()
                    + (VERIFY ? ", refusable calls compared " + VERIFIED.sum() + " (" + REFUSABLE_NANOS.sum() / 1_000_000 + " ms of shipped work), "
                            + "refusable calls that did not end with IllegalBlockSizeException " + WRONG.sum() : "")),
                    "Bons and Furious Assets Protector summary"));
        }
    }

    /** Pattern.compile(regex) inside Assets Protector's resource hook: its own expression compiled once, any other as shipped. */
    public static Pattern pattern(String regex, Operation<Pattern> original) {
        if (!ACTIVE || !TAIL_PATTERN.equals(regex)) return original.call(regex);
        Pattern p = tailPattern;
        if (p == null) {
            tailPattern = p = original.call(regex);
        } else if (METRICS) {
            PATTERNS_REUSED.increment();
        }
        return p;
    }

    private Undecryptable() {
    }

    /** True when AESUtil.decrypt(array) is certain to end with IllegalBlockSizeException (see the class comment). */
    public static boolean cannotDecrypt(byte[] array) {
        if (array == null || array.length < 60) return false;
        try {
            String encrypted = new String(Arrays.copyOfRange(array, array.length - 60, array.length - 16));
            return Base64.getDecoder().decode(encrypted).length % 16 != 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The exception AESUtil's decryption ends with for such input. */
    public static IllegalBlockSizeException blockSize() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: assetsprotector_undecryptable_skip applies (resources Assets Protector cannot decrypt are refused before its key derivation)");
        }
        return new IllegalBlockSizeException("Input length must be multiple of 16 when decrypting with padded cipher");
    }

    public static void count(boolean refused) {
        if (METRICS) (refused ? REFUSED : PASSED).increment();
    }

    /** Verify mode: a refusable call ended with {@code thrown} (null: it returned) after {@code nanos} of the shipped work. */
    public static void verified(Throwable thrown, long nanos) {
        VERIFIED.increment();
        REFUSABLE_NANOS.add(nanos);
        if (!(thrown instanceof IllegalBlockSizeException)) {
            WRONG.increment();
            LOGGER.error("Bons and Furious: assetsprotector_undecryptable_skip verify: a call the switch would refuse ended with {}", thrown == null ? "a result" : thrown.toString());
        }
    }
}
