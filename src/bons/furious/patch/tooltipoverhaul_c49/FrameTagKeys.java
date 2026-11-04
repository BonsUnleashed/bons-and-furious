package bons.furious.patch.tooltipoverhaul_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch tooltipoverhaul_frame_tag_keys_memo (Tooltip Overhaul 2.0.6, tested build; client; since 1.0.49).
 * Exact. Idea: Collections Of Optimizations (tooltipoverhaul.cacheFrameTagKeys), idea text only.
 *
 * What Tooltip Overhaul does. While a tooltip is shown, CustomFrameManager.findMatch asks every loaded frame definition for
 * its CustomFrameData.matchScore(stack) on every frame; for each definition whose item list does not name the item,
 * matchScore calls getTagKeys(), which streams the definition's tag strings into a new list of new ResourceLocations
 * (parse + validation) and TagKey.create lookups (Minecraft's tag key interner), and then tests stack.is(tag) for each.
 *
 * What the switch does. MixinExtras @WrapOperation on that one internal getTagKeys() call in matchScore
 * (FrameTagKeysMixin): the list it built is kept on the definition with a snapshot of the tag strings it was built from
 * (the list's size and every element reference); while the definition's tags list still has that size and the same element
 * references, matchScore gets the kept list again; otherwise getTagKeys() runs as shipped and its result is kept. Why
 * identical: the list only ever feeds matchScore's own loop (nothing else sees it); TagKey.create hands out interned keys,
 * so the kept keys are the keys a new call creates, and stack.is(tag) compares keys by value anyway; a call that throws (a
 * tag string that is not a valid resource location, a null entry) is never kept, so it throws again on every call exactly
 * as before. Other callers of getTagKeys() still get a fresh list each time.
 *
 * -Dbons_and_furious.tooltipoverhaulFrameTagKeysMemo=false runs getTagKeys() on every call again (emergency off).
 * -Dbons_and_furious.tooltipoverhaulFrameTagKeysMemo.shadow=true (verification runs only): getTagKeys() runs on every call as
 * shipped; SHADOW_CHECKS counts the calls the memo would have answered, SHADOW_MISMATCHES those where the kept list differs
 * from the fresh one (WARN, at most 20).
 */
public final class FrameTagKeys {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tooltipoverhaulFrameTagKeysMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.tooltipoverhaulFrameTagKeysMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private FrameTagKeys() {
    }

    /** What a definition keeps: the tag strings (references) the list was built from, and the list. Immutable. */
    public static final class Memo {
        private final Object[] names;     // null = not to be kept (the switch is off)
        private final List<TagKey<Item>> keys;

        Memo(Object[] names, List<TagKey<Item>> keys) {
            this.names = names;
            this.keys = keys;
        }

        /** The list matchScore iterates. */
        public List<TagKey<Item>> keys() {
            return this.keys;
        }

        /** True while the definition's tags list has the size and the element references the kept list was built from. */
        public boolean builtFrom(List<String> tags) {
            Object[] n = this.names;
            if (n == null || tags == null || tags.size() != n.length) return false;
            for (int i = 0; i < n.length; i++) if (tags.get(i) != n[i]) return false;
            return true;
        }

        /** Whether the definition should keep this memo. */
        public boolean keep() {
            return this.names != null;
        }
    }

    /** The answer when the kept memo does not hold (or the switch is off, or shadow mode): getTagKeys() as shipped. */
    public static Memo rebuild(Object self, List<String> tags, Memo kept, Operation<List<TagKey<Item>>> original) {
        if (!enabled || tags == null) return new Memo(null, original.call(self));
        Object[] names = tags.toArray();
        List<TagKey<Item>> fresh = original.call(self);      // throws as shipped; nothing is kept then
        if (!announced) announce();
        if (SHADOW && kept != null && kept.builtFrom(tags)) {
            SHADOW_CHECKS.incrementAndGet();
            if (!kept.keys.equals(fresh) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: tooltipoverhaul_frame_tag_keys_memo shadow mismatch for tags {}: kept {}, fresh {}", tags, kept.keys, fresh);
            return kept.keys.equals(fresh) ? new Memo(kept.names, fresh) : new Memo(names, fresh);
        }
        return new Memo(names.length == tags.size() ? names : null, fresh);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: tooltipoverhaul_frame_tag_keys_memo applies (Tooltip Overhaul frame definitions keep their parsed tag keys instead of parsing them for every tooltip frame)"
                + (SHADOW ? " [shadow mode: every call still parses and is compared]" : ""));
    }
}
