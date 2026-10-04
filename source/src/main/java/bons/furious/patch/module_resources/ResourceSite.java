package bons.furious.patch.module_resources;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The replacement of one module_resources call site's ClassLoader.getResourceAsStream(name) (on 1.21.1 only Distant
 * Horizons; its switch class holds the flag and counters). Where GameLayerResources declines, the original call runs.
 * In shadow mode the class loader's own getResource(name) is also asked and compared (same URL or both null), and the
 * stream is opened from the original's URL.
 *
 * Ported to 1.21.1: unchanged (no game or library API here).
 */
public final class ResourceSite {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Set<String> ANNOUNCED = ConcurrentHashMap.newKeySet();

    private ResourceSite() {
    }

    public static InputStream stream(String key, ClassLoader cl, String name, Supplier<InputStream> original, boolean shadow,
                                     AtomicLong shadowChecks, AtomicLong shadowMismatches, AtomicLong answered, AtomicLong declined) {
        Object r = GameLayerResources.lookup(cl, name);
        if (r == GameLayerResources.NOT_HANDLED) {
            declined.incrementAndGet();
            return original.get();
        }
        URL url = (URL) r;
        answered.incrementAndGet();
        if (ANNOUNCED.add(key)) LOGGER.info("Bons and Furious: {} answers resource lookups from the game-layer listing index", key);
        if (shadow) {
            URL orig = cl.getResource(name);
            shadowChecks.incrementAndGet();
            if (!Objects.equals(orig == null ? null : orig.toExternalForm(), url == null ? null : url.toExternalForm())) {
                long n = shadowMismatches.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch for '{}': original {} vs index {}", key, name, orig, url);
            }
            return GameLayerResources.open(orig);
        }
        return GameLayerResources.open(url);
    }
}
