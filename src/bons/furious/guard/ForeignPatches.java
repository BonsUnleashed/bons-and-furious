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

    /** Every mixin the mod's mixin configs (manifest MixinConfigs, as Forge 47 reads them) list for this side. */
    private static List<String> listed(String modId) throws Exception {
        List<String> known = LISTED.get(modId);
        if (known != null) return known;
        List<String> out = new ArrayList<>();
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
                JsonObject cfg = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                String pkg = cfg.get("package").getAsString();
                for (String list : new String[] {"mixins", client ? "client" : "server"}) {
                    if (cfg.has(list) && cfg.get(list).isJsonArray())
                        for (JsonElement e : cfg.getAsJsonArray(list)) out.add(pkg + "." + e.getAsString());
                }
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

    private static Path configDirectory() {
        try {
            return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            return Path.of("config").toAbsolutePath();
        }
    }
}
