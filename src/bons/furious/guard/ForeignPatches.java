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
import java.util.Set;
import java.util.jar.Manifest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.transformer.Config;

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
 *
 * Since 1.0.45 the patch must also really be applied: the other mod's own mixin config plugin is asked, through Mixin's
 * public API, whether it applies that mixin (IMixinConfigPlugin.shouldApplyMixin(target, mixin), the call Mixin itself
 * makes while it prepares that config). Collections Of Optimizations 4.7 and later leave out their own copies of patches
 * that Bons and Furious also makes; with the earlier rule our switch stepped aside for a copy that never ran, so neither
 * mod ran the patch (KNOWLEDGE 2026-10-19). Asking the plugin follows whatever the other mod decides, in any version:
 *
 *  - the plugin applies the mixin to at least one of its targets, or the config has no plugin (Mixin then applies every
 *    mixin it lists): the patch is active, as before;
 *  - the plugin leaves the mixin out of every target, the same answer twice in a row: the patch is not active, our switch
 *    applies (one INFO line) and the cancel line still leaves the other mod's mixin out should it reach a class anyway;
 *  - the plugin cannot be asked (its config is not registered with Mixin or not selected yet, the mixin's targets cannot
 *    be read, the plugin throws) or answers differently when asked again: the rule above decides alone, as before (one
 *    WARN line).
 *
 * Mixin removes a config from Mixins.getConfigs() when it selects it, before the plugin's onLoad, so a config selected
 * already is found through the names Mixin registered for the environment (MixinEnvironment.getMixinConfigs()) and
 * Config.create(name, environment), which returns Mixin's own handle of a registered config. Every config is selected,
 * and its plugin loaded, before Mixin prepares any config's mixins, which is where our switches decide.
 */
final class ForeignPatches {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<String, List<String>> LISTED = new HashMap<>();          // mod id -> mixins listed for this side
    private static final Map<String, Map<String, String>> LISTED_IN = new HashMap<>(); // mod id -> mixin -> the config listing it
    private static final Map<String, Map<String, String>> OPTIONS = new HashMap<>();  // config file -> dotted key -> value
    private static final Map<String, Asked> ASKED = new HashMap<>();                  // other mod's mixin -> its plugin's answer
    private static final Set<String> LOGGED = new java.util.HashSet<>();

    /** What the other mod's mixin plugin answered for its mixin (since 1.0.45). */
    enum Verdict { APPLIES, LEFT_OUT, UNKNOWN }

    record Asked(Verdict verdict, String detail) {}

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
            // since 1.0.45: and the other mod's own mixin plugin really applies it (asked once per mixin; see the class comment)
            Asked a = asked(y);
            if (a.verdict() == Verdict.LEFT_OUT) {
                if (LOGGED.add("left|" + y.key() + "|" + y.mixin()))
                    LOGGER.info("Bons and Furious: {} applies: {} {}'s own copy ({}) is left out by its mixin plugin ({}), so only this switch's copy runs",
                            y.key(), y.name(), version, y.shortMixin(), a.detail());
                return null;
            }
            return y.name() + " " + version + " makes the same change (" + y.shortMixin() + setting + ")";
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: could not check {}'s {} for {} ({}); {} applies as usual", y.name(), y.shortMixin(), y.key(), t.toString(), y.key());
            return null;
        }
    }

    /**
     * Since 1.0.45: the reason the other mod's plugin gave for leaving this mixin out, when that was its answer; null
     * otherwise (not asked, applied, or not askable). Guards.shouldCancel uses it to tell a mixin that reached a class
     * although its plugin had left it out (the plugin answered Mixin differently than it answered us).
     */
    static synchronized String leftOutByPlugin(Yield y) {
        Asked a = ASKED.get(y.mixin());
        return a != null && a.verdict() == Verdict.LEFT_OUT ? a.detail() : null;
    }

    /** The other mod's plugin's answer for the yield's mixin, asked once per mixin; one WARN line when it cannot be asked. */
    private static Asked asked(Yield y) {
        Asked known = ASKED.get(y.mixin());
        if (known != null) return known;
        Asked a;
        try {
            a = ask(y);
        } catch (Throwable t) {
            a = new Asked(Verdict.UNKNOWN, "its mixin plugin threw " + t);
        }
        ASKED.put(y.mixin(), a);
        if (a.verdict() == Verdict.UNKNOWN)
            LOGGER.warn("Bons and Furious: could not ask {}'s mixin plugin about {} for {} ({}); {} follows {}'s config alone, as before",
                    y.name(), y.shortMixin(), y.key(), a.detail(), y.key(), y.name());
        else
            LOGGER.debug("Bons and Furious: {}'s mixin plugin for {} (asked for {}): {} - {}", y.name(), y.shortMixin(), y.key(), a.verdict(), a.detail());
        return a;
    }

    /**
     * Asks the plugin of the mixin config that lists the yield's mixin, for each target the mixin declares, the way Mixin
     * asks it (MixinInfo: plugin.shouldApplyMixin(declared target, mixin class)), each question twice.
     */
    private static Asked ask(Yield y) {
        Map<String, String> where = LISTED_IN.get(y.mod());
        String name = where == null ? null : where.get(y.mixin());
        if (name == null) return new Asked(Verdict.UNKNOWN, "no mixin config of " + y.mod() + " lists it");
        IMixinConfig config = registeredConfig(name);
        if (config == null) return new Asked(Verdict.UNKNOWN, "its mixin config " + name + " is not registered with Mixin");
        IMixinConfigPlugin plugin;
        try {
            plugin = config.getPlugin();
        } catch (NullPointerException e) {   // Mixin has not selected the config yet: no plugin handle exists
            return new Asked(Verdict.UNKNOWN, "its mixin config " + name + " is not selected yet");
        }
        if (plugin == null) return new Asked(Verdict.APPLIES, "its mixin config " + name + " has no plugin, so Mixin applies every mixin it lists");
        List<String> targets = Guards.declaredTargets(y.mixin());
        if (targets == null || targets.isEmpty()) return new Asked(Verdict.UNKNOWN, "the targets of " + y.shortMixin() + " could not be read");
        List<String> applied = new ArrayList<>();
        for (String target : targets) {
            boolean first = plugin.shouldApplyMixin(target, y.mixin());
            boolean second = plugin.shouldApplyMixin(target, y.mixin());
            if (first != second)
                return new Asked(Verdict.UNKNOWN, "its mixin plugin answered " + first + ", then " + second + " for " + target);
            if (first) applied.add(target);
        }
        String plugins = plugin.getClass().getName();
        if (!applied.isEmpty())
            return new Asked(Verdict.APPLIES, plugins.substring(plugins.lastIndexOf('.') + 1) + " applies it to " + String.join(", ", applied));
        return new Asked(Verdict.LEFT_OUT, plugins.substring(plugins.lastIndexOf('.') + 1) + " leaves it out of "
                + (targets.size() == 1 ? targets.get(0) : "all " + targets.size() + " targets"));
    }

    /**
     * Mixin's own handle of the registered config with this name, or null when Mixin never registered it. A config still
     * waiting to be selected is in Mixins.getConfigs(); a selected one was removed from there, and Config.create(name,
     * environment) returns the handle Mixin keeps for every registered name (it creates nothing for a registered name,
     * which is why the name is first looked up in the environments' registered lists).
     */
    static IMixinConfig registeredConfig(String name) {
        for (Config c : Mixins.getConfigs()) if (name.equals(c.getName())) return c.getConfig();
        MixinEnvironment[] envs = {MixinEnvironment.getDefaultEnvironment(), MixinEnvironment.getCurrentEnvironment()};
        for (MixinEnvironment env : envs) {
            if (env == null || !env.getMixinConfigs().contains(name)) continue;
            Config c = Config.create(name, env);
            return c == null ? null : c.getConfig();
        }
        return null;
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
     * Since 1.0.30: the installed mod that refines {@code foreignMixin} through MixinSquared's @TargetHandler, as "NAME
     * VERSION (MIXIN)", or null. The annotation names the refined mixin class as a string, so a mixin class containing that
     * name is such a refinement (TaCZ Tweaks' crawl.LivingEntityMixinMixin on TaCZ's LivingEntityMixin). Read once for every
     * mixin a switch may cancel, and only in the mods that can use @TargetHandler: those that bundle MixinSquared or depend
     * on it or on the mod that ships one of those mixins. A mod is never counted for a mixin it ships itself.
     */
    static synchronized String handlerUser(String foreignMixin, Map<String, String> cancelled) {
        if (handlerUsers == null) handlerUsers = scanHandlerUsers(cancelled);
        return handlerUsers.get(foreignMixin);
    }

    /**
     * cancelled: every foreign mixin a switch may cancel -> the mod that ships it.
     *
     * Since 1.0.32 the scan finds the same refinements with far less work (it was over a second of every start, client
     * and server, in a 500-mod pack): only mixins whose mod is installed are searched for (a mixin of a mod that is not
     * installed is never loaded, so nothing can refine it); a class file is decoded once as ISO-8859-1, which maps byte i
     * to char i, and searched with String.indexOf, after one test for a part all the names share (a class without that
     * part holds none of them); and a mod file that cannot be read makes only the mixins it could refine step aside
     * instead of every switch with a cancel line. Mixin configs with JSON null values are read the way Mixin reads them.
     */
    private static Map<String, String> scanHandlerUsers(Map<String, String> cancelled) {
        Map<String, String> users = new HashMap<>();
        long start = System.nanoTime();
        var loading = net.minecraftforge.fml.loading.LoadingModList.get();
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
                List<String> ids = new ArrayList<>();
                for (var m : mods) ids.add(m.getModId());
                if (ids.contains("bons_and_furious")) continue;
                List<String> open = new ArrayList<>();
                for (Map.Entry<String, String> c : live.entrySet())
                    if (!users.containsKey(c.getKey()) && !ids.contains(c.getValue())) open.add(c.getKey());
                if (open.isEmpty()) continue;
                var file = info.getFile();
                try {
                    if (!mayUseTargetHandler(file, mods, cancelled.values())) continue;
                    files++;
                    for (Path cls : mixinClassFiles(file)) {
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
                    String what = mods.isEmpty() ? file.getFileName() : mods.get(0).getDisplayName() + " " + mods.get(0).getVersion();
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
            return net.minecraftforge.fml.loading.LoadingModList.get().getModFileById(modId) != null;
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
    private static boolean mayUseTargetHandler(net.minecraftforge.forgespi.locating.IModFile file,
                                               List<net.minecraftforge.forgespi.language.IModInfo> mods,
                                               java.util.Collection<String> owners) throws Exception {
        for (var m : mods)
            for (var d : m.getDependencies())
                if (d.getModId().equals("mixinsquared") || owners.contains(d.getModId())) return true;
        Path meta = file.findResource("META-INF", "jarjar", "metadata.json");
        return Files.isRegularFile(meta) && Files.readString(meta, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT).contains("mixinsquared");
    }

    /** Every mixin class the jar's mixin configs (manifest MixinConfigs) name for either side, plus, for a config with a
     *  plugin, every class in its package (a plugin may add mixins the lists do not name). */
    private static List<Path> mixinClassFiles(net.minecraftforge.forgespi.locating.IModFile file) throws Exception {
        List<Path> out = new ArrayList<>();
        Path mf = file.findResource("META-INF", "MANIFEST.MF");
        if (!Files.isRegularFile(mf)) return out;
        String configs;
        try (InputStream in = Files.newInputStream(mf)) {
            configs = new Manifest(in).getMainAttributes().getValue("MixinConfigs");
        }
        if (configs == null) return out;
        for (String name : configs.split(",")) {
            Path p = file.findResource(name.trim());
            if (!Files.isRegularFile(p)) continue;
            JsonElement parsed = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) continue;
            JsonObject cfg = parsed.getAsJsonObject();
            String pkg = string(cfg, "package");
            if (pkg == null) continue;   // since 1.0.32 a JSON null here is skipped, not an error that stopped the whole scan
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
            var file = net.minecraftforge.fml.loading.LoadingModList.get().getModFileById(modId);
            return file == null ? null : file.getMods().stream().filter(m -> m.getModId().equals(modId)).findFirst()
                    .map(m -> m.getVersion().toString()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Every mixin the mod's mixin configs (manifest MixinConfigs, as Forge 47 reads them) list for this side. Since 1.0.45
     * also records which config lists each one (LISTED_IN), whose plugin is asked; the list itself is unchanged.
     */
    private static List<String> listed(String modId) throws Exception {
        List<String> known = LISTED.get(modId);
        if (known != null) return known;
        List<String> out = new ArrayList<>();
        Map<String, String> where = new HashMap<>();
        var file = net.minecraftforge.fml.loading.LoadingModList.get().getModFileById(modId).getFile();
        Path mf = file.findResource("META-INF", "MANIFEST.MF");
        if (Files.isRegularFile(mf)) {
            String configs;
            try (InputStream in = Files.newInputStream(mf)) {
                configs = new Manifest(in).getMainAttributes().getValue("MixinConfigs");
            }
            boolean client = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();
            for (String name : configs == null ? new String[0] : configs.split(",")) {
                Path p = file.findResource(name.trim());
                if (!Files.isRegularFile(p)) continue;
                JsonElement parsed = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) continue;
                JsonObject cfg = parsed.getAsJsonObject();
                String pkg = string(cfg, "package");
                if (pkg == null) continue;
                String prefix = pkg.isEmpty() ? "" : pkg + ".";
                for (String list : new String[] {"mixins", client ? "client" : "server"})
                    for (String e : strings(cfg, list)) {
                        out.add(prefix + e);
                        where.putIfAbsent(prefix + e, name.trim());
                    }
            }
        }
        LISTED_IN.put(modId, where);
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
                String line = raw.replace("﻿", "").trim();
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

    /** FML's config directory, else ./config (1.0.46: also when Forge 47.4.28 returns null offline, see PureConfig). */
    private static Path configDirectory() {
        try {
            Path dir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
            if (dir != null) return dir;
        } catch (Throwable t) {
            // offline: no FML
        }
        return Path.of("config").toAbsolutePath();
    }
}
