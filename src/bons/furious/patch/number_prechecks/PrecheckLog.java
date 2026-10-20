package bons.furious.patch.number_prechecks;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/** Bons and Furious, group number_prechecks (since 1.0.45): the one INFO line per switch and the shadow-mode warnings. */
final class PrecheckLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PrecheckLog() {
    }

    static void applies(String key, String what, boolean shadow) {
        LOGGER.info("Bons and Furious: {} applies ({}){}", key, what, shadow ? " - shadow verification on" : "");
    }

    static void mismatch(String key, AtomicLong mismatches, String detail) {
        long m = mismatches.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: {}", key, m, detail);
    }
}
