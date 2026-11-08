package bons.furious.patch.emf_lean;

import bons.furious.mixin.emf_lean.PathPackResourcesRootAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.resource.EmptyPackResources;
import org.slf4j.Logger;
import traben.entity_model_features.utils.EMFResourceCaching;

/**
 * Bons and Furious switch emf_listed_resource_misses (tested build: Entity Model Features 3.3.9 for NeoForge 1.21.1; client
 * only). Trade-off switch, off by default.
 *
 * What EMF does. On every resource reload EMF's EMFResourceCaching lists every resource under emf/cem and optifine/cem once
 * (ResourceManager.listResources) and remembers each listed file as present. Every other existence question
 * (resourceExists: model, texture and properties files EMF looks for) still calls ResourceManager.getResource, which for a
 * missing file asks every pack of that namespace in turn; nearly every such question is a miss, thousands per reload.
 *
 * What this switch does. A question EMF would send to getResource is answered "absent" from EMF's own listing when ALL of
 * these hold: both listings of this reload returned normally; the path lies under emf/cem/ or optifine/cem/ (what was
 * listed), is not a .mcmeta file (listings leave metadata out) and its last segment names a file (has a dot); EMF's cache
 * does not hold it as listed; and every pack of the stack is one whose listing names exactly the files it serves: the
 * vanilla pack, zip packs (FilePackResources), NeoForge's mod-jar packs (a PathPackResources whose root lies inside the
 * jar's own file system, as ResourcePackLoader.createPackForMod makes them; jar file systems are case-sensitive like the
 * listing) and NeoForge's empty "Mod Resources" group pack. With any other pack in the stack (folder packs and mods run
 * from a folder, whose file system may answer a lookup that differs in case; packs with format overlays; generated or
 * custom pack classes) the switch stands aside for that reload. Every other question runs EMF's own getResource call.
 *
 * What differs (why it is a trade-off): getResource logs "Resource ... not found, but was filtered by pack ..." for a miss
 * that a pack filter hides; such a miss is now answered without that warning. And a FOLDER inside a zip pack or mod jar
 * whose name looks like a file (e.g. "creeper.jem/") is reported present by getResource but not by the listing, so it now
 * counts as absent. Files that exist are found exactly as before (the listing, filters included, names the same files
 * getResource serves for these pack classes), and nothing else changes.
 *
 * -Dbons_and_furious.emfListedResourceMisses=false switches it off at run time.
 * -Dbons_and_furious.emfListedResourceMisses.shadow=true (verification runs): every question still goes to getResource;
 * SHADOW_CHECKS counts the ones the switch would answer, SHADOW_MISMATCHES those getResource found present (WARN, at most 20).
 */
public final class EmfListedMisses {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfListedResourceMisses", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.emfListedResourceMisses.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Misses answered from the listing (for the harness and the rig). */
    public static final AtomicLong ANSWERED = new AtomicLong();
    private static volatile boolean emfListed, optifineListed;
    /** The resource manager the pack verdict below belongs to (reset with EMF's cache on every reload). */
    private static volatile ResourceManager verdictFor;
    private static volatile boolean listComplete;
    private static volatile boolean announced;

    private EmfListedMisses() {
    }

    /** EMFResourceCaching.scanPrefix: its listResources call; a listing counts once the call returned normally. */
    public static Map<ResourceLocation, Resource> listed(ResourceManager resources, String path, Predicate<ResourceLocation> filter,
                                                         Operation<Map<ResourceLocation, Resource>> original) {
        Map<ResourceLocation, Resource> found = original.call(resources, path, filter);
        if ("emf/cem".equals(path)) emfListed = true;
        else if ("optifine/cem".equals(path)) optifineListed = true;
        return found;
    }

    /** EMFResourceCaching.clearCache (EMF's reset on every resource reload): start of a new listing. */
    public static void cleared() {
        emfListed = false;
        optifineListed = false;
        verdictFor = null;
    }

    /** EMFResourceCaching.resourceExists: its getResource call (EMF only asks isPresent() of the result). */
    public static Optional<Resource> lookup(ResourceManager resources, ResourceLocation location, Operation<Optional<Resource>> original) {
        if (!enabled || !emfListed || !optifineListed || !candidate(location)
                || Boolean.TRUE.equals(EMFResourceCaching.RESOURCE_EXISTENCE_CACHE.get(location.toString())) || !listComplete(resources))
            return original.call(resources, location);
        if (SHADOW) {
            Optional<Resource> stock = original.call(resources, location);
            SHADOW_CHECKS.incrementAndGet();
            if (stock.isPresent()) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: emf_listed_resource_misses shadow mismatch #{}: {} is not in EMF's listing but exists", m, location);
            }
            return stock;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: emf_listed_resource_misses applies (EMF model files that do not exist are answered from EMF's own listing)");
        }
        ANSWERED.incrementAndGet();
        return Optional.empty();
    }

    /** A file under the listed prefixes that a listing can name (not metadata, last segment has a dot). */
    static boolean candidate(ResourceLocation location) {
        String path = location.getPath();
        if (!(path.startsWith("emf/cem/") || path.startsWith("optifine/cem/")) || path.endsWith(".mcmeta")) return false;
        int slash = path.lastIndexOf('/'), dot = path.lastIndexOf('.');
        return dot > slash + 1 && dot < path.length() - 1;
    }

    /** True when every pack of the stack lists exactly the files it serves (decided once per manager and reload). */
    static boolean listComplete(ResourceManager resources) {
        if (verdictFor == resources) return listComplete;
        String other = null;
        try {
            Iterator<PackResources> it = resources.listPacks().iterator();
            while (it.hasNext() && other == null) other = unknownPack(it.next());
        } catch (Throwable t) {
            other = "(pack list unavailable: " + t + ")";
        }
        boolean complete = other == null;
        if (!complete) LOGGER.debug("Bons and Furious: emf_listed_resource_misses stands aside this reload: {} is not a zip, mod-jar or vanilla pack", other);
        listComplete = complete;
        verdictFor = resources;
        return complete;
    }

    /** null when the pack is a known list-complete kind (exact classes only: a subclass may serve more than it lists). */
    private static String unknownPack(PackResources pack) {
        Class<?> c = pack.getClass();
        if (c == VanillaPackResources.class || c == FilePackResources.class || c == EmptyPackResources.class) return null;
        // NeoForge's pack for one mod jar (ResourcePackLoader.createPackForMod): the jar's root path, in the jar's file system
        if (c == PathPackResources.class) {
            Path root = ((PathPackResourcesRootAccessor) pack).bons$root();
            return root != null && root.getFileSystem() != FileSystems.getDefault() ? null : c.getName() + " (" + root + ", not inside a jar)";
        }
        return c.getName();
    }
}
