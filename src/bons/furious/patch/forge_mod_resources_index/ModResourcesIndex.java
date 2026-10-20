package bons.furious.patch.forge_mod_resources_index;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

/**
 * Bons and Furious switch forge_mod_resources_namespace_index (Forge 47.4.16 on Minecraft 1.20.1; client: the "mod_resources"
 * resource pack of every client resource reload). SRG member names. Idea: Collections Of Optimizations
 * (vanilla.skipAbsentPackNamespaces), idea text only; applying it at Forge's DelegatingPackResources layer is ours.
 *
 * Client only (since the 1.0.45 review): Forge builds the combined "mod_resources" pack only for resource packs
 * (ClientModLoader, the only Forge class that constructs a DelegatingPackResources). For data, a dedicated server and the
 * integrated server register every mod file as its own pack "mod:<id>" (ServerLifecycleHooks.serverPackFinder), so no
 * mod_resources pack exists there; a dedicated-server rehearsal counted 0 calls. The other DelegatingPackResources in this pack
 * (TACZ's gun packs, GunPackLoader$2; Kiwi's CustomizationHooks) have delegates that are not Forge mod-file packs and keep
 * Forge's loop (one DEBUG line per class).
 *
 * What Forge does. All mod jars' resources form one pack, DelegatingPackResources "mod_resources", with one Forge mod-file
 * pack per mod file as delegates (ResourcePackLoader.createPackForMod: an anonymous PathPackResources, ResourcePackLoader$1,
 * that resolves paths inside the mod file). Its listResources (m_8031_) asks EVERY delegate to list type/namespace/path,
 * although the class already keeps, per type, namespace -> the delegates that have that namespace (in delegate order, built
 * from each delegate's getNamespaces in the constructor) and uses it for getResource (getCandidatePacks). The resource manager
 * asks mod_resources once per namespace it declares and folder it lists: here 555 delegates x 433 asset namespaces = 240,315
 * delegate calls per folder, of which 526 reach a delegate that has the namespace.
 *
 * What the switch does. The delegate loop iterates the namespace's own delegate list (the same map getCandidatePacks reads,
 * namespacesAssets for client resources, namespacesData otherwise) instead of every delegate, when all of these hold:
 *  - every delegate of this instance is a Forge mod-file pack (ResourcePackLoader$1);
 *  - the namespace is in the map (some delegate declares it; mod_resources is only asked for those);
 *  - FileUtil.decomposePath(path) accepts the path (Forge's listing logs an error per delegate for an invalid one);
 *  - Neighbours: the other mods' mixins on Forge's PathPackResources, ResourcePackLoader$1, vanilla's PathPackResources and
 *    DelegatingPackResources are the tested ones (ModernFix 5.27.77, Fusion 1.3.14a, CreativeCore 2.12.39 accessors).
 * Otherwise the original loop runs. Why the result is identical: a mod-file pack can only list files under its own
 * type/namespace/ folder (Forge: listPath under resolve(type, namespace); ModernFix: its cache node for the namespace; Fusion:
 * its overrides folder's type/namespace/, whose namespaces its getNamespaces adds), and its getNamespaces names every such
 * folder (Forge: every directory under type/, plus the asset namespaces for data when there is no data folder; ModernFix's
 * cache: every namespace with an indexed file, which is also all its listing sees). Fusion sets the overrides folder when the
 * pack is created, before mod_resources reads the namespaces. So a skipped delegate would have listed nothing, logged nothing
 * (a missing folder is a silent NoSuchFileException), and thrown nothing; the delegates that are asked are asked in the
 * original order with the same arguments and the same (possibly wrapped) output. Mod files cannot change while the game runs,
 * and the namespace map is rebuilt with every DelegatingPackResources (every Pack.open). Our TrackworkResources
 * @ModifyVariable of the output at HEAD keeps wrapping the output for the trackwork namespace.
 *
 * -Dbons_and_furious.modResourcesNamespaceIndex=false switches it off at run time.
 * -Dbons_and_furious.modResourcesNamespaceIndex.shadow=true (verification runs only): the original loop runs; before it, every
 * delegate the switch would skip is asked once more into a recorder and must list nothing: SHADOW_CHECKS, SHADOW_MISMATCHES
 * (WARN for the first 20).
 */
public final class ModResourcesIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.modResourcesNamespaceIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.modResourcesNamespaceIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Listings answered from the namespace's delegates, and the delegate calls they left out. */
    public static final LongAdder INDEXED = new LongAdder(), SKIPPED = new LongAdder();
    static final String MOD_FILE_PACK = "net.minecraftforge.resource.ResourcePackLoader$1";
    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> VALID_PATHS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile int standDown = -1;   // -1 not decided yet, 0 applies, 1 steps aside
    private static volatile boolean announced;

    private ModResourcesIndex() {
    }

    /** True when every delegate is a Forge mod-file pack (decided once per DelegatingPackResources; its list is immutable). */
    public static boolean modFilePacksOnly(List<PackResources> delegates) {
        for (PackResources p : delegates) if (p == null || !MOD_FILE_PACK.equals(p.getClass().getName())) return false;
        return true;
    }

    private static final Set<String> NOT_ELIGIBLE_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A DelegatingPackResources whose delegates are not all Forge mod-file packs keeps Forge's loop; one DEBUG line per class
     * says so (TACZ's gun pack loader and Kiwi build such packs; they are not the switch's target).
     */
    public static void notEligible(Object pack, List<PackResources> delegates) {
        if (!NOT_ELIGIBLE_LOGGED.add(pack.getClass().getName())) return;
        String other = "a null delegate";
        for (PackResources p : delegates) {
            if (p == null) break;
            if (!MOD_FILE_PACK.equals(p.getClass().getName())) {
                other = p.getClass().getName();
                break;
            }
        }
        LOGGER.debug("Bons and Furious: forge_mod_resources_namespace_index leaves {} ({} delegates, e.g. {}) to Forge's loop: only packs of Forge mod files are indexed",
                pack.getClass().getName(), delegates.size(), other);
    }

    /** DelegatingPackResources.listResources: the delegates.iterator() call of its loop. */
    public static Iterator<PackResources> delegates(List<PackResources> list, List<PackResources> delegates, Map<String, List<PackResources>> assets,
                                                    Map<String, List<PackResources>> data, boolean modFilePacksOnly, PackType type,
                                                    String namespace, String path, Operation<Iterator<PackResources>> original) {
        if (!enabled || !modFilePacksOnly || list != delegates || type == null || namespace == null || path == null || standsDown())
            return original.call(list);
        List<PackResources> candidates = (type == PackType.CLIENT_RESOURCES ? assets : data).get(namespace);   // as getCandidatePacks
        if (candidates == null || !validPath(path)) return original.call(list);
        if (SHADOW) {
            shadow(list, candidates, type, namespace, path);
            return original.call(list);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: forge_mod_resources_namespace_index applies (mod_resources lists a namespace only in the mod files that have it)");
        }
        INDEXED.increment();
        SKIPPED.add(list.size() - candidates.size());
        return candidates.iterator();
    }

    /**
     * FileUtil.decomposePath(path) succeeds (the test Forge's listing makes before it logs "Invalid path"). decomposePath is a
     * pure function of the string, so its answer is kept for up to 4,096 distinct paths (a reload lists a few hundred).
     */
    static boolean validPath(String path) {
        Boolean known = VALID_PATHS.get(path);
        if (known != null) return known;
        boolean valid = FileUtil.m_245538_(path).result().isPresent();
        if (VALID_PATHS.size() < 4096) VALID_PATHS.putIfAbsent(path, valid);
        return valid;
    }

    /**
     * Shadow mode: every delegate the switch would skip is asked once more, into a recorder, and must list nothing (the
     * delegates it keeps are asked only by the real loop, in the same order, so their output and their logging stay single).
     */
    static void shadow(List<PackResources> all, List<PackResources> candidates, PackType type, String namespace, String path) {
        Set<PackResources> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(candidates);
        List<ResourceLocation> extra = new ArrayList<>();
        String[] from = {null};
        for (PackResources p : all) {
            if (kept.contains(p)) continue;
            p.m_8031_(type, namespace, path, (id, supplier) -> {
                if (extra.isEmpty()) from[0] = p.m_5542_();
                extra.add(id);
            });
        }
        SHADOW_CHECKS.incrementAndGet();
        if (!extra.isEmpty()) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: forge_mod_resources_namespace_index shadow mismatch #{} for {} {}:{}: {} skipped delegate(s) listed {} (first {} from {})",
                    m, type, namespace, path, extra.size() == 1 ? "a" : "", extra.size(), extra.get(0), from[0]);
        }
    }

    /** True when the switch steps aside (decided once, at the first eligible listing; the classes are transformed by then). */
    static boolean standsDown() {
        int s = standDown;
        if (s < 0) {
            String why = Neighbours.whyNot();
            standDown = s = why == null ? 0 : 1;
            if (why != null) LOGGER.info("Bons and Furious: forge_mod_resources_namespace_index steps aside: {}; mod_resources asks every mod file as Forge does", why);
        }
        return s == 1;
    }
}
