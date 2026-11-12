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
 *
 * Since 1.0.49 a trade-off switch may carry a numeric value on the line directly under it,
 * "&lt;key&gt;.value=&lt;number&gt;" (an interval, cap, distance or budget the player can tune). Such a line is
 * never a switch: it is not counted in the start-up line, never published as a disabled
 * property and not reported as an unknown key. The switch's own code reads it with
 * {@link #intValue} or {@link #doubleValue}, and only while the switch is on; a missing line
 * takes the bundled default (and is added to the file like a missing switch), a value that does
 * not parse or lies outside the switch's range takes the switch's default with one warning that
 * names the line.
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
    /** Since 1.0.49: "&lt;key&gt;.value=&lt;number&gt;" is the numeric value of switch &lt;key&gt;, not a switch. */
    public static final String VALUE_SUFFIX = ".value";
    /** Since 1.0.49: -Dbons_and_furious.value.&lt;key&gt;=&lt;number&gt; sets a value for one run (it wins over the file). */
    public static final String PROP_VALUE_PREFIX = "bons_and_furious.value.";
    private static final java.util.regex.Pattern WHOLE_NUMBER = java.util.regex.Pattern.compile("[+-]?[0-9]+");
    private static final java.util.regex.Pattern DECIMAL_NUMBER =
            java.util.regex.Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<String, Boolean> STATE = new LinkedHashMap<>();
    /** Since 1.0.49: switch -> the bundled default of its value line (file order). */
    private static final Map<String, String> VALUE_DEFAULTS = new LinkedHashMap<>();
    /** Since 1.0.49: switch -> the raw value in effect (the user's file, else the bundled default) and where it came from. */
    private static final Map<String, String> VALUES = new LinkedHashMap<>();
    private static final Map<String, String> VALUE_ORIGIN = new LinkedHashMap<>();
    /** Since 1.0.49: checked values per call shape, and the switches already warned about (one warning each). */
    private static final Map<String, Number> RESOLVED = new java.util.HashMap<>();
    private static final Set<String> VALUE_WARNED = new java.util.HashSet<>();
    private static final Set<String> VALUE_FLAGGED = new java.util.HashSet<>();
    private static final Map<String, String> LEGACY_PROPERTIES = new LinkedHashMap<>();
    /** Switches removed from the mod, and why; an older config that still lists one gets an info line, not a warning. */
    private static final Map<String, String> RETIRED = Map.of(
            "engineering_industry_recipe_index", "moved into the Living Engineering addon itself in 1.0.20",
            "engineering_culture_controls", "moved into the Living Engineering addon itself in 1.0.20",
            "engineering_vat_growth_reuse", "moved into the Living Engineering addon itself in 1.0.20");
    /**
     * Since 1.0.49.2: switches whose bundled default went from true to false for safety, with the version and the reason.
     * The file keeps every switch as a written value, so a file made before that version still says "=true" and the
     * switch stayed on after every update. Such a file gets the new default once: the line becomes "=false" under a
     * "# [migrated] key:" comment, which also marks it as done, so a user who sets it back to true keeps it on.
     */
    private static final Map<String, String[]> DEFAULT_NOW_OFF = Map.of(
            "radium_experimental_tickets_spawning", new String[] {"1.0.40",
                    "it can crash next to mods that move entities off the server thread"});
    static final String MIGRATED_MARK = "# [migrated] ";
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
            // since 1.0.49: the switches were seeded with their bundled values before the file was touched (seedDefaults),
            // so a file that cannot be read or written leaves the "=false" switches off; before, every switch turned on
            if (STATE.isEmpty()) {
                LOGGER.error("Bons and Furious could not read its configuration; every optimization stays enabled", t);
            } else {
                List<String> off = new ArrayList<>();
                for (Map.Entry<String, Boolean> e : STATE.entrySet()) if (!e.getValue()) off.add(e.getKey());
                LOGGER.error("Bons and Furious could not read its configuration; every switch keeps its bundled default ({} of {} on{})",
                        STATE.size() - off.size(), STATE.size(), off.isEmpty() ? "" : "; off: " + String.join(", ", off), t);
            }
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

    /**
     * Since 1.0.49: the whole-number value of switch {@code key} ("key.value=..." directly under the switch line).
     * Returns {@code def} without reading anything while the switch is off (or disabled by a JVM flag). While it is on:
     * -Dbons_and_furious.value.&lt;key&gt; if given (one info line), else the user's line, else the bundled default; a value
     * that is not a plain whole number or lies outside {@code min..max} gives {@code def} and one warning naming the line.
     * Call it once (a static field or the first use) and keep the result: the file is read at start-up only, so the value
     * never changes during a session.
     */
    public static synchronized int intValue(String key, int def, int min, int max) {
        return resolveValue(key, def, min, max, false).intValue();
    }

    /** Since 1.0.49: as {@link #intValue}, for a decimal value (plain digits with an optional fraction and exponent). */
    public static synchronized double doubleValue(String key, double def, double min, double max) {
        return resolveValue(key, def, min, max, true).doubleValue();
    }

    private static Number resolveValue(String key, Number def, Number min, Number max, boolean decimal) {
        load();
        String shape = key + '\u0000' + (decimal ? 'd' : 'i') + '\u0000' + def + '\u0000' + min + '\u0000' + max;
        Number known = RESOLVED.get(shape);
        if (known != null) return known;
        Boolean on = STATE.get(key);
        Number out = def;
        if (on == null || on) {     // read only while the switch is on (an unknown switch counts as on, as in isEnabled)
            String raw = VALUES.get(key);
            String line = key + VALUE_SUFFIX + '=' + raw;
            String origin = "in " + VALUE_ORIGIN.get(key);
            String flag = System.getProperty(PROP_VALUE_PREFIX + key);
            if (flag != null) {
                raw = flag;
                line = "-D" + PROP_VALUE_PREFIX + key + '=' + flag;
                origin = "(JVM flag)";
                if (VALUE_FLAGGED.add(key)) LOGGER.info("Bons and Furious: {} value {} set by JVM flag -D{}{}", key, flag, PROP_VALUE_PREFIX, key);
            }
            if (raw == null) {
                warnValueOnce(key, "Bons and Furious: '{}' has no '{}{}' line in the bundled defaults; using {}",
                        key, key, VALUE_SUFFIX, def);
            } else {
                String v = raw.trim();
                Number parsed = null;
                try {
                    if (decimal && DECIMAL_NUMBER.matcher(v).matches()) {
                        double d = Double.parseDouble(v);
                        if (!Double.isInfinite(d) && !Double.isNaN(d) && d >= min.doubleValue() && d <= max.doubleValue()) parsed = d;
                    } else if (!decimal && WHOLE_NUMBER.matcher(v).matches()) {
                        long l = Long.parseLong(v);
                        if (l >= min.longValue() && l <= max.longValue()) parsed = (int) l;
                    }
                } catch (NumberFormatException e) {
                    parsed = null;   // more digits than a long holds: outside every range
                }
                if (parsed != null) {
                    out = parsed;
                } else {
                    warnValueOnce(key, "Bons and Furious: '{}' {} is not " + (decimal ? "a number" : "a whole number")
                            + " from {} to {}; using {}", line, origin, min, max, def);
                }
            }
        }
        RESOLVED.put(shape, out);
        return out;
    }

    private static void warnValueOnce(String key, String message, Object... args) {
        if (VALUE_WARNED.add(key)) LOGGER.warn(message, args);
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
        parseDefaults(bundled, defaults, VALUE_DEFAULTS);
        version = extractVersion(bundled);
        seedDefaults(defaults);

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
        List<String> migrated = new ArrayList<>();
        for (String[] entry : defaults) {
            String key = entry[0];
            // since 1.0.30: a key starts from its bundled value; a key the bundled defaults ship "=false" stays off until
            // the user enables it (a fresh file and a key missing from an older file take the bundled value; every
            // switch before 1.0.30 ships "=true", so they behave exactly as before)
            boolean byDefault = !"false".equalsIgnoreCase(entry[2]);
            boolean enabled = byDefault;
            String raw = file.getProperty(key);
            // since 1.0.49: the switch's value line (if it has one) is read as text here and checked only when the switch's
            // code asks for it; a missing line takes the bundled default and is added to the file like a missing switch
            String valueDefault = VALUE_DEFAULTS.get(key);
            String valueRaw = valueDefault == null ? null : file.getProperty(key + VALUE_SUFFIX);
            if (valueDefault != null) {
                VALUES.put(key, valueRaw != null ? valueRaw : valueDefault);
                VALUE_ORIGIN.put(key, valueRaw != null ? String.valueOf(source) : "the bundled defaults");
            }
            boolean addValue = existing != null && valueDefault != null && valueRaw == null;
            if (raw == null) {
                if (existing != null) {
                    append.append(entry[1]).append(key).append('=').append(byDefault).append('\n');
                    if (addValue) append.append(key).append(VALUE_SUFFIX).append('=').append(valueDefault).append('\n');
                    append.append('\n');
                }
            } else {
                if (addValue) {
                    append.append("# Value of ").append(key).append(" (missing from this file; added with its default)\n")
                            .append(key).append(VALUE_SUFFIX).append('=').append(valueDefault).append("\n\n");
                }
                String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
                if (v.equals("true")) {
                    enabled = true;
                    // since 1.0.49.2: "=true" left over from before the bundled default became false (see DEFAULT_NOW_OFF)
                    if (!byDefault && DEFAULT_NOW_OFF.containsKey(key) && !existing.contains(MIGRATED_MARK + key + ':')) {
                        enabled = false;
                        migrated.add(key);
                    }
                }
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
            // since 1.0.49: the value line of a switch that takes one is known; any other ".value" line is an unknown key
            if (name.endsWith(VALUE_SUFFIX) && VALUE_DEFAULTS.containsKey(name.substring(0, name.length() - VALUE_SUFFIX.length()))) continue;
            String retired = RETIRED.get(name);
            if (retired != null) LOGGER.info("Bons and Furious: '{}' in {} is a retired switch ({}); it is ignored", name, source, retired);
            else LOGGER.warn("Bons and Furious: unknown key '{}' in {} is ignored", name, source);
        }
        if (!migrated.isEmpty()) {
            boolean written = false;
            if (source.equals(configPath)) {
                try {
                    existing = writeMigrated(configPath, migrated);
                    written = true;
                } catch (IOException | RuntimeException e) {
                    LOGGER.warn("Bons and Furious could not mark {} as migrated in {} ({}); it stays off for this session", migrated, configPath, e.toString());
                }
            }
            for (String key : migrated) {
                LOGGER.info("Bons and Furious: {} was still on from an older default; its default is off since {} ({}){}; set it to true in {} to turn it back on",
                        key, DEFAULT_NOW_OFF.get(key)[0], DEFAULT_NOW_OFF.get(key)[1], written ? ", so it is now switched off in the file" : "", source);
            }
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
     * Since 1.0.49: every known switch starts at its bundled value (and a JVM disable flag), before the user's file is
     * read or created. When that file cannot be used (unreadable, a folder of that name, a read-only config folder, a
     * malformed unicode escape in it), load() keeps these values: the switches that ship "=false" (Radium's experimental spawning,
     * the background level.dat writer, the JEI deferral) stay off instead of turning on with every other key. A file that
     * loads overrides them below exactly as before.
     */
    private static void seedDefaults(List<String[]> defaults) {
        // STATE only: the bons_and_furious.disabled.<key> properties stay as they are until the loop below publishes them,
        // since that loop reads a property already set to true as a JVM flag (it would block a user's "=true")
        for (String[] entry : defaults) {
            String key = entry[0];
            STATE.put(key, !"false".equalsIgnoreCase(entry[2])
                    && !Boolean.getBoolean(LEGACY_DISABLED_PREFIX + key) && !Boolean.getBoolean(PROP_DISABLED_PREFIX + key));
        }
        // C1 (1.0.49): the value lines start at their bundled numbers the same way, so a switch that is on reads its bundled
        // value even when the file cannot be used (the opt-in "=false" switches that carry values stay off, see above)
        for (Map.Entry<String, String> v : VALUE_DEFAULTS.entrySet()) {
            VALUES.put(v.getKey(), v.getValue());
            VALUE_ORIGIN.put(v.getKey(), "the bundled defaults");
        }
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

    /**
     * Since 1.0.49.2: in the user's file, the LAST line that sets each key (the one Properties keeps) becomes "key=false"
     * under a "# [migrated] key: ..." comment. Everything else stays byte for byte (line endings, a leading BOM). Returns
     * the new text without the BOM, as readText would.
     */
    private static String writeMigrated(Path path, List<String> keys) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        boolean bom = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
        String text = new String(bytes, bom ? 3 : 0, bytes.length - (bom ? 3 : 0), StandardCharsets.UTF_8);
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 256 * keys.size());
        java.util.Set<Integer> done = new java.util.HashSet<>();
        java.util.Map<Integer, String> replace = new java.util.HashMap<>();
        for (String key : keys) {
            // Properties separators: "=", ":" or plain whitespace, each with optional whitespace around it
            java.util.regex.Pattern line = java.util.regex.Pattern.compile("^\\s*" + java.util.regex.Pattern.quote(key) + "(?:\\s*[=:]\\s*|\\s+)true\\s*$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            for (int i = lines.length - 1; i >= 0; i--) {
                String l = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
                if (line.matcher(l).matches()) {
                    if (!done.add(i)) break;
                    String[] why = DEFAULT_NOW_OFF.get(key);
                    replace.put(i, MIGRATED_MARK + key + ": off by default since " + why[0] + " (" + why[1] + "); this file still had the old"
                            + " default, so Bons and Furious " + version + " switched it off. Set it to true to turn it back on." + nl + key + "=false"
                            + (lines[i].endsWith("\r") ? "\r" : ""));
                    break;
                }
            }
        }
        if (replace.isEmpty()) throw new IOException("no \"=true\" line found for " + keys);
        for (int i = 0; i < lines.length; i++) {
            out.append(replace.getOrDefault(i, lines[i]));
            if (i < lines.length - 1) out.append('\n');
        }
        String result = out.toString();
        byte[] body = result.getBytes(StandardCharsets.UTF_8);
        byte[] all = body;
        if (bom) {
            all = new byte[body.length + 3];
            all[0] = (byte) 0xEF;
            all[1] = (byte) 0xBB;
            all[2] = (byte) 0xBF;
            System.arraycopy(body, 0, all, 3, body.length);
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, all);
        try {
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);   // only left over when the move failed (a read-only file, a file held open)
        }
        return result;
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

    /**
     * Collects (key, preceding comment block) pairs from the bundled defaults, in file order. Since 1.0.49 a
     * "&lt;switch&gt;.value=&lt;number&gt;" line goes into {@code values} (switch -> bundled value) instead: it is not a switch.
     */
    private static void parseDefaults(String text, List<String[]> out, Map<String, String> values) {
        StringBuilder comment = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ----")) { comment.setLength(0); comment.append(line).append('\n'); continue; }
            if (trimmed.startsWith("#")) { comment.append(line).append('\n'); continue; }
            if (trimmed.isEmpty()) { continue; }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String key = trimmed.substring(0, eq).trim();
            if (key.endsWith(VALUE_SUFFIX) && key.length() > VALUE_SUFFIX.length()) {
                values.put(key.substring(0, key.length() - VALUE_SUFFIX.length()), trimmed.substring(eq + 1).trim());
                comment.setLength(0);
                continue;
            }
            out.add(new String[] {key, comment.toString(), trimmed.substring(eq + 1).trim()});
            comment.setLength(0);
        }
        // a value line names a switch of the same file; one without its switch is dropped (the build checks the file)
        Set<String> switches = new LinkedHashSet<>();
        for (String[] entry : out) switches.add(entry[0]);
        values.keySet().retainAll(switches);
    }

    private static String extractVersion(String text) {
        int i = text.indexOf("Bons and Furious ");
        if (i < 0) return "?";
        int start = i + "Bons and Furious ".length();
        int end = text.indexOf(' ', start);
        return end > start ? text.substring(start, end) : "?";
    }

    /**
     * FML's config directory, else ./config with a WARN. 1.0.46: Forge 47.4.28 returns null here (where 47.4.16 threw)
     * when FMLPaths was never set up, as in an offline test harness; the game always sets it before any mixin plugin
     * loads. A null now takes the same fallback as the exception instead of failing the whole configuration load.
     */
    private static Path configDirectory() {
        Path dir = null;
        Throwable why = null;
        try {
            dir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            why = t;
        }
        if (dir != null) return dir;
        Path fallback = Paths.get("config").toAbsolutePath();
        LOGGER.warn("Bons and Furious: FMLPaths unavailable ({}); using {}", why != null ? why.toString() : "no config directory set", fallback);
        return fallback;
    }
}
