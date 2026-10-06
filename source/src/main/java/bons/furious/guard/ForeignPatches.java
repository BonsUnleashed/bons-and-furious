package bons.furious.guard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Patches another mod makes to the same code, the same way, as one of our switches (the "yield" lines of the guard
 * table, from patches/compat.json). Exactly one copy may run: two copies of the frame-rate limiter wait twice per frame,
 * and two @Overwrites of one method leave a "Method overwrite conflict" warning and one of them unused.
 *
 *  - The other mod's patch is active: our switch steps aside (Guards.State.YIELD) and leaves the class to it.
 *  - Otherwise our switch applies, and the other mod's mixin is left out (Guards.shouldCancel, MixinSquared), so that
 *    switching the other mod's option on while the game runs cannot add a second copy.
 *
 * "Active" is decided the way the other mod decides it itself, before any class is transformed: the mod is installed, its
 * own mixin config lists the mixin for this side, none of the mods it steps aside for is installed, and its master switch
 * and the patch's option are on in its config file (or the file does not say, and the default is on). The option is read
 * once at start, like our own switches.
 */
final class ForeignPatches {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<String, List<String>> LISTED = new HashMap<>();          // mod id -> mixins listed for this side
    private static final Map<String, Map<String, String>> OPTIONS = new HashMap<>();  // config file -> dotted key -> value

    /** One patch of another mod that does what our switch {@code key} does. */
    record Yield(String key, String mod, String name, String mixin, String config, String master, String option,
                 boolean byDefault, List<String> unlessMods) {
        String shortMixin() {
            return mixin.substring(mixin.lastIndexOf('.') + 1);
        }
    }

    private ForeignPatches() {}

    /** guard-table line: yield key mod name mixin config master option default unless ("-" = none). */
    static Yield parse(String[] f) {
        return new Yield(f[1], f[2], f[3], f[4], dash(f[5]), dash(f[6]), dash(f[7]), Boolean.parseBoolean(f[8]),
                f[9].equals("-") ? List.of() : List.of(f[9].split(",")));
    }

    private static String dash(String s) {
        return s.equals("-") ? null : s;
    }

    /** The first of the switch's yields whose patch is active, as a YIELD decision; null when our switch may apply. */
    static synchronized Guards.Decision yieldDecision(List<Yield> yields) {
        if (yields == null) return null;
        for (Yield y : yields) {
            String why = activeBecause(y);
            if (why != null) return new Guards.Decision(Guards.State.YIELD, why);
        }
        return null;
    }

    /** Why the other mod's patch is active ("Collections Of Optimizations 4.4 makes the same change ..."), or null. */
    static synchronized String activeBecause(Yield y) {
        try {
            String version = modVersion(y.mod());
            if (version == null || !listed(y.mod()).contains(y.mixin())) return null;
            for (String other : y.unlessMods()) if (modVersion(other) != null) return null;
            String setting = "";
            if (y.option() != null) {
                Map<String, String> opts = options(y.config());
                if (y.master() != null && !on(opts.get(y.master()), true)) return null;
                if (!on(opts.get(y.option()), y.byDefault())) return null;
                setting = ", option " + y.option() + " on";
            }
            return y.name() + " " + version + " makes the same change (" + y.shortMixin() + setting + ")";
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: could not check {}'s {} for {} ({}); {} applies as usual", y.name(), y.shortMixin(), y.key(), t.toString(), y.key());
            return null;
        }
    }

    /** True when the other mod lists the mixin for this side (cancelling one it never loads is pointless and not logged). */
    static synchronized boolean listedHere(Yield y) {
        try {
            return modVersion(y.mod()) != null && listed(y.mod()).contains(y.mixin());
        } catch (Throwable t) {
            return false;
        }
    }

    private static Map<String, String> handlerUsers;   // foreign mixin -> "NAME VERSION (MIXIN)" of a mod that refines it

    /**
     * Since 1.0.30 (NeoForge loading API): the installed mod that refines
     * {@code foreignMixin} through MixinSquared's @TargetHandler, as "NAME VERSION (MIXIN)", or null. The annotation names
     * the refined mixin class as a string, so a mixin class containing that name is such a refinement (TaCZ Tweaks'
     * crawl.LivingEntityMixinMixin on TaCZ's LivingEntityMixin, on Forge 1.20.1). Read once for every mixin a switch may
     * cancel, and only in the mods that can use @TargetHandler: those that bundle MixinSquared or depend on it or on the
     * mod that ships one of those mixins. A mod is never counted for a mixin it ships itself.
     */
    static synchronized String handlerUser(String foreignMixin, Map<String, String> cancelled) {
        if (handlerUsers == null) handlerUsers = scanHandlerUsers(cancelled);
        return handlerUsers.get(foreignMixin);
    }

    /**
     * cancelled: every foreign mixin a switch may cancel -> the mod that ships it.
     *
     * Since 1.0.33 the scan finds the same refinements with far less work (it was over a second of every start, client
     * and server, in a 500-mod pack): only mixins whose mod is installed are searched for (a mixin of a mod that is not
     * installed is never loaded, so nothing can refine it); a class file is decoded once as ISO-8859-1, which maps byte i
     * to char i, and searched with String.indexOf, after one test for a part all the names share (a class without that
     * part holds none of them); and a mod file that cannot be read makes only the mixins it could refine step aside
     * instead of every switch with a cancel line. Mixin configs with JSON null values are read the way Mixin reads them.
     */
    private static Map<String, String> scanHandlerUsers(Map<String, String> cancelled) {
        Map<String, String> users = new HashMap<>();
        long start = System.nanoTime();
        var loading = net.neoforged.fml.loading.LoadingModList.get();
        if (loading == null) return users;   // not running under FML (offline tools): no installed mod to read
        Map<String, String> live = new LinkedHashMap<>();   // the cancellable mixins of installed mods, in table order
        for (Map.Entry<String, String> c : cancelled.entrySet())
            if (mayBeInstalled(c.getValue())) live.put(c.getKey(), c.getValue());
        int files = 0, classes = 0;
        long bytes = 0;
        if (!live.isEmpty()) {
            Map<String, String> needles = new LinkedHashMap<>();   // mixin -> its UTF-8 bytes as an ISO-8859-1 string
            for (String mixin : live.keySet()) needles.put(mixin, latin1(mixin.getBytes(StandardCharsets.UTF_8)));
            String shared = sharedPart(needles.values());
            for (var info : loading.getModFiles()) {
                var mods = info.getMods();
                if (mods.isEmpty()) continue;
                List<String> ids = new ArrayList<>();
                for (var m : mods) ids.add(m.getModId());
                if (ids.contains("bons_and_furious")) continue;
                List<String> open = new ArrayList<>();
                for (Map.Entry<String, String> c : live.entrySet())
                    if (!users.containsKey(c.getKey()) && !ids.contains(c.getValue())) open.add(c.getKey());
                if (open.isEmpty()) continue;
                try {
                    if (!mayUseTargetHandler(info, mods, cancelled.values())) continue;
                    files++;
                    for (Path cls : mixinClassFiles(info)) {
                        byte[] b = Files.readAllBytes(cls);
                        classes++;
                        bytes += b.length;
                        String text = latin1(b);
                        if (shared != null && text.indexOf(shared) < 0) continue;
                        for (String mixin : open) {
                            if (users.containsKey(mixin) || text.indexOf(needles.get(mixin)) < 0) continue;
                            var mod = mods.get(0);
                            String name = cls.toString().replace('\\', '/');
                            name = name.substring(name.lastIndexOf('/') + 1).replace(".class", "");
                            users.put(mixin, mod.getDisplayName() + " " + mod.getVersion() + " (" + name + ")");
                        }
                    }
                } catch (Throwable t) {
                    String what = mods.get(0).getDisplayName() + " " + mods.get(0).getVersion();
                    LOGGER.warn("Bons and Furious: could not check {} for MixinSquared refinements ({}); the switches that replace a mixin "
                            + "it could refine step aside", what, t.toString());
                    for (String mixin : open) users.putIfAbsent(mixin, what + " (could not be checked)");
                }
            }
        }
        LOGGER.debug("Bons and Furious: checked {} mod files ({} classes, {} KB) for MixinSquared refinements of {} of {} cancellable mixins "
                + "in {} ms; {} found", files, classes, bytes / 1024, live.size(), cancelled.size(), (System.nanoTime() - start) / 1_000_000, users.size());
        return users;
    }

    /** False only for a known mod id that is not installed ("?" or a missing owner keeps the mixin in the search). */
    private static boolean mayBeInstalled(String modId) {
        if (modId == null || modId.isEmpty() || modId.equals("?")) return true;
        try {
            return net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId) != null;
        } catch (Throwable t) {
            return true;
        }
    }

    /** Byte i of the input is char i of the result, so String.indexOf finds a needle exactly where a byte search would. */
    private static String latin1(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    /**
     * The longest part (at least 3 chars) that every needle contains, or null: a text without it contains none of them.
     * Needles are fully qualified mixin class names, so this is typically ".mixin." or longer.
     */
    static String sharedPart(java.util.Collection<String> needles) {
        String shortest = null;
        for (String n : needles) if (shortest == null || n.length() < shortest.length()) shortest = n;
        if (shortest == null) return null;
        for (int len = shortest.length(); len >= 3; len--) {
            for (int i = 0; i + len <= shortest.length(); i++) {
                String part = shortest.substring(i, i + len);
                boolean all = true;
                for (String n : needles) if (n.indexOf(part) < 0) { all = false; break; }
                if (all) return part;
            }
        }
        return null;
    }

    /** A config value as Mixin reads it: a JSON string, or null when the key is missing, JSON null or not a string. */
    private static String string(JsonObject cfg, String key) {
        JsonElement e = cfg.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    /** The string entries of a config list (mixins / client / server); JSON nulls and other values are skipped. */
    private static List<String> strings(JsonObject cfg, String key) {
        JsonElement e = cfg.get(key);
        if (e == null || !e.isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive() && x.getAsJsonPrimitive().isString()) out.add(x.getAsString());
        return out;
    }

    /** The jar bundles MixinSquared (Jar-in-Jar), or one of its mods depends on MixinSquared or on one of the owners. */
    private static boolean mayUseTargetHandler(net.neoforged.fml.loading.moddiscovery.ModFileInfo info,
                                               List<net.neoforged.neoforgespi.language.IModInfo> mods,
                                               java.util.Collection<String> owners) throws Exception {
        for (var m : mods)
            for (var d : m.getDependencies())
                if (d.getModId().equals("mixinsquared") || owners.contains(d.getModId())) return true;
        Path meta = info.getFile().findResource("META-INF", "jarjar", "metadata.json");
        return Files.isRegularFile(meta) && Files.readString(meta, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT).contains("mixinsquared");
    }

    /** Every mixin class the mod file's mixin configs name for either side ([[mixins]] in neoforge.mods.toml, plus the
     *  legacy manifest MixinConfigs attribute), and, for a config with a plugin, every class in its package (a plugin may
     *  add mixins the lists do not name). */
    private static List<Path> mixinClassFiles(net.neoforged.fml.loading.moddiscovery.ModFileInfo info) throws Exception {
        List<Path> out = new ArrayList<>();
        var file = info.getFile();
        java.util.Set<String> configs = new java.util.LinkedHashSet<>();
        for (var entry : info.getConfig().getConfigList("mixins"))
            entry.<String>getConfigElement("config").ifPresent(c -> configs.add(c.trim()));
        Path mf = file.findResource("META-INF", "MANIFEST.MF");
        if (Files.isRegularFile(mf)) {
            try (InputStream in = Files.newInputStream(mf)) {
                String legacy = new Manifest(in).getMainAttributes().getValue("MixinConfigs");
                if (legacy != null) for (String n : legacy.split(",")) if (!n.isBlank()) configs.add(n.trim());
            }
        }
        for (String name : configs) {
            Path p = file.findResource(name);
            if (!Files.isRegularFile(p)) continue;
            JsonElement parsed = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) continue;
            JsonObject cfg = parsed.getAsJsonObject();
            String pkg = string(cfg, "package");
            if (pkg == null) continue;   // since 1.0.33 a JSON null here is skipped, not an error that stopped the whole scan
            String prefix = pkg.isEmpty() ? "" : pkg + ".";
            for (String list : new String[] {"mixins", "client", "server"}) {
                for (String e : strings(cfg, list)) {
                    Path c = file.findResource((prefix + e).replace('.', '/') + ".class");
                    if (Files.isRegularFile(c)) out.add(c);
                }
            }
            if (string(cfg, "plugin") != null) {
                Path dir = file.findResource(pkg.split("\\."));
                if (Files.isDirectory(dir)) {
                    try (var walk = Files.walk(dir)) {
                        walk.filter(x -> x.toString().endsWith(".class")).forEach(out::add);
                    }
                }
            }
        }
        return out;
    }

    /** TOML booleans as Forge writes them; anything else (or nothing) is the default. */
    private static boolean on(String value, boolean byDefault) {
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        return byDefault;
    }

    static String modVersion(String modId) {
        try {
            var file = net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId);
            return file == null ? null : file.getMods().stream().filter(m -> m.getModId().equals(modId)).findFirst()
                    .map(m -> m.getVersion().toString()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Every mixin registered through NeoForge's [[mixins]] metadata for this side and installed requiredMods. */
    private static List<String> listed(String modId) throws Exception {
        List<String> known = LISTED.get(modId);
        if (known != null) return known;
        List<String> out = new ArrayList<>();
        var info = net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId);
        var file = info.getFile();
        {
            boolean client = net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
            for (var entry : info.getConfig().getConfigList("mixins")) {
                List<String> required = entry.<List<String>>getConfigElement("requiredMods").orElse(List.of());
                if (required.stream().anyMatch(id -> modVersion(id) == null)) continue;
                String name = entry.<String>getConfigElement("config").orElseThrow();
                Path p = file.findResource(name.trim());
                if (!Files.isRegularFile(p)) continue;
                JsonElement parsed = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) continue;
                JsonObject cfg = parsed.getAsJsonObject();
                String pkg = string(cfg, "package");
                if (pkg == null) continue;
                String prefix = pkg.isEmpty() ? "" : pkg + ".";
                for (String list : new String[] {"mixins", client ? "client" : "server"})
                    for (String e : strings(cfg, list)) out.add(prefix + e);
            }
        }
        LISTED.put(modId, out);
        return out;
    }

    /** The mod's config file as dotted key -> raw value ("[vanilla]" + "paceFramesBeforeSwap = true"); empty when absent. */
    private static Map<String, String> options(String fileName) throws Exception {
        Map<String, String> known = OPTIONS.get(fileName);
        if (known != null) return known;
        Map<String, String> out = new LinkedHashMap<>();
        Path p = configDirectory().resolve(fileName);
        if (Files.isRegularFile(p)) {
            String section = "";
            for (String raw : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String line = raw.replace("ï»¿", "").trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[")) {
                    String name = line.replace("[", "").replace("]", "").trim();
                    section = unquote(name);
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = unquote(line.substring(0, eq).trim());
                String value = line.substring(eq + 1).trim();
                int comment = value.indexOf('#');
                if (comment >= 0 && !value.startsWith("\"")) value = value.substring(0, comment).trim();
                out.put(section.isEmpty() ? key : section + "." + key, value);
            }
        }
        OPTIONS.put(fileName, out);
        return out;
    }

    private static String unquote(String s) {
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    private static Path configDirectory() {
        try {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            return Path.of("config").toAbsolutePath();
        }
    }
}
