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
 * Bons and Furious switch distanthorizons_sql_script_lookup_index (Minecraft 1.21.1 with NeoForge 21.1.252 game layer:
 * FancyModLoader 4.0.44, securejarhandler 3.0.8, modlauncher 11.0.5): the shared lookup engine of group module_resources.
 * No NeoForge, securejarhandler or Distant Horizons code here.
 *
 * What a mod's ClassLoader.getResourceAsStream(name) costs here: the game layer's class loader (modlauncher's
 * TransformingClassLoader, a cpw.mods.cl.ModuleClassLoader) answers getResource(name) with findResourceList: when the
 * "package" of name (the part before the last '/', with '/' -> '.', "" when there is none or it ends in '/') belongs to
 * one of its modules it asks that module only; otherwise it asks EVERY module of its configuration (resolvedRoots: the
 * JarModuleReference modules, several hundred jars in a large pack), each through the module's findFile = a
 * union-filesystem existence check, keeps the URLs in resolvedRoots.values() order and returns the first, or
 * fallbackClassLoader.getResource(name) when there is none (ClassLoader.getResourceAsStream then opens it; an IOException
 * gives null). Distant Horizons asks for its 13 SQL scripts ("sqlScripts/...") for each of the four databases of every
 * dimension at world load (341 Server-thread samples of our own 1.20.1 client load window, all in those scans).
 *
 * This engine gives the same answer from directory listings. Per directory it lists, once, that directory in every
 * module whose filesystem cannot change while the game runs and remembers the file names; a lookup then walks the
 * modules in the class loader's own order and takes the first that holds the file name (modules it could not index are
 * asked directly, as the original does), and asks the fallback when none does. Why it is the same answer
 * (securejarhandler 3.0.8 / modlauncher 11.0.5, read from the decompiled library classes):
 *  - module order: ModuleClassLoader's constructor builds resolvedRoots with Collectors.toMap (a HashMap) over
 *    configuration.modules() filtered to JarModuleReference references, keyed by descriptor name (3.0.8 filters the
 *    ResolvedModule stream and fills packageLookup in a peek; the keys and their insertion sequence are those of
 *    2.1.10's pipeline). The same keys inserted in the same sequence (FMLLoader.getGameLayer() is defined from the
 *    configuration the class loader was built with, and its modules() Set is unmodifiable) give a HashMap with the same
 *    values() order. The package set is collected the same way (packages of the modules in resolvedRoots).
 *  - presence: for a name without a "." / ".." segment, JarContentsImpl.findFile(name) (reached through
 *    JarModuleReference.jar() and JarModuleReader.find alike) is Files.exists(root.resolve(name)) unless the jar's
 *    multi-release override applies (a relative name among the keys JarContentsImpl.readMultiReleaseInfo computes; the
 *    same keys are computed here for every module and such names are asked directly; absolute names never match them,
 *    UnionPath.equals compares the absolute flag), i.e. UnionFileSystem.exists: some base path where testFilter accepts
 *    the real path and it exists (3.0.8's findFirstFiltered checks existence for all but the last base path and
 *    exists() checks the one it returns, so the answer is the same "any base path" rule as 2.1.10). A directory listing
 *    of the parent (UnionFileSystem.newDirStream) collects, over the same base paths, the children of the existing
 *    directories that pass the same testFilter on the same real paths (a zip filesystem's directory stream returns
 *    dir.resolve(fileName), the same path as the existence check's efs.getPath(dir + "/" + file)). So name present <=>
 *    its file name is in the parent's listing. Listings are only kept for modules whose base paths are zip filesystems
 *    (jar files, or paths inside a jar): their entry tables are read once when the filesystem opens and never change, so
 *    the existence checks of the original answer from that same table. A module backed by a directory on disk, or one
 *    without a root (a VirtualJar), is asked directly.
 *  - result: the URL is built exactly as ModuleClassLoader does (findFile, then URI.toURL; MalformedURLException ->
 *    IllegalArgumentException), only from the first module (the original builds URLs for every module that has the
 *    file; a union URI always converts, its handler is registered); the fallback is the class loader's own fallback
 *    object (modlauncher 11.0.5 ModuleLayerHandler.buildLayer sets the BOOT layer's ModuleClassLoader, the class loader
 *    of cpw.mods.modlauncher.ModuleLayerHandler, on every layer it builds, the GAME layer included; the only other
 *    setFallbackClassLoader call in NeoForge 21.1.252, its libraries and the pinned 1.21.1 target jars is modlauncher's
 *    own on the PLUGIN layer).
 * Declines (the caller's original call runs): another class loader, a name that is not a plain relative or absolute
 * path of [A-Za-z0-9_.+$-] segments, a name whose package is a module package, a development (non-production) launch,
 * securejarhandler / modlauncher other than 3.0.8 / 11.0.5, or any surprise while the index is set up.
 *
 * Ported to 1.21.1: re-derived against securejarhandler 3.0.8 (findFile moved from Jar to JarContentsImpl, same code;
 * UnionFileSystem's findFirstFiltered/newDirStream/testFilter rewritten with the same answers; the multi-release keys
 * are now computed from the walk's absolute paths, name 2 = version, subpath(3) = key, kept when version <= the running
 * feature version, so redirects are real on 1.21.1 and this engine computes the 3.0.8 rule) and modlauncher 11.0.5
 * (fallback wiring unchanged); the version check now expects 3.0.8 / 11.0.5. Only Distant Horizons uses it on 1.21.1:
 * Kiwi 15.8.7 reads its metadata through its own mod file (switch retired) and Ice and Fire has no 1.21.1 build.
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
        // the equivalence is argued from securejarhandler 3.0.8 and modlauncher 11.0.5 (NeoForge 21.1.252's); library
        // classes cannot carry fingerprint guards, so their versions are checked here instead
        if (!version(cpw.mods.cl.ModuleClassLoader.class, "3.0.8") || !version(cpw.mods.modlauncher.api.ITransformationService.class, "11.0.5")) {
            LOGGER.info("Bons and Furious: game-layer resource index off (securejarhandler / modlauncher are not the tested 3.0.8 / 11.0.5)");
            return NONE;
        }
        if (!net.neoforged.fml.loading.FMLLoader.isProduction()) return NONE;
        ModuleLayer layer = net.neoforged.fml.loading.FMLLoader.getGameLayer();
        if (layer == null || GameLayerResources.class.getModule().getLayer() != layer) return NONE;
        ClassLoader fallback = Class.forName("cpw.mods.modlauncher.ModuleLayerHandler").getClassLoader();
        if (!(fallback instanceof cpw.mods.cl.ModuleClassLoader)) return NONE;
        return Index.build(layer.configuration(), loader, fallback);
    }

    /** The class's module version or package implementation version starts with v (e.g. "3.0.8+main.8382e570"). */
    static boolean version(Class<?> c, String v) {
        java.lang.module.ModuleDescriptor d = c.getModule().getDescriptor();
        String raw = d == null ? null : d.rawVersion().orElse(null);
        String impl = c.getPackage() == null ? null : c.getPackage().getImplementationVersion();
        return raw != null && (raw.equals(v) || raw.startsWith(v + "+")) || impl != null && (impl.equals(v) || impl.startsWith(v + "+"));
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
            // the same keys in the same sequence as ModuleClassLoader's constructor (resolvedRoots)
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
         * The relative names JarContentsImpl.findFile redirects into META-INF/versions/N when the jar is multi-release:
         * securejarhandler 3.0.8's own computation (walk root/META-INF/versions when it is a directory, keep the
         * non-directory entries, name 2 of the absolute walk path = N, subpath(3) = the key, kept when N <= the running
         * Java's feature version), done here for every module whatever its manifest says (for a jar that is not
         * multi-release the set only sends those names to the direct path). Absolute names never match these relative
         * keys (UnionPath.equals compares the absolute flag). Null = could not be computed (the module is then asked
         * directly).
         */
        private static Set<String> redirected(ModuleReader reader) {
            try {
                Optional<URI> root = reader.find("");
                if (root.isEmpty()) return null;
                Path vers = Path.of(root.get()).resolve("META-INF/versions");
                if (!Files.isDirectory(vers)) return Set.of();
                Set<String> out = new HashSet<>();
                int feature = Runtime.version().feature();
                try (java.util.stream.Stream<Path> walk = Files.walk(vers)) {
                    for (Path p : (Iterable<Path>) walk::iterator) {
                        if (Files.isDirectory(p)) continue;
                        int n = Integer.parseInt(p.getName(2).toString());
                        if (n <= feature) out.add(p.subpath(3, p.getNameCount()).toString());
                    }
                }
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
