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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Early configuration for Bons and Furious.
 *
 * Every optimization is applied while its target class is loaded, long before any mod
 * constructor runs, so the switches are read here: first from the Mixin config plugin
 * (which Forge initialises before any game or mod class is transformed) and again,
 * harmlessly, from the mod constructor. The effective state is published as JVM system
 * properties that the Forge coremod scripts read with ASMAPI.getSystemPropertyFlag:
 *
 *   bons_and_furious.config.loaded=true            the file was processed
 *   bons_and_furious.disabled.&lt;key&gt;=true            that optimization must not be applied
 *
 * A key that is disabled on the JVM command line (-Dbons_and_furious.disabled.key=true) stays
 * disabled regardless of the file. If anything goes wrong reading or writing the file,
 * every optimization stays enabled and the problem is logged; the mod never refuses to load.
 *
 * Up to 1.0.15 the mod used other names. They keep working: an existing
 * config/bons_pure_optimizations.properties is carried over into the new file on the first
 * start (switches set to false stay false) and then kept as ...properties.migrated;
 * -Dbons_pure.disabled.key=true still disables a key; -Dbons.pure.name=value flags are
 * applied as -Dbons_and_furious.name=value.
 */
public final class PureConfig {
    public static final String FILE_NAME = "bons_and_furious.properties";
    public static final String DEFAULT_RESOURCE = "/bons_and_furious.default.properties";
    public static final String PROP_LOADED = "bons_and_furious.config.loaded";
    public static final String PROP_DISABLED_PREFIX = "bons_and_furious.disabled.";
    static final String FLAG_PREFIX = "bons_and_furious.";
    static final String LEGACY_FILE_NAME = "bons_pure_optimizations.properties";
    static final String LEGACY_DISABLED_PREFIX = "bons_pure.disabled.";
    static final String LEGACY_FLAG_PREFIX = "bons.pure.";
    static final String MIGRATED_SUFFIX = ".migrated";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<String, Boolean> STATE = new LinkedHashMap<>();
    private static final Map<String, String> LEGACY_PROPERTIES = new LinkedHashMap<>();
    /** Switches removed from the mod, and why; an older config that still lists one gets an info line, not a warning. */
    private static final Map<String, String> RETIRED = Map.of(
            "engineering_industry_recipe_index", "moved into the Living Engineering addon itself in 1.0.20",
            "engineering_culture_controls", "moved into the Living Engineering addon itself in 1.0.20",
            "engineering_vat_growth_reuse", "moved into the Living Engineering addon itself in 1.0.20");
    private static volatile boolean loaded;
    private static Path configPath;
    private static String version = "?";

    static {
        LEGACY_PROPERTIES.put("frame_pacing", "bons_and_furious.framePacing");
        LEGACY_PROPERTIES.put("terrain_density_memo", "ac.terrain.enabled");
        LEGACY_PROPERTIES.put("ambientsounds_terrain_scan_bound", "bons_and_furious.ambientHeight");
    }

    private PureConfig() {}

    /** Loads the switches once. Safe to call repeatedly and from any thread. */
    public static synchronized void load() {
        if (loaded) return;
        try {
            adoptLegacyFlags();
            doLoad();
        } catch (Throwable t) {
            LOGGER.error("Bons and Furious could not read its configuration; every optimization stays enabled", t);
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

    /** -Dbons.pure.name=value (the flag spelling up to 1.0.15) is applied as -Dbons_and_furious.name=value. */
    private static void adoptLegacyFlags() {
        for (String name : System.getProperties().stringPropertyNames()) {
            if (!name.startsWith(LEGACY_FLAG_PREFIX)) continue;
            String current = FLAG_PREFIX + name.substring(LEGACY_FLAG_PREFIX.length());
            if (System.getProperty(current) != null) continue;
            String value = System.getProperty(name);
            System.setProperty(current, value);
            LOGGER.info("Bons and Furious: JVM flag -D{}={} uses the mod's former name; applied as -D{}={}", name, value, current, value);
        }
    }

    private static void doLoad() throws IOException {
        // 1. The bundled defaults document every key in order.
        List<String[]> defaults = new ArrayList<>(); // [key, comment block]
        String bundled = readResource();
        parseDefaults(bundled, defaults);
        version = extractVersion(bundled);

        // 2. Read the user's file, carry over the file from the mod's former name, or create it from the defaults.
        Path dir = configDirectory();
        configPath = dir.resolve(FILE_NAME);
        Path legacyPath = dir.resolve(LEGACY_FILE_NAME);
        Path source = configPath;
        if (!Files.isRegularFile(configPath) && Files.isRegularFile(legacyPath)) {
            try {
                migrate(legacyPath, bundled, defaults);
            } catch (IOException e) {
                LOGGER.warn("Bons and Furious could not carry {} over to {} ({}); reading {} for this session", legacyPath, configPath, e.toString(), legacyPath);
                source = legacyPath;
            }
        } else if (Files.isRegularFile(legacyPath)) {
            LOGGER.info("Bons and Furious: {} is from the mod's former name and is no longer read; the switches are in {}", legacyPath, configPath);
        }
        Properties file = new Properties();
        String existing = null;
        if (Files.isRegularFile(source)) {
            existing = readText(source);
            try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(existing.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)) {
                file.load(reader);
            }
        } else {
            Files.createDirectories(dir);
            Files.write(configPath, bundled.getBytes(StandardCharsets.UTF_8));
            LOGGER.info("Bons and Furious wrote the default configuration to {}", configPath);
        }

        // 3. Resolve every known key; append missing ones so the file stays complete after updates.
        StringBuilder append = new StringBuilder();
        List<String> disabled = new ArrayList<>();
        for (String[] entry : defaults) {
            String key = entry[0];
            // since 1.0.30: a key starts from its bundled value; a key the bundled defaults ship "=false" stays off until
            // the user enables it (a fresh file and a key missing from an older file take the bundled value; every
            // switch before 1.0.30 ships "=true", so they behave exactly as before)
            boolean byDefault = !"false".equalsIgnoreCase(entry[2]);
            boolean enabled = byDefault;
            String raw = file.getProperty(key);
            if (raw == null) {
                if (existing != null) append.append(entry[1]).append(key).append('=').append(byDefault).append("\n\n");
            } else {
                String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (v.equals("true")) enabled = true;
                else if (v.equals("false")) enabled = false;
                else LOGGER.warn("Bons and Furious: '{}' has invalid value '{}'; using {}", key, raw, byDefault);   // 1.0.34: the bundled default, not always true
            }
            // Name the flag as it was given; the second load also sees the property the first load published.
            String flag = Boolean.getBoolean(LEGACY_DISABLED_PREFIX + key) ? LEGACY_DISABLED_PREFIX
                    : Boolean.getBoolean(PROP_DISABLED_PREFIX + key) ? PROP_DISABLED_PREFIX : null;
            if (flag != null) {
                if (enabled) LOGGER.info("Bons and Furious: {} disabled by JVM flag -D{}{}=true", key, flag, key);
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
            if (STATE.containsKey(name)) continue;
            String retired = RETIRED.get(name);
            if (retired != null) LOGGER.info("Bons and Furious: '{}' in {} is a retired switch ({}); it is ignored", name, source, retired);
            else LOGGER.warn("Bons and Furious: unknown key '{}' in {} is ignored", name, source);
        }
        if (append.length() > 0 && source.equals(configPath)) {
            String sep = existing.endsWith("\n") ? "" : "\n";
            Files.write(configPath, (sep + "\n" + append).getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            LOGGER.info("Bons and Furious added missing switches to {}", configPath);
        }
        LOGGER.info("Bons and Furious {}: {} of {} optimizations enabled from {}{}", version, STATE.size() - disabled.size(), STATE.size(),
                source, disabled.isEmpty() ? "" : "; disabled: " + String.join(", ", disabled));
    }

    /**
     * Writes the new file from the bundled defaults with every switch the old file set to false still false, then keeps
     * the old file as ...properties.migrated (or leaves it in place when that name is taken). Unknown keys and invalid
     * values are not carried over; they were ignored before as well.
     */
    private static void migrate(Path legacyPath, String bundled, List<String[]> defaults) throws IOException {
        Properties old = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(readText(legacyPath).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)) {
            old.load(reader);
        }
        Set<String> known = new LinkedHashSet<>();
        for (String[] entry : defaults) known.add(entry[0]);
        Set<String> off = new LinkedHashSet<>();
        for (String key : known) {
            String raw = old.getProperty(key);
            if (raw != null && raw.trim().equalsIgnoreCase("false")) off.add(key);
        }
        StringBuilder text = new StringBuilder(bundled.length() + 64);
        for (String line : bundled.split("\n", -1)) {
            String trimmed = line.trim();
            int eq = trimmed.indexOf('=');
            if (!trimmed.startsWith("#") && eq > 0 && off.contains(trimmed.substring(0, eq).trim())) {
                line = trimmed.substring(0, eq).trim() + "=false";
            }
            text.append(line).append('\n');
        }
        text.setLength(text.length() - 1);
        Files.createDirectories(configPath.getParent());
        Files.write(configPath, text.toString().getBytes(StandardCharsets.UTF_8));
        Path kept = legacyPath.resolveSibling(LEGACY_FILE_NAME + MIGRATED_SUFFIX);
        String where;
        if (Files.exists(kept)) {
            where = legacyPath + " (left in place because " + kept.getFileName() + " already exists; it is no longer read)";
        } else {
            try {
                Files.move(legacyPath, kept);
                where = kept.toString();
            } catch (IOException e) {
                where = legacyPath + " (could not be renamed: " + e + "; it is no longer read)";
            }
        }
        LOGGER.info("Bons and Furious carried the switches over from {} (the mod's former name) to {}: {} of {} switched off{}; the old file is kept as {}",
                legacyPath.getFileName(), configPath, off.size(), known.size(), off.isEmpty() ? "" : " (" + String.join(", ", off) + ")", where);
    }

    private static String readText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        int skip = (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) ? 3 : 0;
        return new String(bytes, skip, bytes.length - skip, StandardCharsets.UTF_8);
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
            out.add(new String[] {trimmed.substring(0, eq).trim(), comment.toString(), trimmed.substring(eq + 1).trim()});
            comment.setLength(0);
        }
    }

    private static String extractVersion(String text) {
        int i = text.indexOf("Bons and Furious ");
        if (i < 0) return "?";
        int start = i + "Bons and Furious ".length();
        int end = text.indexOf(' ', start);
        return end > start ? text.substring(start, end) : "?";
    }

    private static Path configDirectory() {
        try {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            Path fallback = Paths.get("config").toAbsolutePath();
            LOGGER.warn("Bons and Furious: FMLPaths unavailable ({}); using {}", t.toString(), fallback);
            return fallback;
        }
    }
}
