package bons.furious.patch.vanilla_resource_stack_c49;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_resource_stack_miss_cache (Minecraft 1.21.1, client). Ours; no Minecraft or NeoForge
 * code is carried.
 *
 * What the game does. ResourceManager.getResourceStack(id) (MultiPackResourceManager, the client's resource manager behind
 * Minecraft.getResourceManager()) asks the namespace's FallbackResourceManager, which asks EVERY pack holding that namespace
 * (with NeoForge every mod file is a pack of its own: a PathPackResources over its jar) whether it has the file: a path
 * resolution and an existence test per pack, or a zip entry lookup. A miss walks them all again on every call; a mod that
 * probes for an optional file every frame pays that walk every frame.
 *
 * What the switch does. Each namespace's FallbackResourceManager remembers the ids whose stack came back empty, but only
 * when every pack of that namespace is an immutable archive and none of them filters files (pack.mcmeta filter sections):
 * - a zip resource pack (FilePackResources: entries are looked up in its ZipFile, opened once while the pack is open);
 * - a PathPackResources whose root lies in an archive: a mod jar or the game's own jars (NeoForge serves them through
 *   securejarhandler's union file system; it counts as an archive when the file it was made from is a jar file on disk),
 *   or a read-only zip file system; mod-supplied packs inside a mod jar alike. Jar-in-jar mods, which NeoForge opens
 *   through its jij file system, keep the walk;
 * - the game's built-in vanilla pack (VanillaPackResources) when all of its roots lie in archives;
 * - a pack with overlays (CompositePackResources) whose every pack is one of the above.
 * A remembered miss answers an empty stack of the same kind (Lists.reverse of an empty ArrayList) without the walk. Folder
 * resource packs (and mods run from folders), generated / virtual packs (any class not listed) and filtered namespaces keep
 * the walk, so a file added to a folder pack mid-session is seen exactly as before. When the namespace's pack list changes,
 * its verdict is made again and its remembered misses are dropped.
 *
 * Why the result is identical. An archive's files cannot change while the manager that opened it lives (a reload builds
 * a new manager, with new memos), so a lookup that found nothing will find nothing again; without filters the walk logs
 * nothing for a miss. Elysium API's hidden assets (its wrapper of each pack's lookup in getResourceStack) are decided once
 * per pack and asset and only forgotten when a reload starts, so they stay hidden for the manager's life too. The returned
 * list is empty either way, of the same class, and new on every call.
 *
 * -Dbons_and_furious.vanillaResourceStackMissCache=false switches it off at run time.
 * -Dbons_and_furious.vanillaResourceStackMissCache.shadow=true: every remembered miss also runs the walk, which must find
 * nothing (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class ResourceStackMisses {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaResourceStackMissCache", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaResourceStackMissCache.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** securejarhandler's union file system (mod jars and the game's jars on NeoForge 21.1). */
    private static final String UNION_FS = "cpw.mods.niofs.union.UnionFileSystem";
    /** Union file system -> made from an archive (computed once per file system). */
    private static final Map<FileSystem, Boolean> UNION_ARCHIVES = new ConcurrentHashMap<>();
    /** UnionFileSystem.getPrimaryPath() (public, package exported), looked up per class; null when absent. */
    private static final ClassValue<MethodHandle> PRIMARY_PATH = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                return MethodHandles.publicLookup().findVirtual(type, "getPrimaryPath", MethodType.methodType(Path.class));
            } catch (Throwable t) {
                return null;
            }
        }
    };
    private static volatile boolean announced;

    private ResourceStackMisses() {}

    /** Whether misses of this namespace may be remembered (computed once per entry count, kept on the manager). */
    public static boolean cacheable(RsStackViews.Fallback fallback) {
        List<?> entries = fallback.bons$rsStackC49Entries();
        int size = entries.size();
        int v = fallback.bons$rsStackC49Verdict();
        if (v != 0 && (v >>> 2) == size) return (v & 3) == 1;
        if (v != 0) fallback.bons$rsStackC49Misses().clear();
        boolean ok = entriesImmutable(entries);
        fallback.bons$rsStackC49SetVerdict((size << 2) | (ok ? 1 : 2));
        return ok;
    }

    private static boolean entriesImmutable(List<?> entries) {
        if (entries.isEmpty()) return false;
        for (Object e : entries) {
            if (!(e instanceof RsStackViews.Entry entry) || entry.bons$rsStackC49Filter() != null || !immutable(entry.bons$rsStackC49Pack(), 0))
                return false;
        }
        return true;
    }

    /** A pack whose files cannot change while it is open (the classes listed above, nothing else). */
    static boolean immutable(PackResources pack, int depth) {
        if (pack == null || depth > 4) return false;
        Class<?> c = pack.getClass();
        if (c == FilePackResources.class) return true;
        if (c == PathPackResources.class) {
            return pack instanceof RsStackViews.FolderPack folder && archived(folder.bons$rsStackC49Root(), 0);
        }
        if (c == CompositePackResources.class) {
            if (!(pack instanceof RsStackViews.CompositePack composite)) return false;
            List<PackResources> stack = composite.bons$rsStackC49Stack();
            if (stack == null || stack.isEmpty()) return false;
            for (PackResources p : stack) if (!immutable(p, depth + 1)) return false;
            return true;
        }
        if (c == VanillaPackResources.class) {
            if (!(pack instanceof RsStackViews.VanillaPack v)) return false;
            List<Path> roots = v.bons$rsStackC49Roots();
            Map<PackType, List<Path>> typed = v.bons$rsStackC49TypePaths();
            if (roots == null || typed == null) return false;
            for (Path p : roots) if (!archived(p, 0)) return false;
            for (List<Path> list : typed.values()) for (Path p : list) if (!archived(p, 0)) return false;
            return true;
        }
        return false;
    }

    /** A path inside an archive that cannot change while it is open; a path on the default file system is a folder. */
    static boolean archived(Path p, int depth) {
        if (p == null || depth > 4) return false;
        FileSystem fs = p.getFileSystem();
        if (fs == FileSystems.getDefault()) return false;
        if ("jar".equals(fs.provider().getScheme())) return fs.isReadOnly();
        if (!fs.getClass().getName().equals(UNION_FS)) return false;
        Boolean known = UNION_ARCHIVES.get(fs);
        if (known != null) return known;
        boolean archive = unionOfArchive(fs, depth);
        UNION_ARCHIVES.put(fs, archive);
        return archive;
    }

    /** A union file system made from a jar file on disk, or from a path inside another archive this check knows. */
    private static boolean unionOfArchive(FileSystem fs, int depth) {
        MethodHandle primaryPath = PRIMARY_PATH.get(fs.getClass());
        if (primaryPath == null) return false;
        try {
            Path primary = (Path) primaryPath.invoke(fs);
            if (primary == null) return false;
            if (primary.getFileSystem() == FileSystems.getDefault()) return Files.isRegularFile(primary);
            return archived(primary, depth + 1);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The answer for a remembered miss: what the walk returns for nothing found (a new, empty, reversed ArrayList). */
    public static List<Resource> hit(Operation<List<Resource>> original, ResourceLocation id) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_resource_stack_miss_cache applies (resource lookups that found nothing in archive packs are remembered){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            List<Resource> walked = original.call(id);
            if (!walked.isEmpty()) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_resource_stack_miss_cache shadow mismatch #{}: {} was remembered as missing but the walk found {}", m, id, walked.size());
                return walked;
            }
        }
        return Lists.reverse(new ArrayList<>());
    }
}
