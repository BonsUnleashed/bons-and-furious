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
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.lang.reflect.Modifier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
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
 *   GuardTool also <patches.json> <key> <build label> <roots...>        (since 1.0.46)
 *                                                       record the fingerprints the given jars (another build of the
 *                                                       targets) have for every guard entry of the key, as that build's
 *                                                       row of "sha256_also" (an entry equal to the tested build gets no row)
 * Roots are jars or directories, first hit wins (Forge-patched vanilla before the SRG jar, then the mods).
 *
 * Since 1.0.46 (accepted alternative fingerprints): a guard entry's "sha256" is the tested build's fingerprint, JSON null
 * when the method must be absent there; "sha256_also" lists {"build": label, "sha256": print or null} for other builds the
 * switch's patch is valid for. A key passes when ONE label (the tested build, or a build label: an entry without a row for
 * it is unchanged there) matches all its entries. A key's "redundant_since": [{"mod", "version", "note", "even_if_matching"}]
 * marks an upstream fix of the same cost: fill never overwrites its recorded prints (the last build its patch supports),
 * and emit reports a non-matching key as a NOTE (it stands down there at run time) instead of a problem. A guard entry with
 * "audit": true only audits another mod's side effect: when no class of its package is in the given jars (that mod is not
 * installed) it is satisfied (NOTE) and fill keeps its recorded print; present, it must match like any other entry.
 */
public final class GuardTool {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    public static void main(String[] a) throws Exception {
        String mode = a[0];
        Path patches = Paths.get(a[1]);
        int rootStart = mode.equals("fill") ? 2 : 4;
        List<Path> roots = new ArrayList<>();
        for (int i = rootStart; i < a.length; i++) roots.add(Paths.get(a[i]));
        Classes classes = new Classes(roots);
        if (mode.equals("also")) { also(patches, a[2], a[3], classes); return; }
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
        int n = 0, kept = 0;
        for (Path f : files) {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> k : root.getAsJsonObject("keys").entrySet()) {
                boolean redundant = k.getValue().getAsJsonObject().has("redundant_since");
                for (JsonElement g : k.getValue().getAsJsonObject().getAsJsonArray("guard")) {
                    JsonObject o = g.getAsJsonObject();
                    // since 1.0.46: a redundant key keeps the prints of the last build its patch supports
                    if (redundant && o.has("sha256")) { kept++; continue; }
                    String fp = fingerprint(classes, o);
                    if (o.has("sha256") && o.get("sha256").isJsonNull()) {   // since 1.0.46: absent in the tested build
                        if (fp != null) throw new IllegalStateException(f.getFileName() + " " + k.getKey() + ": " + o + " is recorded as absent in the tested build but the given jars have it");
                        kept++;
                        continue;
                    }
                    if (fp == null && auditGone(classes, o) && o.has("sha256")) { kept++; continue; }   // since 1.0.46
                    if (fp == null) throw new IllegalStateException(f.getFileName() + " " + k.getKey() + ": " + o + " not found in the given jars");
                    o.addProperty("sha256", fp);
                    n++;
                }
            }
            Files.writeString(f, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        }
        System.out.println("filled " + n + " fingerprints in " + files.size() + " file(s)" + (kept > 0 ? "; kept " + kept + " (redundant keys, absent entries)" : ""));
    }

    /** Since 1.0.46: the key's entries as found in the given jars (another build), recorded as that build's sha256_also rows. */
    static void also(Path file, String key, String label, Classes classes) throws Exception {
        if (label.isBlank() || label.contains(";") || label.contains("=") || label.contains("\t") || label.contains(","))
            throw new IllegalStateException("a build label may not be blank or contain ; = , or a tab: " + label);
        JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject k = root.getAsJsonObject("keys").getAsJsonObject(key);
        if (k == null) throw new IllegalStateException(file.getFileName() + ": no key " + key);
        int rows = 0, same = 0, present = 0;
        for (JsonElement g : k.getAsJsonArray("guard")) {
            JsonObject o = g.getAsJsonObject();
            if (!o.has("sha256")) throw new IllegalStateException(key + ": fill the tested build first (" + o + ")");
            String tested = o.get("sha256").isJsonNull() ? null : o.get("sha256").getAsString();
            String fp = fingerprint(classes, o);
            if (fp == null && auditGone(classes, o)) { same++; continue; }   // an audited mod these jars lack: satisfied anyway
            if (fp != null) present++;
            JsonArray kept = new JsonArray();
            for (JsonElement r : arr(o, "sha256_also")) if (!label.equals(str(r.getAsJsonObject(), "build"))) kept.add(r);
            if (java.util.Objects.equals(fp, tested)) same++;
            else {
                JsonObject row = new JsonObject();
                row.addProperty("build", label);
                if (fp == null) row.add("sha256", com.google.gson.JsonNull.INSTANCE);
                else row.addProperty("sha256", fp);
                kept.add(row);
                rows++;
            }
            if (kept.size() == 0) o.remove("sha256_also");
            else o.add("sha256_also", kept);
        }
        if (present == 0) throw new IllegalStateException(key + ": none of its guarded methods exists in the given jars; build " + label + " is not recorded");
        Files.writeString(file, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        System.out.println("also " + key + " [" + label + "]: " + rows + " row(s) recorded, " + same + " entr" + (same == 1 ? "y" : "ies") + " equal to the tested build");
    }

    /** Since 1.0.46: an "audit" entry whose package has no class in the given jars (the audited mod is not there). */
    static boolean auditGone(Classes classes, JsonObject e) {
        if (!e.has("audit") || !e.get("audit").getAsBoolean()) return false;
        String c = e.get("class").getAsString();
        return !classes.packageKnown(c.substring(0, Math.max(0, c.lastIndexOf('/'))));
    }

    /** Since 1.0.46: the fingerprint an entry expects in a build label ("" = tested; null = absent). */
    private static boolean matches(String expected, String actual) {
        return expected == null ? actual == null : actual != null && Fingerprint.matches(expected, actual);
    }

    private static String expected(JsonObject e, String label) {
        if (!e.has("sha256")) return "\u0000never filled";   // matches nothing, not even an absent method
        if (!label.isEmpty())
            for (JsonElement r : arr(e, "sha256_also")) {
                JsonObject row = r.getAsJsonObject();
                if (label.equals(str(row, "build"))) return str(row, "sha256");
            }
        String result = str(e, "sha256");
        if (result != null) for (JsonElement v : arr(e, "sha256_variants")) result += "|" + v.getAsString();
        return result;
    }

    /** The switches whose mixins predate the guard table (no patches/*.json key); a yield may name them too. */
    static final Set<String> UNGUARDED = Set.of("frame_pacing", "terrain_density_memo", "vanilla_data_merge_unchanged");

    static void emit(List<Path> files, Path resources, Path out, Classes classes) throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, String> mixinKey = new TreeMap<>();
        Map<String, String> mod = new TreeMap<>();
        Map<String, List<String>> guard = new TreeMap<>();
        Map<String, String> cancel = new TreeMap<>();
        List<JsonObject> yields = new ArrayList<>();
        List<String> notes = new ArrayList<>();          // since 1.0.46: redundant keys that do not match these jars
        List<String> redundantLines = new ArrayList<>();
        List<String> auditLines = new ArrayList<>();
        int alternatives = 0;
        for (Path f : files) {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (JsonElement y : arr(root, "yields")) yields.add(y.getAsJsonObject());
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
                // since 1.0.46: redundant_since lines (mod, first fixed version, even_if_matching, note)
                for (JsonElement r : arr(o, "redundant_since")) {
                    JsonObject rs = r.getAsJsonObject();
                    String rm = str(rs, "mod"), rv = str(rs, "version"), note = str(rs, "note");
                    if (rm == null || rv == null || !Character.isDigit(rv.charAt(0)) || note != null && note.contains("\t"))
                        problems.add(key + ": redundant_since needs mod and a numeric version (and no tab in the note): " + rs);
                    else redundantLines.add(String.join("\t", key, rm, rv, String.valueOf(rs.has("even_if_matching") && rs.get("even_if_matching").getAsBoolean()),
                            note == null ? "" : note));
                }
                List<String> lines = new ArrayList<>();
                List<JsonObject> entries = new ArrayList<>();
                List<String> found = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                List<String> tested = new ArrayList<>();   // the tested build's problems, as before
                for (JsonElement g : arr(o, "guard")) {
                    JsonObject e = g.getAsJsonObject();
                    entries.add(e);
                    String want = e.has("sha256") ? (e.get("sha256").isJsonNull() ? null : e.get("sha256").getAsString()) : "";
                    if (want != null) {
                        for (JsonElement variant : arr(e, "sha256_variants")) want += "|" + variant.getAsString();
                        if (!want.matches("[0-9a-f]{64}(\\|[0-9a-f]{64})*")) problems.add("Malformed fingerprint set: " + want);
                    }
                    String got = fingerprint(classes, e);
                    String where = key + " " + e.get("class").getAsString() + "." + e.get("method").getAsString() + e.get("desc").getAsString();
                    if (e.has("audit") && e.get("audit").getAsBoolean())
                        auditLines.add(key + "\t" + e.get("class").getAsString() + "\t" + e.get("method").getAsString() + "\t" + e.get("desc").getAsString());
                    boolean gone = got == null && auditGone(classes, e);   // since 1.0.46: the audited mod is not in these jars
                    found.add(gone ? AUDIT_GONE : got);
                    if (gone) notes.add(where + ": audited mod not in the local jars (satisfied)");
                    else if (want == null) { if (got != null) tested.add(where + ": exists in the local jars, the tested build has no such method"); }
                    else if (got == null) tested.add(where + ": not found in the local jars");
                    else if (!Fingerprint.matches(want, got)) tested.add(where + ": the local jar differs from the tested build (" + want + " expected, " + got + " found)");
                    StringBuilder alts = new StringBuilder();
                    for (JsonElement r : arr(e, "sha256_also")) {
                        JsonObject row = r.getAsJsonObject();
                        String label = str(row, "build");
                        if (label == null || label.isBlank() || label.contains(";") || label.contains("=") || label.contains("\t") || label.contains(","))
                            problems.add(where + ": sha256_also row without a usable build label: " + row);
                        else {
                            if (!labels.contains(label)) labels.add(label);
                            if (alts.length() > 0) alts.append(';');
                            alts.append(label).append('=').append(str(row, "sha256") == null ? "-" : str(row, "sha256"));
                        }
                    }
                    lines.add(e.get("class").getAsString() + "\t" + e.get("method").getAsString() + "\t" + e.get("desc").getAsString() + "\t"
                            + (want == null ? "-" : want) + (alts.length() > 0 ? "\t" + alts : ""));
                }
                String matched = tested.isEmpty() ? "" : null;
                for (int li = 0; matched == null && li < labels.size(); li++) {
                    boolean all = true;
                    for (int i = 0; i < entries.size() && all; i++)
                        all = found.get(i) == AUDIT_GONE || matches(expected(entries.get(i), labels.get(li)), found.get(i));
                    if (all) matched = labels.get(li);

                }
                if (matched == null) {
                    if (o.has("redundant_since")) for (String t : tested) notes.add(t + " (redundant_since: the switch stands down with these jars)");
                    else {
                        problems.addAll(tested);
                        if (!labels.isEmpty()) problems.add(key + ": none of its accepted alternative builds " + labels + " matches the local jars either");
                    }
                } else if (!matched.isEmpty()) alternatives++;
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
        // Another mod's patch that makes the same change as one of our switches (patches/compat.json, see
        // bons.furious.guard.ForeignPatches): one "yield" line, and its mixin is cancelled while our switch applies.
        List<String> yieldLines = new ArrayList<>();
        for (JsonObject y : yields) {
            String key = str(y, "key"), mixin = str(y, "mixin");
            if (key == null || str(y, "mod") == null || str(y, "name") == null || mixin == null) { problems.add("yield without key, mod, name or mixin: " + y); continue; }
            if (!mod.containsKey(key) && !UNGUARDED.contains(key)) problems.add("yield for unknown switch " + key);
            if ((str(y, "option") == null) != (str(y, "config") == null)) problems.add("yield " + key + ": option and config go together");
            String prev = cancel.put(mixin, key);
            if (prev != null && !prev.equals(key)) problems.add(mixin + " is cancelled for " + prev + " and " + key);
            List<String> unless = new ArrayList<>();
            for (JsonElement u : arr(y, "unless_mods")) unless.add(u.getAsString());
            yieldLines.add(String.join("\t", key, str(y, "mod"), str(y, "name"), mixin, dash(str(y, "config")), dash(str(y, "master")),
                    dash(str(y, "option")), String.valueOf(y.has("default") && y.get("default").getAsBoolean()), unless.isEmpty() ? "-" : String.join(",", unless)));
        }
        notes.forEach(p -> System.out.println("GUARD NOTE " + p));
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.out.println("GUARD PROBLEM " + p));
            System.exit(1);
        }
        StringBuilder sb = new StringBuilder("# Bons and Furious guard table, generated by tools/GuardTool.java from patches/*.json; do not edit\n");
        for (var e : mixinKey.entrySet()) sb.append("mixin\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        for (var e : mod.entrySet()) sb.append("mod\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        for (var e : guard.entrySet()) for (String l : e.getValue()) sb.append("guard\t").append(e.getKey()).append('\t').append(l).append('\n');
        for (var e : cancel.entrySet()) sb.append("cancel\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        for (String l : yieldLines) sb.append("yield\t").append(l).append('\n');
        for (String l : redundantLines) sb.append("redundant\t").append(l).append('\n');
        for (String l : auditLines) sb.append("audit\t").append(l).append('\n');
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("guards: " + mod.size() + " switches, " + mixinKey.size() + " mixins, " + guard.values().stream().mapToInt(List::size).sum()
                + " fingerprints, " + cancel.size() + " cancelled foreign mixins, " + yieldLines.size() + " foreign patches stepped aside for, "
                + alternatives + " switches matched an accepted alternative build, " + redundantLines.size() + " redundant_since lines ("
                + notes.size() + " notes) -> " + out.getFileName());
    }

    private static JsonArray arr(JsonObject o, String name) {
        return o.has(name) ? o.getAsJsonArray(name) : new JsonArray();
    }

    private static String str(JsonObject o, String name) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : null;
    }

    private static String dash(String s) {
        return s == null ? "-" : s;
    }

    static String fingerprint(Classes classes, JsonObject e) throws Exception {
        ClassNode node = classes.node(e.get("class").getAsString());
        if (node == null) return null;
        if (e.get("method").getAsString().equals("*")) return Fingerprint.shape(node);   // declared-method set
        for (MethodNode m : node.methods)
            if (m.name.equals(e.get("method").getAsString()) && m.desc.equals(e.get("desc").getAsString())) return Fingerprint.of(m);
        return null;
    }

    /** Since 1.0.46: what an "audit" entry found when its mod is not in the given jars. */
    static final String AUDIT_GONE = "\u0000audited mod absent";

    static final class Classes {
        private final Map<String, Path> index = new HashMap<>();
        private final List<Path> dirs = new ArrayList<>();
        private final Map<String, ClassNode> cache = new LinkedHashMap<>();
        private Set<String> packages;

        /** Since 1.0.46: true when some root holds a class directly in this package (internal name, no trailing slash). */
        boolean packageKnown(String pkg) {
            if (packages == null) {
                packages = new java.util.HashSet<>();
                for (String n : index.keySet()) packages.add(n.lastIndexOf('/') < 0 ? "" : n.substring(0, n.lastIndexOf('/')));
            }
            if (packages.contains(pkg)) return true;
            for (Path d : dirs) {
                Path p = d.resolve(pkg);
                if (Files.isDirectory(p)) {
                    try (var s = Files.list(p)) { if (s.anyMatch(x -> x.getFileName().toString().endsWith(".class"))) return true; }
                    catch (Exception e) { return true; }
                }
            }
            return false;
        }

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
