package bons.furious.patch.hostilenetworks_coo49;

import dev.shadowsoffire.hostilenetworks.data.DataModelRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch hostilenetworks_model_holder_memo (since 1.0.49; Hostile Neural Networks 5.3.3 with Placebo
 * 8.6.3; both sides). Helper of HnnStoredModelMixin; no code of either mod is carried.
 *
 * What Hostile Neural Networks does. Every question about a data model item goes through DataModelItem.getStoredModel:
 * read the stack's "data_model" tag; empty stack, no tag or no "id" -> the registry's empty holder; otherwise
 * DataModelRegistry.INSTANCE.holder(new ResourceLocation(tag.getString("id"))): a ResourceLocation parse (character
 * checks) and a capturing lambda for Placebo's holders.computeIfAbsent on every call. The loot fabricator asks per tick,
 * the item renderer per drawn model per frame, the deep learner's HUD four times per frame, tooltips and recipe viewers per
 * item.
 *
 * Why a memo of the holder is exact. Placebo's DynamicRegistry keeps one DynamicHolder per id in a private map that is
 * filled by computeIfAbsent (holder(id), register) and never cleared: a datapack reload only unbinds and rebinds the
 * holders it holds (jar census of Placebo 8.6.3; guarded). So holder(id) returns the same object for an id forever, and
 * equal id strings parse to equal ids. The memo maps the id STRING to the holder the first call got from the registry
 * (that call is made exactly as Hostile Neural Networks makes it: same INSTANCE read first, same parse, same holder call,
 * so a malformed id throws the same exception and is never remembered). A hit returns that same object, whose bound value
 * is read live by its users as before. At most 4,096 strings are remembered (the registry itself keeps a holder for every
 * id ever asked); beyond that the remaining ids take the original path. Empty, untagged and id-less stacks take the
 * original path.
 * -Dbons_and_furious.hostilenetworksModelHolderMemo=false runs the original. SHADOW MODE (verification runs):
 * -Dbons_and_furious.hostilenetworksModelHolderMemo.shadow=true also asks the registry on every hit and compares the
 * holder's identity (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class HnnModelHolders {
    /** hostilenetworks_model_holder_memo: -Dbons_and_furious.hostilenetworksModelHolderMemo=false runs the original. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.hostilenetworksModelHolderMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.hostilenetworksModelHolderMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final int CAP = 4096;
    private static final ConcurrentHashMap<String, DynamicHolder<?>> MEMO = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private HnnModelHolders() {
    }

    /**
     * DataModelItem.getStoredModel's answer for a stack that names a model, or null when the original must run (switch
     * off, empty stack, no tag, no id). Throws exactly what the original throws for a malformed id.
     */
    public static DynamicHolder<?> stored(ItemStack stack) {
        if (!enabled) return null;
        CompoundTag tag = stack.m_41737_("data_model");
        if (stack.m_41619_() || tag == null || !tag.m_128441_("id")) return null;
        String id = tag.m_128461_("id");
        DynamicHolder<?> held = MEMO.get(id);
        if (held != null) {
            if (SHADOW) shadow(id, held);
            return held;
        }
        DataModelRegistry registry = DataModelRegistry.INSTANCE;      // read first, as the original does
        DynamicHolder<?> holder = registry.holder(new ResourceLocation(id));
        if (MEMO.size() < CAP) MEMO.putIfAbsent(id, holder);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: hostilenetworks_model_holder_memo applies (data model items find their model by the stored id without parsing it again){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return holder;
    }

    private static void shadow(String id, DynamicHolder<?> held) {
        SHADOW_CHECKS.incrementAndGet();
        Object asked;
        try {
            asked = DataModelRegistry.INSTANCE.holder(new ResourceLocation(id));
        } catch (RuntimeException e) {
            asked = e;
        }
        if (asked != held) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: hostilenetworks_model_holder_memo shadow mismatch #{} for id '{}': kept {}, the registry answers {}", n, id, held, asked);
        }
    }

    /** Remembered ids (offline proof). */
    public static int size() {
        return MEMO.size();
    }
}
