package bons.furious.patch.transmog_coo49;

import com.hidoni.transmog.TransmogUtils;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch transmog_appearance_cache_identity (Transmog forge-1.3.0+1.20, MIT; physical client). Idea:
 * Collections Of Optimizations (transmog.identityAppearanceCache), idea text only; the tables are ours.
 *
 * <p>TransmogUtils.getAppearanceItemStack turns the appearance stored in an item's "transmog:transmogItem" compound back
 * into an item stack whenever the item is drawn (three times per drawn item: model, render, decorations; per armour slot
 * of every drawn player and mob) and for its tooltip. It caches the result in one static access-ordered LinkedHashMap of
 * 101 entries keyed by the compound's hashCode(): every lookup walks the whole appearance compound to compute that hash,
 * boxes it, allocates a lambda and reorders the shared map, which the render thread and the integrated server thread both
 * use without a lock.
 *
 * <p>Trade-off (change, off by default; the first two differences below can show on screen): after the mod's own test (an untransmogged or empty stack is answered by
 * the mod as before), client threads take the appearance from two small tables shared by the client threads. The first is
 * keyed by the stored compound OBJECT (64 sets of 2): the same compound asked again (an item drawn frame after frame) is
 * answered without hashing it. Its set comes from the identity of the compound's "id" tag, an immutable tag that copies
 * of the compound share, so a new copy costs no new identity hash. The second is keyed by the compound's hash, as
 * Transmog's own cache is (128 entries): a compound object not seen yet gets the appearance of its hash when that hash was
 * decoded already, as Transmog would hand out its cached one; a hash not seen is decoded with Transmog's own
 * getAppearanceStackFromTag. Entries are immutable, so a slot always holds a whole entry without locks. What follows from
 * it: when two different appearances have one hash code (Transmog's cache then shows one look for both), a compound keeps
 * the look its first lookup found, so which of the two looks an item shows can differ from the shared map's pick; and a
 * compound changed in place on the client keeps its earlier look until the item is replaced (Transmog itself always
 * stores a new compound: ItemStack.addTagElement; stacks sent by the server arrive as new compounds). The logical server
 * thread (the transmog table's menu) keeps the mod's own cache, which now has only that thread, so its appearance stacks
 * are no longer shared with the render thread. Everything else (the hidden-item rule) is unchanged.
 *
 * <p>-Dbons_and_furious.transmogAppearanceCacheIdentity=false turns it off at run time. SHADOW MODE for rigs:
 * -Dbons_and_furious.transmogAppearanceCacheIdentity.shadow=true answers every by-object hit the hash way too (the hash
 * table, or a fresh decode) and compares item and tag (the count is the one Transmog sets while drawing): SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20. A mismatch is an appearance compound changed in place, or two different
 * appearances with one hash code.
 */
public final class TransmogAppearances {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.transmogAppearanceCacheIdentity", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.transmogAppearanceCacheIdentity.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final int SETS = 64, HASHES = 128;
    private static final Entry[] BY_OBJECT = new Entry[SETS * 2];
    private static final Entry[] BY_HASH = new Entry[HASHES];
    private static volatile boolean announced;

    /** One kept appearance: for a stored compound object (by-object table) or for a hash code (by-hash table, tag null). */
    static final class Entry {
        final CompoundTag tag;
        final int hash;
        final ItemStack appearance;

        Entry(CompoundTag tag, int hash, ItemStack appearance) {
            this.tag = tag;
            this.hash = hash;
            this.appearance = appearance;
        }
    }

    private TransmogAppearances() {
    }

    /** True when the call runs on the logical server's own thread (it keeps the mod's cache). */
    static boolean onServerThread() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.m_18695_();
    }

    /** The first slot of the compound's by-object set: from the identity of its "id" tag (0 without one). */
    static int set(CompoundTag tag) {
        Tag id = tag.m_128423_("id");
        int h = id == null ? 0 : System.identityHashCode(id);
        return ((h ^ (h >>> 16)) & (SETS - 1)) << 1;
    }

    static int slot(int hash) {
        return (hash ^ (hash >>> 16)) & (HASHES - 1);
    }

    /**
     * The answer of getAppearanceItemStack(stack, keepHidden) for a transmogged, non-empty stack whose stored appearance
     * compound is tag, or null when the mod's own code must answer (switch off, or the logical server thread).
     */
    public static ItemStack appearance(CompoundTag tag, boolean keepHidden) {
        if (!enabled || onServerThread()) return null;
        Entry[] t = BY_OBJECT;
        int s = set(tag);
        Entry e = t[s];
        if (e == null || e.tag != tag) {
            e = t[s + 1];
            if (e != null && e.tag != tag) e = null;
        }
        ItemStack appearance;
        if (e != null) {
            appearance = e.appearance;
            if (SHADOW) shadow(tag, appearance);
        } else {
            int h = tag.hashCode();
            appearance = byHash(tag, h);
            t[s + 1] = t[s];
            t[s] = new Entry(tag, h, appearance);
        }
        if (!keepHidden && TransmogUtils.isHiddenItem(appearance)) return ItemStack.f_41583_;
        return appearance;
    }

    /** The hash-keyed answer, as Transmog's own cache gives it (a hash seen before hands out its appearance). */
    static ItemStack byHash(CompoundTag tag, int h) {
        int j = slot(h);
        Entry b = BY_HASH[j];
        if (b != null && b.hash == h) return b.appearance;
        ItemStack a = TransmogUtils.getAppearanceStackFromTag(tag);
        BY_HASH[j] = new Entry(null, h, a);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: transmog_appearance_cache_identity applies (Transmog's drawn appearances are kept per stored tag on the client)");
        }
        return a;
    }

    private static void shadow(CompoundTag tag, ItemStack kept) {
        SHADOW_CHECKS.incrementAndGet();
        int h = tag.hashCode();
        Entry b = BY_HASH[slot(h)];
        ItemStack expected = b != null && b.hash == h ? b.appearance : TransmogUtils.getAppearanceStackFromTag(tag);
        if (expected.m_41720_() != kept.m_41720_() || !Objects.equals(expected.m_41783_(), kept.m_41783_())) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: transmog_appearance_cache_identity SHADOW MISMATCH {}: kept {} but the tag gives {}", n, kept, expected);
        }
    }
}
