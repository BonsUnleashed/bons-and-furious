package bons.pure.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Early configuration for Bons Pure Optimizations.
 *
 * Every optimization is applied while its target class is loaded, long before any mod
 * constructor runs, so the switches are read here: first from the Mixin config plugin
 * (which Forge initialises before any game or mod class is transformed) and again,
 * harmlessly, from the mod constructor. The effective state is published as JVM system
 * properties that the Forge coremod scripts read with ASMAPI.getSystemPropertyFlag:
 *
 *   bons_pure.config.loaded=true            the file was processed
 *   bons_pure.disabled.&lt;key&gt;=true            that optimization must not be applied
 *
 * A key that is disabled on the JVM command line (-Dbons_pure.disabled.key=true) stays
 * disabled regardless of the file. If anything goes wrong reading or writing the file,
 * every optimization stays enabled and the problem is logged; the mod never refuses to load.
 */
public final class PureConfig {
    public static final String FILE_NAME = "bons_pure_optimizations.properties";
    public static final String DEFAULT_RESOURCE = "/bons_pure_optimizations.default.properties";
    public static final String PROP_LOADED = "bons_pure.config.loaded";
    public static final String PROP_DISABLED_PREFIX = "bons_pure.disabled.";
    private static final Logger LOGGER = LogManager.getLogger("Bons Pure Optimizations");
    private static final Map<String, Boolean> STATE = new LinkedHashMap<>();
    private static final Map<String, String> LEGACY_PROPERTIES = new LinkedHashMap<>();
    private static volatile boolean loaded;
    private static Path configPath;
    private static String version = "?";

    static {
        LEGACY_PROPERTIES.put("frame_pacing", "bons.pure.framePacing");
        LEGACY_PROPERTIES.put("terrain_density_memo", "ac.terrain.enabled");
        LEGACY_PROPERTIES.put("ambientsounds_terrain_scan_bound", "bons.pure.ambientHeight");
    }

    private PureConfig() {}

    /** Loads the switches once. Safe to call repeatedly and from any thread. */
    public static synchronized void load() {
        if (loaded) return;
        try {
            doLoad();
        } catch (Throwable t) {
            LOGGER.error("Bons Pure Optimizations could not read its configuration; every optimization stays enabled", t);
        } finally {
            loaded = true;
            System.setProperty(PROP_LOADED, "true");
        }
    }

    public static synchronized boolean isEnabled(String key) {
        load();
        Boolean value = STATE.get(key);
        return value == null || value;
    }

    public static synchronized Map<String, Boolean> snapshot() {
        load();
        return Collections.unmodifiableMap(new LinkedHashMap<>(STATE));
    }

    public static Path path() {
        return configPath;
    }

    public static String version() {
        return version;
    }

    private static void doLoad() throws IOException {
        // 1. The bundled defaults document every key in order.
        List<String[]> defaults = new ArrayList<>(); // [key, comment block]
        String bundled = readResource();
        parseDefaults(bundled, defaults);
        version = extractVersion(bundled);

        // 2. Read the user's file, or create it from the defaults.
        Path dir = configDirectory();
        configPath = dir.resolve(FILE_NAME);
        Properties file = new Properties();
        String existing = null;
        if (Files.isRegularFile(configPath)) {
            byte[] bytes = Files.readAllBytes(configPath);
            int skip = (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) ? 3 : 0;
            existing = new String(bytes, skip, bytes.length - skip, StandardCharsets.UTF_8);
            try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(bytes, skip, bytes.length - skip), StandardCharsets.UTF_8)) {
                file.load(reader);
            }
        } else {
            Files.createDirectories(dir);
            Files.write(configPath, bundled.getBytes(StandardCharsets.UTF_8));
            LOGGER.info("Bons Pure Optimizations wrote the default configuration to {}", configPath);
        }

        // 3. Resolve every known key; append missing ones so the file stays complete after updates.
        StringBuilder append = new StringBuilder();
        List<String> disabled = new ArrayList<>();
        for (String[] entry : defaults) {
            String key = entry[0];
            boolean enabled = true;
            String raw = file.getProperty(key);
            if (raw == null) {
                if (existing != null) append.append(entry[1]).append(key).append("=true\n\n");
            } else {
                String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (v.equals("true")) enabled = true;
                else if (v.equals("false")) enabled = false;
                else LOGGER.warn("Bons Pure Optimizations: '{}' has invalid value '{}'; using true", key, raw);
            }
            if (Boolean.getBoolean(PROP_DISABLED_PREFIX + key)) {
                if (enabled) LOGGER.info("Bons Pure Optimizations: {} disabled by JVM flag -D{}{}=true", key, PROP_DISABLED_PREFIX, key);
                enabled = false;
            }
            STATE.put(key, enabled);
            if (enabled) {
                System.setProperty(PROP_DISABLED_PREFIX + key, "false");
            } else {
                System.setProperty(PROP_DISABLED_PREFIX + key, "true");
                disabled.add(key);
                String legacy = LEGACY_PROPERTIES.get(key);
                if (legacy != null && System.getProperty(legacy) == null) System.setProperty(legacy, "false");
            }
        }
        for (String name : file.stringPropertyNames()) {
            if (!STATE.containsKey(name)) LOGGER.warn("Bons Pure Optimizations: unknown key '{}' in {} is ignored", name, configPath);
        }
        if (append.length() > 0) {
            String sep = existing.endsWith("\n") ? "" : "\n";
            Files.write(configPath, (sep + "\n" + append).getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            LOGGER.info("Bons Pure Optimizations added missing switches to {}", configPath);
        }
        LOGGER.info("Bons Pure Optimizations {}: {} of {} optimizations enabled from {}{}", version, STATE.size() - disabled.size(), STATE.size(),
                configPath, disabled.isEmpty() ? "" : "; disabled: " + String.join(", ", disabled));
    }

    private static String readResource() throws IOException {
        try (InputStream in = PureConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) throw new IOException("missing bundled resource " + DEFAULT_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Collects (key, preceding comment block) pairs from the bundled defaults, in file order. */
    private static void parseDefaults(String text, List<String[]> out) {
        StringBuilder comment = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ----")) { comment.setLength(0); comment.append(line).append('\n'); continue; }
            if (trimmed.startsWith("#")) { comment.append(line).append('\n'); continue; }
            if (trimmed.isEmpty()) { continue; }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            out.add(new String[] {trimmed.substring(0, eq).trim(), comment.toString()});
            comment.setLength(0);
        }
    }

    private static String extractVersion(String text) {
        int i = text.indexOf("Bons Pure Optimizations ");
        if (i < 0) return "?";
        int start = i + "Bons Pure Optimizations ".length();
        int end = text.indexOf(' ', start);
        return end > start ? text.substring(start, end) : "?";
    }

    private static Path configDirectory() {
        try {
            return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            Path fallback = Paths.get("config").toAbsolutePath();
            LOGGER.warn("Bons Pure Optimizations: FMLPaths unavailable ({}); using {}", t.toString(), fallback);
            return fallback;
        }
    }
}
