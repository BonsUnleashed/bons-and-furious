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

    /** The switches whose mixins predate the guard table (no patches/*.json key); a yield may name them too. */
    static final Set<String> UNGUARDED = Set.of("frame_pacing", "terrain_density_memo", "vanilla_data_merge_unchanged");

    static void emit(List<Path> files, Path resources, Path out, Classes classes) throws Exception {
        List<String> problems = new ArrayList<>();
        Map<String, String> mixinKey = new TreeMap<>();
        Map<String, String> mod = new TreeMap<>();
        Map<String, List<String>> guard = new TreeMap<>();
        Map<String, String> cancel = new TreeMap<>();
        List<JsonObject> yields = new ArrayList<>();
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
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("guards: " + mod.size() + " switches, " + mixinKey.size() + " mixins, " + guard.values().stream().mapToInt(List::size).sum()
                + " fingerprints, " + cancel.size() + " cancelled foreign mixins, " + yieldLines.size() + " foreign patches stepped aside for -> "
                + out.getFileName());
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
            if (b != null) { n = new ClassNode(); new ClassReader(b).accept(n, 0); ForgeFieldToMethod.apply(n); }
            cache.put(internal, n);
            return n;
        }
    }

    /**
     * Forge's own coremod coremods/field_to_method.js (forge-1.20.1-47.4.16-universal.jar) rewrites these classes in
     * every game: inside each listed class, every GETFIELD of the field becomes a call of its getter. The runtime check
     * reads classes through Mixin's bytecode provider, which runs the coremods first, so the build fingerprints the same
     * rewritten code. Replays CoreMods 5.2.4 ASMAPI.redirectFieldToMethod step for step (1.0.26: the ItemStack
     * constructor guard of farmersdelight_tool_action_items never matched in game before this).
     */
    static final class ForgeFieldToMethod {
        private static final Map<String, String[][]> REDIRECTS = Map.of(
                "net/minecraft/world/level/biome/Biome", new String[][] {{"f_47437_", "getModifiedClimateSettings"}, {"f_47443_", "getModifiedSpecialEffects"}},
                "net/minecraft/world/level/levelgen/structure/Structure", new String[][] {{"f_226555_", "getModifiedStructureSettings"}},
                "net/minecraft/world/effect/MobEffectInstance", new String[][] {{"f_19502_", "m_19544_"}},
                "net/minecraft/world/level/block/LiquidBlock", new String[][] {{"f_54689_", "getFluid"}},
                "net/minecraft/world/item/BucketItem", new String[][] {{"f_40687_", "getFluid"}},
                "net/minecraft/world/level/block/StairBlock", new String[][] {{"f_56858_", "getModelBlock"}, {"f_56859_", "getModelState"}},
                "net/minecraft/world/level/block/FlowerPotBlock", new String[][] {{"f_53525_", "m_53560_"}},
                "net/minecraft/world/item/ItemStack", new String[][] {{"f_41589_", "m_41720_"}});

        static void apply(ClassNode c) {
            String[][] redirects = REDIRECTS.get(c.name);
            if (redirects != null) for (String[] r : redirects) redirect(c, r[0], r[1]);
        }

        private static void redirect(ClassNode c, String fieldName, String methodName) {
            FieldNode field = null;
            for (FieldNode f : c.fields) {
                if (!f.name.equals(fieldName)) continue;
                if (field != null) throw new IllegalStateException(c.name + ": several fields named " + fieldName);
                field = f;
            }
            if (field == null) throw new IllegalStateException(c.name + ": no field " + fieldName + " for Forge's field_to_method coremod");
            if (!Modifier.isPrivate(field.access) || Modifier.isStatic(field.access))
                throw new IllegalStateException(c.name + "." + fieldName + " is not a private instance field");
            String sig = "()" + field.desc;
            MethodNode getter = null;
            for (MethodNode m : c.methods) {
                if (!m.desc.equals(sig) || !m.name.equals(methodName)) continue;
                if (getter != null) throw new IllegalStateException(c.name + ": several methods " + methodName + sig);
                getter = m;
            }
            if (getter == null) throw new IllegalStateException(c.name + ": no getter " + methodName + sig + " for Forge's field_to_method coremod");
            for (MethodNode m : c.methods) {
                if (m == getter || m.desc.equals(sig)) continue;   // the coremod leaves every method of the getter's descriptor alone
                for (ListIterator<AbstractInsnNode> it = m.instructions.iterator(); it.hasNext(); ) {
                    AbstractInsnNode n = it.next();
                    if (n.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode) n).name.equals(fieldName)) {
                        it.remove();
                        it.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, c.name, getter.name, getter.desc, false));
                    }
                }
            }
        }
    }
}
