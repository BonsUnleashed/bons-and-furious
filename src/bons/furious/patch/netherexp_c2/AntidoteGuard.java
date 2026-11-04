package bons.furious.patch.netherexp_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ResourceLocationException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix netherexp_antidote_effect_guard (Jaden's Nether Expansion 2.3.5, CC-BY-NC-SA-4.0; both sides). No
 * Nether Expansion code is carried.
 *
 * AntidoteItem.getAntidoteEffect(stack) turns the stack's "AntidoteEffect" string into a ResourceLocation and looks the
 * effect up with Objects.requireNonNull(BuiltInRegistries.MOB_EFFECT.get(id)). An antidote whose stored id is malformed
 * (ResourceLocationException) or names an effect that is not registered, for example from a removed mod
 * (NullPointerException), throws from every caller: the item-colour handler (every frame the item is drawn), the
 * tooltip and drinking it. The method's own answer for "no antidote effect" is null, and all three callers handle null
 * (default colour or the stack's custom colour, no effect line, no effect applied). The fix gives that answer for those
 * two failures only: the malformed id becomes an id that names no effect (bons_and_furious:invalid_antidote_effect), so
 * the registry lookup returns null like for any unknown id, and the null check returns null instead of throwing. Every
 * valid id gives exactly the original effect.
 *
 * Runtime flag: -Dbons_and_furious.netherexpAntidoteEffectGuard=false keeps the original exceptions.
 *
 * Since 1.0.49 (idea from Collections Of Optimizations (netherexp.cacheAntidoteEffect), idea text only), two more parts:
 * (a) a fix: getAntidoteEffect, getColor (the item tint: every drawn antidote, every frame), getDuration, appendHoverText
 * and getDescriptionId (the item name) read the stack's tag through stack.getOrCreateTag(), which attaches a new empty tag
 * to a stack that has none, so merely drawing, naming or describing a tagless antidote changed the stack (it then no
 * longer equals a tagless one: stack comparison, creative moves and recipe viewers' matching differ from the server).
 * {@link #readTag} hands those reads the stack's own tag, or one shared empty tag that nothing writes when the stack has
 * none; every one of those call sites only reads (contains, getString, getInt, getBoolean). finishUsingItem is unchanged.
 * (b) exact: each read parsed the stored id
 * again (new ResourceLocation(id): validation of every character) and asked the mob-effect registry; both answers are now
 * remembered (at most 256 ids each, then nothing more is kept): the parse is a pure function of the string, and a found
 * registry entry never changes once registered (an id that names no effect is asked again every time).
 */
public final class AntidoteGuard {
    /** The tag a tagless stack's reads see (never written: every call site of readTag only reads). */
    private static final CompoundTag EMPTY_READ_ONLY = new CompoundTag();
    private static final int MEMO_LIMIT = 256;
    private static final ConcurrentHashMap<String, ResourceLocation> PARSED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceLocation, Object> FOUND = new ConcurrentHashMap<>();
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.netherexpAntidoteEffectGuard", "true"));
    /** Valid, never registered: the registry answers null for it. */
    public static final ResourceLocation NO_EFFECT = new ResourceLocation("bons_and_furious", "invalid_antidote_effect");
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Lookups answered with "no effect" instead of an exception (for probes and the harness). */
    public static volatile long guarded;

    private AntidoteGuard() {
    }

    /** The @WrapOperation handler for {@code new ResourceLocation(storedId)} in getAntidoteEffect. */
    public static ResourceLocation parse(String id, Operation<ResourceLocation> original) {
        if (!enabled) return original.call(id);
        ResourceLocation kept = id == null ? null : PARSED.get(id);
        if (kept != null) {
            if (kept == NO_EFFECT) note("malformed antidote effect id '" + id + "'");
            return kept;
        }
        ResourceLocation parsed;
        try {
            parsed = original.call(id);
        } catch (ResourceLocationException e) {
            note("malformed antidote effect id '" + id + "'");
            parsed = NO_EFFECT;
        }
        if (id != null && PARSED.size() < MEMO_LIMIT) PARSED.putIfAbsent(id, parsed);
        return parsed;
    }

    /** In place of stack.getOrCreateTag() in getAntidoteEffect, getColor and appendHoverText: read without creating. */
    public static CompoundTag readTag(ItemStack stack) {
        if (!enabled) return stack.m_41784_();
        CompoundTag tag = stack.m_41783_();
        return tag != null ? tag : EMPTY_READ_ONLY;
    }

    /** The @WrapOperation handler for the mob-effect registry lookup in getAntidoteEffect. */
    public static Object lookup(net.minecraft.core.Registry<?> registry, ResourceLocation id, Operation<Object> original) {
        if (!enabled || id == null) return original.call(registry, id);
        Object kept = FOUND.get(id);
        if (kept != null) return kept;
        Object found = original.call(registry, id);
        if (found != null && FOUND.size() < MEMO_LIMIT) FOUND.putIfAbsent(id, found);
        return found;
    }

    /** For the harness: is the shared empty tag still empty. */
    public static boolean readOnlyTagEmpty() {
        return EMPTY_READ_ONLY.m_128456_();
    }

    /** The @WrapOperation handler for {@code Objects.requireNonNull(effect)} in getAntidoteEffect. */
    public static Object require(Object effect, Operation<Object> original) {
        if (effect == null && enabled) {
            note("antidote effect that is not registered");
            return null;
        }
        return original.call(effect);
    }

    private static void note(String what) {
        guarded++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: netherexp_antidote_effect_guard: an antidote with a {} is treated as having no effect (Nether Expansion would have thrown)", what);
        }
    }
}
