package bons.furious.patch.module_resources;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches kiwi_manifest_lookup_index, iceandfire_tabula_lookup_index and
 * distanthorizons_sql_script_lookup_index (Forge 47.4.16 game layer, securejarhandler 2.1.10): the shared lookup engine.
 * No Forge, securejarhandler, Kiwi, Ice and Fire or Distant Horizons code here.
 *
 * What a mod's ClassLoader.getResourceAsStream(name) costs here: the game layer's class loader (modlauncher's
 * TransformingClassLoader, a cpw.mods.cl.ModuleClassLoader) answers getResource(name) with findResourceList: when the
 * "package" of name (the part before the last '/', with '/' -> '.', "" when there is none or it ends in '/') belongs to
 * one of its modules it asks that module only; otherwise it asks EVERY module of its configuration (resolvedRoots: the
 * JarModuleReference modules, ~530 jars here), each through Jar.findFile = a union-filesystem existence check, keeps the
 * URLs in resolvedRoots.values() order and returns the first, or fallbackClassLoader.getResource(name) when there is none
 * (ClassLoader.getResourceAsStream then opens it; an IOException gives null). Kiwi asks for "/<modid>.kiwi.json" for every
 * mod (~470 misses during the serial mod construction), Ice and Fire for every dragon / sea serpent model
 * ("/assets/iceandfire/models/tabula/...tbl", client setup), Distant Horizons for its 13 SQL scripts ("sqlScripts/...")
 * for each of the four databases of every dimension at world load: 341 Server-thread samples (DH), 86 (Kiwi) and 72
 * (Ice and Fire) of our own client load window (cli10_jfr1), all in those scans.
 *
 * This engine gives the same answer from directory listings. Per directory it lists, once, that directory in every
 * module whose filesystem cannot change while the game runs and remembers the file names; a lookup then walks the
 * modules in the class loader's own order and takes the first that holds the file name (modules it could not index are
 * asked directly, as the original does), and asks the fallback when none does. Why it is the same answer:
 *  - module order: resolvedRoots is built by Collectors.toMap (a HashMap) over configuration.modules() filtered by
 *    JarModuleReference.class::isInstance, keyed by descriptor name; the same pipeline over the same Configuration
 *    (FMLLoader.getGameLayer() is defined from the configuration the class loader was built with) and the same
 *    unmodified Set gives a HashMap with the same insertion sequence, so the same values() order. The package set is
 *    collected the same way (packages of the modules in resolvedRoots).
 *  - presence: for a name without a "." / ".." segment, Jar.findFile(name) is Files.exists(root.resolve(name)) unless
 *    the jar's multi-release redirect applies (a relative name among the keys Jar computes from META-INF/versions; the
 *    same keys are computed here for every module and such names are asked directly; absolute names never match them),
 *    i.e. UnionFileSystem.findFirstFiltered: some base path where the path filter accepts the real path and it exists. A directory listing of the parent (UnionFileSystem.newDirStream) collects, over the same base
 *    paths, the children that exist and pass the same filter on the same real paths. So name present <=> its file name
 *    is in the parent's listing. Listings are only kept for modules whose base paths are zip filesystems (jar files, or
 *    paths inside a jar): their entry tables are read once when the filesystem opens and never change, so the existence
 *    checks of the original answer from that same table. A module backed by a directory on disk is asked directly.
 *  - result: the URL is built exactly as ModuleClassLoader does (JarModuleReader.find = Jar.findFile, then
 *    URI.toURL; MalformedURLException -> IllegalArgumentException), only from the first module (the original builds
 *    URLs for every module that has the file; a union URI always converts, its handler is registered); the fallback is
 *    the class loader's own fallback object (modlauncher sets the BOOT layer's ModuleClassLoader: the class loader of
 *    cpw.mods.modlauncher.ModuleLayerHandler; no other code in this pack sets it).
 * Declines (the caller's original call runs): another class loader, a name that is not a plain relative or absolute
 * path of [A-Za-z0-9_.+$-] segments, a name whose package is a module package, a development (non-production) launch,
 * or any surprise while the index is set up.
 */
public final class GameLayerResources {
    /** Returned by lookup when the caller must make the original call. */
    public static final Object NOT_HANDLED = new Object();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Pattern PLAIN = Pattern.compile("/?(?:[A-Za-z0-9_.+$-]+/)*[A-Za-z0-9_.+$-]+");
    private static final Object NONE = new Object();
    private static volatile Object state;

    private GameLayerResources() {
    }

    /** The resource URL (null: not found anywhere, as the original's null) or NOT_HANDLED. */
    public static Object lookup(ClassLoader cl, String name) {
        if (name == null || cl == null) return NOT_HANDLED;
        Object st = state;
        if (st == null) st = init();
        if (!(st instanceof Index index) || cl != index.loader) return NOT_HANDLED;
        return index.lookup(name);
    }

    /** What ClassLoader.getResourceAsStream does with getResource's answer. */
    public static InputStream open(URL url) {
        if (url == null) return null;
        try {
            return url.openStream();
        } catch (IOException e) {
            return null;
        }
    }

    private static synchronized Object init() {
        if (state != null) return state;
        Object built;
        try {
            built = fromGame();
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: game-layer resource index unavailable ({}); resource lookups stay unchanged", t.toString());
            built = NONE;
        }
        state = built;
        return built;
    }

    private static Object fromGame() throws Exception {
        ClassLoader loader = GameLayerResources.class.getClassLoader();
        if (!"cpw.mods.modlauncher.TransformingClassLoader".equals(loader.getClass().getName())) return NONE;
        // the equivalence is argued from securejarhandler 2.1.10 and modlauncher 10.0.9 (Forge 47.4.16's); library classes
        // cannot carry fingerprint guards, so their versions are checked here instead
        if (!version(cpw.mods.cl.ModuleClassLoader.class, "2.1.10") || !version(cpw.mods.modlauncher.api.ITransformationService.class, "10.0.9")) {
            LOGGER.info("Bons and Furious: game-layer resource index off (securejarhandler / modlauncher are not the tested 2.1.10 / 10.0.9: {} / {})",
                    versions(cpw.mods.cl.ModuleClassLoader.class), versions(cpw.mods.modlauncher.api.ITransformationService.class));
            return NONE;
        }
        if (!net.minecraftforge.fml.loading.FMLLoader.isProduction()) return NONE;
        ModuleLayer layer = net.minecraftforge.fml.loading.FMLLoader.getGameLayer();
        if (layer == null || GameLayerResources.class.getModule().getLayer() != layer) return NONE;
        ClassLoader fallback = Class.forName("cpw.mods.modlauncher.ModuleLayerHandler").getClassLoader();
        if (!(fallback instanceof cpw.mods.cl.ModuleClassLoader)) return NONE;
        return Index.build(layer.configuration(), loader, fallback);
    }

    /**
     * Every version the class carries (its module's version, its package's implementation version, the
     * Implementation-Version of its own module's manifest) is v or starts with v + "+" (e.g. "2.1.10+2.1.10+main.96a7b6a8"),
     * and it carries at least one. securejarhandler 2.1.10 has only the manifest one: its module-info has no version and,
     * loaded from the JVM's module path, its packages carry no manifest attributes (before 1.0.32 only the first two were
     * read, so the index never switched on in a real launch).
     */
    static boolean version(Class<?> c, String v) {
        String[] found = found(c);
        boolean any = false;
        for (String f : found) {
            if (f == null) continue;
            if (!f.equals(v) && !f.startsWith(v + "+")) return false;
            any = true;
        }
        return any;
    }

    private static String[] found(Class<?> c) {
        java.lang.module.ModuleDescriptor d = c.getModule().getDescriptor();
        String raw = d == null ? null : d.rawVersion().orElse(null);
        String impl = c.getPackage() == null ? null : c.getPackage().getImplementationVersion();
        return new String[]{raw, impl, manifestVersion(c.getModule())};
    }

    /** For the log line: module / package / manifest versions found. */
    static String versions(Class<?> c) {
        return String.join(" / ", java.util.Arrays.stream(found(c)).map(String::valueOf).toArray(String[]::new));
    }

    /** Implementation-Version of the main section of a named module's own META-INF/MANIFEST.MF, or null. */
    private static String manifestVersion(Module m) {
        if (!m.isNamed()) return null;
        try (InputStream in = m.getResourceAsStream("META-INF/MANIFEST.MF")) {
            return in == null ? null : new java.util.jar.Manifest(in).getMainAttributes().getValue("Implementation-Version");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** For offline equivalence harnesses: an index over the given configuration, class loader and fallback. */
    public static Object forTest(Configuration configuration, ClassLoader loader, ClassLoader fallback) throws Exception {
        Index index = Index.build(configuration, loader, fallback);
        state = index;
        return index;
    }

    /** For offline harnesses: forget the index. */
    public static void resetForTest() {
        state = null;
    }

    /** For offline harnesses: the root URIs of the modules asked directly (not indexed). */
    public static java.util.List<String> directModulesForTest() throws IOException {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (state instanceof Index index) {
            for (int i = 0; i < index.readers.length; i++) if (!index.indexed[i]) out.add(String.valueOf(index.readers[i].find("").orElse(null)));
        }
        return out;
    }

    /** For offline harnesses: the URLs of EVERY module that has name, in the class loader's order (what its getResources
     *  lists), or null where lookup would decline. */
    public static java.util.List<String> matchesForTest(ClassLoader cl, String name) throws IOException {
        Object st = state;
        if (!(st instanceof Index index) || cl != index.loader || index.lookup(name) == NOT_HANDLED) return null;
        String rel = name.startsWith("/") ? name.substring(1) : name;
        int r = rel.lastIndexOf('/');
        Set<String>[] listing = index.dirs.get(r < 0 ? "" : rel.substring(0, r));
        String file = rel.substring(r + 1);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < index.readers.length; i++) {
            boolean present = index.direct(i, name) ? index.readers[i].find(name).isPresent() : listing != null && listing[i] != null && listing[i].contains(file);
            if (present) out.add(index.readers[i].find(name).get().toURL().toExternalForm());
        }
        return out;
    }

    static final class Index {
        final ClassLoader loader;
        final ClassLoader fallback;
        final Set<String> packages;
        final ModuleReader[] readers;
        final boolean[] indexed;
        final Set<String>[] redirects;
        final ConcurrentHashMap<String, Set<String>[]> dirs = new ConcurrentHashMap<>();

        private Index(ClassLoader loader, ClassLoader fallback, Set<String> packages, ModuleReader[] readers, boolean[] indexed, Set<String>[] redirects) {
            this.loader = loader;
            this.fallback = fallback;
            this.packages = packages;
            this.readers = readers;
            this.indexed = indexed;
            this.redirects = redirects;
        }

        /** Whether module i must be asked directly for name (not indexed, or a relative name its jar may redirect). */
        boolean direct(int i, String name) {
            return !indexed[i] || !name.startsWith("/") && redirects[i].contains(name);
        }

        static Index build(Configuration configuration, ClassLoader loader, ClassLoader fallback) throws Exception {
            Class<?> jarRef = Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReference", false, cpw.mods.cl.ModuleClassLoader.class.getClassLoader());
            // the same pipeline as ModuleClassLoader's constructor (resolvedRoots)
            Map<String, ModuleReference> roots = configuration.modules().stream().map(ResolvedModule::reference).filter(jarRef::isInstance)
                    .collect(Collectors.toMap(r -> r.descriptor().name(), r -> r));
            Set<String> packages = new HashSet<>();
            for (ResolvedModule m : configuration.modules()) {
                if (roots.containsKey(m.name())) packages.addAll(m.reference().descriptor().packages());
            }
            ModuleReference[] refs = roots.values().toArray(new ModuleReference[0]);
            ModuleReader[] readers = new ModuleReader[refs.length];
            boolean[] indexed = new boolean[refs.length];
            @SuppressWarnings("unchecked") Set<String>[] redirects = new Set[refs.length];
            int direct = 0;
            for (int i = 0; i < refs.length; i++) {
                readers[i] = refs[i].open();
                redirects[i] = redirected(readers[i]);
                indexed[i] = redirects[i] != null && immutable(readers[i]);
                if (!indexed[i]) direct++;
            }
            LOGGER.debug("Bons and Furious: game-layer resource index over {} modules ({} asked directly)", refs.length, direct);
            return new Index(loader, fallback, packages, readers, indexed, redirects);
        }

        /** A module whose files are listed once: its union filesystem's primary path is a jar/zip file or lies inside one
         *  (production launches build every game-layer module from jars). */
        private static boolean immutable(ModuleReader reader) {
            try {
                Optional<URI> root = reader.find("");
                if (root.isEmpty()) return false;
                FileSystem fs = Path.of(root.get()).getFileSystem();
                if (!(fs instanceof cpw.mods.niofs.union.UnionFileSystem union)) return false;
                Path primary = union.getPrimaryPath();
                if (primary.getFileSystem() == FileSystems.getDefault()) {
                    if (!Files.isRegularFile(primary)) return false;
                    String file = primary.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return file.endsWith(".jar") || file.endsWith(".zip");
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * The relative names Jar.findFile redirects into META-INF/versions/N when the jar is multi-release: Jar's own
         * computation (walk root/META-INF/versions, keep the relative non-directory entries, "META-INF/versions/N/rest" ->
         * rest with the largest N, kept when that N is below the running Java's feature version), done here for every
         * module whatever its manifest says (for a jar that is not multi-release the set only sends those names to the
         * direct path). Absolute names never match these relative keys (UnionPath.equals compares the absolute flag).
         * Null = could not be computed (the module is then asked directly).
         */
        private static Set<String> redirected(ModuleReader reader) {
            try {
                Optional<URI> root = reader.find("");
                if (root.isEmpty()) return null;
                Path vers = Path.of(root.get()).resolve("META-INF/versions");
                if (!Files.exists(vers)) return Set.of();
                Map<String, Integer> max = new java.util.HashMap<>();
                try (java.util.stream.Stream<Path> walk = Files.walk(vers)) {
                    for (Path p1 : (Iterable<Path>) walk::iterator) {
                        if (p1.isAbsolute() || Files.isDirectory(p1)) continue;
                        Path p = p1.subpath(2, p1.getNameCount());
                        String rest = p.subpath(1, p.getNameCount()).toString();
                        int n = Integer.parseInt(p.getName(0).toString());
                        max.merge(rest, n, Math::max);
                    }
                }
                Set<String> out = new HashSet<>();
                int feature = Runtime.version().feature();
                for (Map.Entry<String, Integer> e : max.entrySet()) if (e.getValue() < feature) out.add(e.getKey());
                return out;
            } catch (Exception e) {
                return null;
            }
        }

        Object lookup(String name) {
            if (!PLAIN.matcher(name).matches()) return NOT_HANDLED;
            int idx = name.lastIndexOf('/');
            String pkg = idx == -1 || idx == name.length() - 1 ? "" : name.substring(0, idx).replace('/', '.');
            if (packages.contains(pkg)) return NOT_HANDLED;
            String rel = name.startsWith("/") ? name.substring(1) : name;
            int r = rel.lastIndexOf('/');
            String dir = r < 0 ? "" : rel.substring(0, r);
            String file = rel.substring(r + 1);
            for (String seg : rel.split("/", -1)) if (seg.equals(".") || seg.equals("..")) return NOT_HANDLED;
            Set<String>[] listing = dirs.get(dir);
            if (listing == null) {
                listing = list(dir);
                if (listing == null) return NOT_HANDLED;
                Set<String>[] raced = dirs.putIfAbsent(dir, listing);
                if (raced != null) listing = raced;
            }
            try {
                for (int i = 0; i < readers.length; i++) {
                    boolean present = direct(i, name) ? readers[i].find(name).isPresent() : listing[i] != null && listing[i].contains(file);
                    if (!present) continue;
                    Optional<URI> uri = readers[i].find(name);
                    if (uri.isEmpty()) return NOT_HANDLED;   // cannot happen (see the class comment); be safe
                    try {
                        return uri.get().toURL();
                    } catch (MalformedURLException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            } catch (IOException e) {
                return NOT_HANDLED;
            }
            return fallback.getResource(name);
        }

        /** Child names of dir ("" = root) per indexed module (null where the directory is absent); null on surprise. */
        @SuppressWarnings("unchecked")
        private Set<String>[] list(String dir) {
            Set<String>[] out = new Set[readers.length];
            try {
                for (int i = 0; i < readers.length; i++) {
                    if (!indexed[i]) continue;
                    Optional<URI> d = readers[i].find(dir);
                    if (d.isEmpty()) continue;
                    Path p = Path.of(d.get());
                    Set<String> names = new HashSet<>();   // a file named like the directory throws: the whole directory declines
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                        for (Path c : ds) {
                            Path fn = c.getFileName();
                            if (fn != null) names.add(fn.toString());
                        }
                    }
                    out[i] = names;
                }
            } catch (Exception e) {
                return null;
            }
            return out;
        }
    }
}
