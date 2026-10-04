package bons.furious.patch.curios_tooltip;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.SlotResult;

/**
 * Bons and Furious switch curios_tag_predicate_keys (Curios API, LGPL-3.0-or-later; 1.21.1 tested build
 * curios-neoforge-9.5.1+1.21.1; both sides).
 *
 * <p>Curios registers one built-in slot validator under "curios:tag" (the synthetic CuriosImplMixinHooks.lambda$static$8,
 * the validator of every slot type that does not name another one). Per call it builds
 * {@code ItemTags.create(ResourceLocation.fromNamespaceAndPath("curios", id))} for the slot id and
 * {@code ItemTags.create(ResourceLocation.fromNamespaceAndPath("curios", "curio"))}, then answers
 * {@code stack.is(tag1) || stack.is(tag2)}. Curios' tooltip handler asks it for every slot type of the player on every
 * item tooltip (CuriosApi.getItemStackSlots -> testCurioPredicates), so JEI's ingredient filter build at world join runs it
 * slot-types x ingredients times: two ResourceLocation constructions with path validation and two Guava interner lookups
 * each time (4.6% of our test rig's 9.0 s "Building ingredient filter" window on 1.20.1, cli10_jfr1).
 *
 * <p>This class answers the same question with the two TagKeys kept: one per slot id (built the first time an id is seen,
 * exactly as the original builds it) and the constant curios:curio key. Why the answer is identical:
 * <ul>
 * <li>TagKey is a record of (registry key, location); ItemTags.create(rl) = TagKey.create(Registries.ITEM, rl) =
 *   Interner.intern(new TagKey(ITEM, rl)) (weak interner). Equal ids give equal locations and equal keys, and
 *   ItemStack.is(TagKey) -> Holder.Reference.is -> the bound tag set's contains, which compares keys by equals/hashCode.
 *   A kept key equals the key the original would build, so both calls of stack.is return the same booleans, in the same
 *   order (is(tag1) first, is(tag2) only when the first is false) with the same stack object.</li>
 * <li>Identity: the original returns the interner's canonical instance; the kept key IS that canonical instance (the result
 *   of the first create), and keeping it reachable keeps the interner answering with it, so any later create of an equal
 *   key anywhere in the game returns this same object, as it would in the original while that instance is alive. (In the
 *   original a weakly interned key with no other holder may be collected between two calls and a new canonical instance
 *   made; keeping one alive is one of the states the original itself can be in.)</li>
 * <li>Exceptions: a key is kept only for ids whose every character is valid in a vanilla path (a-z 0-9 _ - . /). On
 *   1.21.1 fromNamespaceAndPath = createUntrusted = new ResourceLocation(assertValidNamespace(ns, path),
 *   assertValidPath(ns, path)); "curios" is a valid namespace, and for such ids isValidPath holds, so the original
 *   construction cannot throw. No mixin of any pinned 1.21.1 target jar (jar-in-jar included) targets ResourceLocation,
 *   TagKey or ItemTags, and NeoForge's own mixins do not either. Any other id (null, uppercase, spaces, ...) is built
 *   through the original expression on every call, so it throws or passes exactly as before.
 *   "curios"/"curio" is valid by the same rule.</li>
 * <li>Calls the switch no longer makes: the per-call ResourceLocation constructions and the interner lookups (pure).</li>
 * </ul>
 * Thread safety: tooltips run on the render thread, slot checks on the server thread; the kept keys sit in a
 * ConcurrentHashMap and are immutable records. At most {@link #MAX_KEPT} ids are kept; further ids take the original
 * expression every time.
 *
 * <p>Ported to 1.21.1: ResourceLocation.fromNamespaceAndPath replaces the 1.20.1 public constructor (same validation:
 * namespace, then path, ResourceLocationException or NullPointerException as before); the wrapped lambda is
 * lambda$static$8; the 1.20.1 pack's ResourceLocation hooks (Oculus, CIT Reforged, Entity Texture Features, All The Leaks)
 * have no counterpart among the 1.21.1 targets.
 */
public final class CuriosTagKeys {
    /** Runtime switch. -Dbons_and_furious.curiosTagPredicateKeys=false turns it off (the original lambda runs). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosTagPredicateKeys", "true"));
    /** Shadow mode for rigs: every call also runs Curios' own validator, compares answers and keys, and returns Curios' answer. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.curiosTagPredicateKeys.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Keys built through the original expression (first sight of a keepable id, or an id that is never kept). */
    public static final AtomicLong BUILT = new AtomicLong();

    static final int MAX_KEPT = 4096;
    private static final ConcurrentHashMap<String, TagKey<Item>> SLOT_TAGS = new ConcurrentHashMap<>();
    private static volatile TagKey<Item> curioTag;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private CuriosTagKeys() {
    }

    /**
     * True when the result, its context and its stack are not null. Otherwise the wrapper runs Curios' lambda, so the
     * NullPointerException it throws (with its helpful message) stays Curios' own. The record accessors are plain field
     * reads; reading them once more is unobservable.
     */
    public static boolean handles(SlotResult slotResult) {
        return slotResult != null && slotResult.slotContext() != null && slotResult.stack() != null;
    }

    /** The "curios:tag" validator with kept keys (same calls on the slot result and the stack as Curios' lambda). */
    public static boolean test(SlotResult slotResult) {
        String id = slotResult.slotContext().identifier();
        TagKey<Item> tag1 = slotTag(id);
        TagKey<Item> tag2 = curioTag();
        ItemStack stack = slotResult.stack();
        return stack.is(tag1) || stack.is(tag2);   // ItemStack.is(TagKey), as Curios calls it
    }

    /** Shadow mode: Curios' own answer is returned; ours and the keys are compared with it. */
    public static boolean shadow(SlotResult slotResult, Predicate<SlotResult> original) {
        boolean theirs = original.test(slotResult);
        boolean ours;
        boolean keysEqual;
        try {
            ours = test(slotResult);
            String id = slotResult.slotContext().identifier();
            keysEqual = slotTag(id).equals(ItemTags.create(ResourceLocation.fromNamespaceAndPath("curios", id)))
                    && curioTag().equals(ItemTags.create(ResourceLocation.fromNamespaceAndPath("curios", "curio")));
        } catch (RuntimeException e) {
            ours = !theirs;   // the original answered, ours threw: count it
            keysEqual = false;
        }
        SHADOW_CHECKS.incrementAndGet();
        if (ours != theirs || !keysEqual) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) {
                LOGGER.warn("Bons and Furious: curios_tag_predicate_keys shadow mismatch for slot '{}' and {}: original {}, kept keys {}{}",
                        slotResult.slotContext().identifier(), slotResult.stack(), theirs, ours, keysEqual ? "" : " (keys differ)");
            }
        }
        return theirs;
    }

    /** The slot's tag key: kept for ids with a vanilla-valid path, else built through the original expression. */
    static TagKey<Item> slotTag(String id) {
        if (id != null) {
            TagKey<Item> kept = SLOT_TAGS.get(id);
            if (kept != null) return kept;
        }
        TagKey<Item> built = ItemTags.create(ResourceLocation.fromNamespaceAndPath("curios", id));   // the original expression (may throw)
        BUILT.incrementAndGet();
        if (keepable(id) && SLOT_TAGS.size() < MAX_KEPT) {
            TagKey<Item> prev = SLOT_TAGS.putIfAbsent(id, built);
            if (prev != null) return prev;   // a racing thread kept an equal key first (the interner gave it the same object)
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: curios_tag_predicate_keys keeps the TagKeys of Curios' curios:tag slot validator per slot id");
            }
        }
        return built;
    }

    static TagKey<Item> curioTag() {
        TagKey<Item> t = curioTag;
        if (t == null) {
            t = ItemTags.create(ResourceLocation.fromNamespaceAndPath("curios", "curio"));   // valid path: cannot throw
            curioTag = t;   // benign race: both threads get the interner's canonical key
        }
        return t;
    }

    /** True when every character is a vanilla path character (a-z 0-9 _ - . /): the original construction cannot throw. */
    static boolean keepable(String id) {
        if (id == null) return false;
        for (int i = 0, n = id.length(); i < n; i++) {
            char c = id.charAt(i);
            if (!(c == '_' || c == '-' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.')) return false;
        }
        return true;
    }

    /** Ids kept so far (for probes and the harness). */
    public static int keptCount() {
        return SLOT_TAGS.size();
    }
}
