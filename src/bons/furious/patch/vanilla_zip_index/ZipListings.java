package bons.furious.patch.vanilla_zip_index;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_zip_pack_listing_index (Minecraft 1.20.1 on Forge 47.4.16; client and server, every zipped
 * resource pack and data pack). SRG member names. Idea: Collections Of Optimizations (vanilla.indexZipPacks), idea text only;
 * the design below is ours.
 *
 * What vanilla does. FilePackResources.listResources (m_8031_) answers "which files lie under type/namespace/path/" by
 * walking EVERY entry of the zip: ZipFile.entries() builds a new ZipEntry per entry (name decoded from the central directory,
 * extra field copied, the ZipFile's lock taken per entry) and the loop keeps the names that start with type/namespace/path/.
 * The resource manager asks every namespace a zip declares, once per folder it lists, so a zip with 54 namespaces and 3,428
 * entries (Better Leaves in this pack) is walked 54 times for every folder a reload lists.
 *
 * What the switch does. The first listing of an open ZipFile reads its entries once, in central-directory order, into
 * buckets keyed by the entry name up to and including its third '/' (type/first folder/second folder/). Directory entries and
 * names with fewer than three '/' are left out: vanilla's loop can never keep them. Later listings hand vanilla's own loop the
 * bucket of type/namespace/path/ instead of the whole zip:
 *  - every name that starts with the prefix type/namespace/path/ (which holds at least three '/') has the same first three
 *    '/' as the prefix, so it is in the bucket keyed by the prefix up to its third '/';
 *  - a bucket keeps central-directory order, and vanilla's unchanged loop (isDirectory, startsWith, ResourceLocation.tryBuild,
 *    then accept or its "Invalid path in datapack" warning) drops the bucket's other names exactly as it drops them from the
 *    full walk; so the output gets the same calls with the same values in the same order, and the same warnings.
 * The ZipEntry objects come from the same open ZipFile: the index belongs to that ZipFile object, and close() followed by a
 * reopen (a new ZipFile, which reads the file again) builds a new index. IoSupplier.create(zip, entry) opens an entry by its
 * name (final in ZipEntry), so a ZipEntry built once serves every listing. ZipFile.entries() is still called first on every
 * listing, so a closed ZipFile throws exactly as before. A zip in which two file entries share a name is never indexed (its
 * listings walk it as vanilla does): ZipFile.getInputStream finds an entry by name but prefers the position of the entry it
 * built last, which during vanilla's walk is the entry just handed out, so a reader that opens the supplier inside accept()
 * gets that very duplicate. With unique names the name alone decides the entry, whatever ZipFile built last.
 *
 * Steps aside (one INFO line) when ModernFix's FilePackResourcesMixin (its zip index, stability level BETA) is applied, and
 * when another mod's mixin this switch was not tested with is applied to FilePackResources (Neighbours), or replaces
 * listResources (@Overwrite).
 *
 * -Dbons_and_furious.zipPackListingIndex=false switches it off at run time.
 * -Dbons_and_furious.zipPackListingIndex.shadow=true (verification runs only): vanilla's loop walks the zip's own entries as
 * usual; each listing also filters the bucket and a full walk with vanilla's test (not a directory, starts with the prefix)
 * and compares the two name sequences: SHADOW_CHECKS, SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class ZipListings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.zipPackListingIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.zipPackListingIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Indexes built, listings answered from an index, builds that failed (vanilla's walk was used), zips with shared names. */
    public static final LongAdder BUILDS = new LongAdder(), INDEXED = new LongAdder(), BUILD_FAILURES = new LongAdder(), DUPLICATE_ZIPS = new LongAdder();
    private static final Enumeration<ZipEntry> NONE = Collections.emptyEnumeration();
    private static volatile int standDown = -1;   // -1 not decided yet, 0 applies, 1 steps aside
    private static volatile boolean announced;

    private ZipListings() {
    }

    /** The per-pack state FilePackResourcesZipIndexMixin adds to FilePackResources. */
    public interface Holder {
        Index bons$zipIndex();

        void bons$zipIndex(Index index);

        Object bons$zipIndexLock();
    }

    /** The buckets of one open ZipFile; never changed after it is built. */
    public static final class Index {
        final ZipFile zip;
        final Map<String, ZipEntry[]> buckets;
        final int entries;

        Index(ZipFile zip, Map<String, ZipEntry[]> buckets, int entries) {
            this.zip = zip;
            this.buckets = buckets;
            this.entries = entries;
        }

        public int entries() {
            return entries;
        }

        public int buckets() {
            return buckets.size();
        }
    }

    /** One bucket handed to vanilla's loop: hasMoreElements / nextElement over an array, central-directory order. */
    static final class Bucket implements Enumeration<ZipEntry> {
        private final ZipEntry[] entries;
        private int next;

        Bucket(ZipEntry[] entries) {
            this.entries = entries;
        }

        @Override
        public boolean hasMoreElements() {
            return next < entries.length;
        }

        @Override
        public ZipEntry nextElement() {
            if (next >= entries.length) throw new NoSuchElementException();
            return entries[next++];
        }
    }

    /**
     * FilePackResources.listResources: the ZipFile.entries() call. Returns the bucket of type/namespace/path/ (empty when the
     * zip has none) or, when the switch is off, steps aside, or anything is unusual, the original enumeration.
     */
    public static Enumeration<? extends ZipEntry> entries(Holder holder, ZipFile zip, PackType type, String namespace, String path,
                                                          Operation<Enumeration<? extends ZipEntry>> original) {
        Enumeration<? extends ZipEntry> all = original.call(zip);   // vanilla's call: a closed ZipFile throws here as before
        if (!enabled || type == null || namespace == null || path == null || standsDown()) return all;
        Index index = holder.bons$zipIndex();
        if (index == null || index.zip != zip) {
            index = build(holder, zip);
            if (index == null) return all;
        }
        if (index.buckets == null) return all;   // entries sharing a name: vanilla's walk (see build)
        String prefix = type.m_10305_() + "/" + namespace + "/" + path + "/";   // the prefix vanilla's loop tests (PackType.getDirectory)
        String key = key(prefix);
        if (key == null) return all;   // cannot happen: the prefix holds three '/'
        ZipEntry[] bucket = index.buckets.get(key);
        if (SHADOW) {
            shadow(zip, prefix, bucket);
            return all;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_zip_pack_listing_index applies (zipped resource and data packs list a folder from a per-folder index of the zip instead of walking every entry)");
        }
        INDEXED.increment();
        return bucket == null ? NONE : new Bucket(bucket);
    }

    /** The name up to and including its third '/', or null when it has fewer than three. */
    static String key(String name) {
        int a = name.indexOf('/');
        if (a < 0) return null;
        int b = name.indexOf('/', a + 1);
        if (b < 0) return null;
        int c = name.indexOf('/', b + 1);
        if (c < 0) return null;
        return c == name.length() - 1 ? name : name.substring(0, c + 1);
    }

    /**
     * One walk of the open ZipFile into buckets, once per ZipFile object; null (vanilla's walk for this call) when the walk
     * fails. A zip in which two file entries share a name gets an index without buckets: every listing of that ZipFile walks
     * it as vanilla does. (ZipFile.getInputStream(entry) finds the entry by name, using the position of the entry it built last
     * when the names match; during vanilla's walk that is the entry just handed out, so a reader that opens the supplier inside
     * accept() gets that very duplicate, which the prebuilt entries could not reproduce. With unique names the name alone
     * decides, so both give the same entry in every case.)
     */
    static Index build(Holder holder, ZipFile zip) {
        synchronized (holder.bons$zipIndexLock()) {
            Index current = holder.bons$zipIndex();
            if (current != null && current.zip == zip) return current;
            try {
                Map<String, List<ZipEntry>> lists = new HashMap<>();
                Set<String> names = new HashSet<>();
                boolean duplicates = false;
                int n = 0;
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    n++;
                    if (entry.isDirectory()) continue;   // vanilla's loop skips directory entries
                    if (!names.add(entry.getName())) duplicates = true;
                    String key = key(entry.getName());
                    if (key != null) lists.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
                }
                Map<String, ZipEntry[]> buckets = null;
                if (duplicates) {
                    DUPLICATE_ZIPS.increment();
                    LOGGER.debug("Bons and Furious: vanilla_zip_pack_listing_index: {} has entries that share a name; its listings walk the zip as vanilla does", zip.getName());
                } else {
                    buckets = new HashMap<>(Math.max(16, lists.size() * 2));
                    for (Map.Entry<String, List<ZipEntry>> x : lists.entrySet()) buckets.put(x.getKey(), x.getValue().toArray(new ZipEntry[0]));
                }
                Index index = new Index(zip, buckets, n);
                holder.bons$zipIndex(index);
                BUILDS.increment();
                return index;
            } catch (RuntimeException e) {
                BUILD_FAILURES.increment();
                LOGGER.warn("Bons and Furious: vanilla_zip_pack_listing_index could not index {} ({}); this listing walks the zip as vanilla does", zip.getName(), e.toString());
                return null;
            }
        }
    }

    /** Shadow mode: vanilla's test over a full walk and over the bucket must keep the same names in the same order. */
    static void shadow(ZipFile zip, String prefix, ZipEntry[] bucket) {
        List<String> full = new ArrayList<>(), indexed = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
            ZipEntry entry = e.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(prefix)) full.add(entry.getName());
        }
        if (bucket != null) for (ZipEntry entry : bucket) if (!entry.isDirectory() && entry.getName().startsWith(prefix)) indexed.add(entry.getName());
        SHADOW_CHECKS.incrementAndGet();
        if (!full.equals(indexed)) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_zip_pack_listing_index shadow mismatch #{} in {} for {}: vanilla keeps {} entries, the index {}",
                    m, zip.getName(), prefix, full.size(), indexed.size());
        }
    }

    /** True when the switch steps aside (decided once, at the first listing; the class is transformed by then). */
    static boolean standsDown() {
        int s = standDown;
        if (s < 0) {
            String why = Neighbours.whyNot();
            standDown = s = why == null ? 0 : 1;
            if (why != null) LOGGER.info("Bons and Furious: vanilla_zip_pack_listing_index steps aside: {}; FilePackResources.listResources is left as it is", why);
        }
        return s == 1;
    }
}
