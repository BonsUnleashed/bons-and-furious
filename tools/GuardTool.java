import bons.furious.guard.Fingerprint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Guard table tool for the build.
 *
 *   GuardTool fill <patches dir> <roots...>             compute the sha256 of every guard entry from the given jars
 *                                                       (the tested mod builds) and write it into patches/*.json
 *   GuardTool emit <patches dir> <resources dir> <out.tsv> <roots...>
 *                                                       check every recorded sha256 against the given jars, check that
 *                                                       the mixin configs and the patch files list the same mixins,
 *                                                       then write bons_and_furious.guards.tsv
 * Roots are jars or directories, first hit wins (Forge-patched vanilla before the SRG jar, then the mods).
 */
public final class GuardTool {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] a) throws Exception {
        String mode = a[0];
        Path patches = Paths.get(a[1]);
        int rootStart = mode.equals("fill") ? 2 : 4;
        List<Path> roots = new ArrayList<>();
        for (int i = rootStart; i < a.length; i++) roots.add(Paths.get(a[i]));
        Classes classes = new Classes(roots);
        List<Path> files = jsonFiles(patches, ".json");
        if (mode.equals("fill")) { fill(files, classes); return; }
        emit(files, Paths.get(a[2]), Paths.get(a[3]), classes);
    }

    /** A single file, or every file with the suffix in a directory (sorted). */
    static List<Path> jsonFiles(Path p, String suffix) throws Exception {
        if (Files.isRegularFile(p)) return List.of(p);
        try (var s = Files.list(p)) { return s.filter(x -> x.getFileName().toString().endsWith(suffix)).sorted().toList(); }
    }

    static void fill(List<Path> files, Classes classes) throws Exception {
        int n = 0;
        for (Path f : files) {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> k : root.getAsJsonObject("keys").entrySet()) {
                for (JsonElement g : k.getValue().getAsJsonObject().getAsJsonArray("guard")) {
                    JsonObject o = g.getAsJsonObject();
                    String fp = fingerprint(classes, o);
                    if (fp == null) throw new IllegalStateException(f.getFileName() + " " + k.getKey() + ": " + o + " not found in the given jars");
                    o.addProperty("sha256", fp);
                    n++;
                }
            }
            Files.writeString(f, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        }
        System.out.println("filled " + n + " fingerprints in " + files.size() + " file(s)");
    }

    static void emit(List<Path> files, Path resources, Path out, Classes classes) throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, String> mixinKey = new TreeMap<>();
        Map<String, String> mod = new TreeMap<>();
        Map<String, List<String>> guard = new TreeMap<>();
        Map<String, String> cancel = new TreeMap<>();
        for (Path f : files) {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> k : root.getAsJsonObject("keys").entrySet()) {
                String key = k.getKey();
                JsonObject o = k.getValue().getAsJsonObject();
                if (mod.containsKey(key)) problems.add(key + " is defined twice");
                mod.put(key, o.get("mod").getAsString());
                for (JsonElement m : arr(o, "mixins")) {
                    String prev = mixinKey.put(m.getAsString(), key);
                    if (prev != null) problems.add(m.getAsString() + " is listed under " + prev + " and " + key);
                }
                for (JsonElement c : arr(o, "cancel")) cancel.put(c.getAsString(), key);
                List<String> lines = new ArrayList<>();
                for (JsonElement g : arr(o, "guard")) {
                    JsonObject e = g.getAsJsonObject();
                    String want = e.has("sha256") ? e.get("sha256").getAsString() : "";
                    String got = fingerprint(classes, e);
                    String where = key + " " + e.get("class").getAsString() + "." + e.get("method").getAsString() + e.get("desc").getAsString();
                    if (got == null) problems.add(where + ": not found in the local jars");
                    else if (!got.equals(want)) problems.add(where + ": the local jar differs from the tested build (" + want + " expected, " + got + " found)");
                    lines.add(e.get("class").getAsString() + "\t" + e.get("method").getAsString() + "\t" + e.get("desc").getAsString() + "\t" + want);
                }
                if (lines.isEmpty()) problems.add(key + " has no guard entries");
                guard.put(key, lines);
            }
        }
        // Every mixin a Bons and Furious config lists must belong to exactly one guarded switch, and vice versa.
        Set<String> configured = new LinkedHashSet<>();
        for (Path c : jsonFiles(resources, ".mixins.json")) {
            JsonObject cfg = JsonParser.parseString(Files.readString(c)).getAsJsonObject();
            String pkg = cfg.get("package").getAsString();
            for (String list : new String[] {"mixins", "client", "server"})
                for (JsonElement m : arr(cfg, list)) configured.add(pkg + "." + m.getAsString());
        }
        Set<String> legacy = Set.of("agentcraft.pure.mixin.DataCommandsMixin",
                "agentcraft.terrain.mixin.NoiseChunkMixin", "agentcraft.terrain.mixin.HolderHolderMixin",
                "bons.pure.pacing.mixin.MinecraftPacingMixin", "agentcraft.consolidated.bons_valkyrien_fixes.mixin.TrackworkResources");
        for (String m : configured) if (!legacy.contains(m) && !mixinKey.containsKey(m)) problems.add(m + " is in a mixin config but in no patches/*.json key");
        for (String m : mixinKey.keySet()) if (!configured.contains(m)) problems.add(m + " is listed in patches/*.json but in no mixin config");
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.out.println("GUARD PROBLEM " + p));
            System.exit(1);
        }
        StringBuilder sb = new StringBuilder("# Bons and Furious guard table, generated by tools/GuardTool.java from patches/*.json; do not edit\n");
        for (var e : mixinKey.entrySet()) sb.append("mixin\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        for (var e : mod.entrySet()) sb.append("mod\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        for (var e : guard.entrySet()) for (String l : e.getValue()) sb.append("guard\t").append(e.getKey()).append('\t').append(l).append('\n');
        for (var e : cancel.entrySet()) sb.append("cancel\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("guards: " + mod.size() + " switches, " + mixinKey.size() + " mixins, " + guard.values().stream().mapToInt(List::size).sum()
                + " fingerprints, " + cancel.size() + " cancelled foreign mixins -> " + out.getFileName());
    }

    private static JsonArray arr(JsonObject o, String name) {
        return o.has(name) ? o.getAsJsonArray(name) : new JsonArray();
    }

    static String fingerprint(Classes classes, JsonObject e) throws Exception {
        ClassNode node = classes.node(e.get("class").getAsString());
        if (node == null) return null;
        if (e.get("method").getAsString().equals("*")) return Fingerprint.shape(node);   // declared-method set
        for (MethodNode m : node.methods)
            if (m.name.equals(e.get("method").getAsString()) && m.desc.equals(e.get("desc").getAsString())) return Fingerprint.of(m);
        return null;
    }

    static final class Classes {
        private final Map<String, Path> index = new HashMap<>();
        private final List<Path> dirs = new ArrayList<>();
        private final Map<String, ClassNode> cache = new LinkedHashMap<>();

        Classes(List<Path> roots) throws Exception {
            for (Path r : roots) {
                if (Files.isDirectory(r)) { dirs.add(r); continue; }
                if (!Files.isRegularFile(r)) continue;
                try (JarFile jf = new JarFile(r.toFile())) {
                    var en = jf.entries();
                    while (en.hasMoreElements()) {
                        String n = en.nextElement().getName();
                        if (n.endsWith(".class")) index.putIfAbsent(n, r);
                    }
                }
            }
        }

        ClassNode node(String internal) throws Exception {
            if (cache.containsKey(internal)) return cache.get(internal);
            byte[] b = null;
            for (Path d : dirs) { Path p = d.resolve(internal + ".class"); if (Files.isRegularFile(p)) { b = Files.readAllBytes(p); break; } }
            if (b == null) {
                Path jar = index.get(internal + ".class");
                if (jar != null) try (JarFile jf = new JarFile(jar.toFile()); InputStream in = jf.getInputStream(jf.getEntry(internal + ".class"))) { b = in.readAllBytes(); }
            }
            ClassNode n = null;
            if (b != null) { n = new ClassNode(); new ClassReader(b).accept(n, 0); }
            cache.put(internal, n);
            return n;
        }
    }
}
