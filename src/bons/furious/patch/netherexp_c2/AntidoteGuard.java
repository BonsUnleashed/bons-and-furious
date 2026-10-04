package bons.furious.patch.netherexp_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
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
 */
public final class AntidoteGuard {
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
        try {
            return original.call(id);
        } catch (ResourceLocationException e) {
            note("malformed antidote effect id '" + id + "'");
            return NO_EFFECT;
        }
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
