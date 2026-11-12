package bons.furious.patch.vanilla_resource_stack_c49;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.resource.DelegatingPackResources;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_resource_stack_miss_cache (Minecraft 1.20.1 + Forge 47.4.28; client side). Ours; no
 * Minecraft or Forge code is carried.
 *
 * What the game does. ResourceManager.getResourceStack(id) (MultiPackResourceManager, the client's resource manager behind
 * Minecraft.getResourceManager()) asks the namespace's FallbackResourceManager, which asks EVERY pack holding that
 * namespace, and with Forge every mod file inside the mod_resources pack one by one, whether it has the file. A miss walks
 * them all again on every call; a mod that probes for an optional file every frame (Punchy asks for items/<id>.json for
 * every entity using an item in third person) pays that walk every frame.
 *
 * What the switch does. Each namespace's FallbackResourceManager remembers the ids whose stack came back empty, but only
 * when every pack of that namespace is an immutable archive: a zip resource pack (FilePackResources), a mod jar (Forge's
 * mod-file pack over a jar file), a pack inside an archive file system (the vanilla jar's built-in packs), or Forge's
 * mod_resources pack made of such packs; and none of them filters files (pack.mcmeta filter sections). A remembered miss
 * answers an empty stack of the same kind (Lists.reverse of an empty ArrayList) without the walk. Folder resource packs,
 * generated / virtual packs (KubeJS, Moonlight's dynamic packs, any class not listed) and filtered namespaces keep the
 * walk, so a file added to a folder pack mid-session is seen exactly as before.
 *
 * Why the result is identical. An archive's files cannot change while the manager that opened it lives (a reload builds
 * a new manager, with new memos), so a lookup that found nothing will find nothing again; without filters the walk logs
 * nothing for a miss. The returned list is empty either way, of the same class, and new on every call.
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
    private static final String MOD_FILE_PACK = "net.minecraftforge.resource.ResourcePackLoader$1";
    private static volatile boolean announced;

    private ResourceStackMisses() {}

    /** Whether misses of this namespace may be remembered (computed once per entry count, kept on the manager). */
    public static boolean cacheable(RsStackViews.Fallback fallback) {
        List<?> entries = fallback.bons$rsStackC49Entries();
        int size = entries.size();
        int v = fallback.bons$rsStackC49Verdict();
        if (v != 0 && (v >>> 2) == size) return (v & 3) == 1;
        boolean ok = entriesImmutable(entries);
        // since 1.0.49.2: a namespace whose pack list changed since its misses were recorded forgets them (a pack added to a live
        // manager may hold a file an earlier lookup missed; vanilla adds packs only while it builds the manager)
        if (v != 0) fallback.bons$rsStackC49Misses().clear();
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
        if (c == DelegatingPackResources.class) {
            java.util.Collection<PackResources> children = ((DelegatingPackResources) pack).getChildren();
            if (children == null || children.isEmpty()) return false;
            for (PackResources child : children) if (!immutable(child, depth + 1)) return false;
            return true;
        }
        if (c.getName().equals(MOD_FILE_PACK)) {
            Path source = ((net.minecraftforge.resource.PathPackResources) pack).getSource();
            return source != null && source.getFileSystem() == FileSystems.getDefault() && Files.isRegularFile(source);
        }
        if (c == net.minecraftforge.resource.PathPackResources.class) {
            Path source = ((net.minecraftforge.resource.PathPackResources) pack).getSource();
            return inArchive(source);
        }
        if (c == net.minecraft.server.packs.PathPackResources.class) {
            return pack instanceof RsStackViews.FolderPack folder && inArchive(folder.bons$rsStackC49Root());
        }
        if (c == VanillaPackResources.class) {
            if (!(pack instanceof RsStackViews.VanillaPack v)) return false;
            List<Path> roots = v.bons$rsStackC49Roots();
            Map<PackType, List<Path>> typed = v.bons$rsStackC49TypePaths();
            if (roots == null || typed == null) return false;
            for (Path p : roots) if (!inArchive(p)) return false;
            for (List<Path> list : typed.values()) for (Path p : list) if (!inArchive(p)) return false;
            return true;
        }
        return false;
    }

    private static boolean inArchive(Path p) {
        return p != null && p.getFileSystem() != FileSystems.getDefault();
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
