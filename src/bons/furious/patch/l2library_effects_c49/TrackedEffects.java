package bons.furious.patch.l2library_effects_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.tags.ITag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2library_tracked_effect_memo (L2 Library 2.5.1-slim as nested in Ars Delight 1.2.2, also checked on
 * the standalone 2.5.5; both sides). L2 Library is LGPL-2.1; no L2 Library code is carried. Ours, idea from Collections Of
 * Optimizations (l2library.cacheTrackedEffects), idea text only.
 *
 * What L2 Library does: EffectSyncEvents.tracked(effect) asks TRACKED.contains(effect) and then the l2library tracked-effect
 * tag: ForgeRegistries.MOB_EFFECTS.tags().getTag(TRACKED_EFFECTS).contains(effect), which for Forge's ForgeRegistryTag is a
 * linear List.contains over the tag's contents. It runs for every MobEffectEvent Added / Remove / Expired of every entity on
 * both sides, for every effect when a player starts tracking an entity, and at login.
 *
 * What the switch does: the tag half (TRACKED.contains stays live: it is a public mutable set) answers from an identity set
 * built from the tag's current contents list. Forge rebuilds that list only when the tag is rebound (ForgeRegistryTag.bind
 * clears it; getContents makes a new one), so the snapshot is kept with the list object it was built from and rebuilt as
 * soon as getContents hands out another list. Why identical: List.contains(effect) is effect.equals(element) for some
 * element; for a MobEffect class that keeps Object's equals that is identity, which the set answers; an effect class with
 * its own equals, a null effect (List.contains throws for it on Forge's immutable lists) and any ITag that is not Forge's
 * ForgeRegistryTag call the original contains.
 *
 * Flag: -Dbons_and_furious.l2libraryTrackedEffectMemo=false restores the list walk at runtime.
 */
public final class TrackedEffects {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2libraryTrackedEffectMemo", "true"));
    private static volatile boolean announced;
    private static volatile Snapshot snapshot;

    /** Answers the contents list of a ForgeRegistryTag (an @Invoker added to Forge's class). */
    public interface Contents {
        List<Object> bons$l2library_effects_c49Contents();
    }

    private record Snapshot(List<?> list, Set<Object> members) {}

    private static final ClassValue<Boolean> PLAIN_EQUALS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("equals", Object.class).getDeclaringClass() == Object.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private static volatile Class<?> forgeTag;

    private TrackedEffects() {
    }

    /** Forge's (package-private) ForgeRegistryTag: only that exact class is answered from the snapshot. */
    private static Class<?> forgeTagClass() {
        Class<?> c = forgeTag;
        if (c == null) {
            try {
                c = Class.forName("net.minecraftforge.registries.ForgeRegistryTag", false, TrackedEffects.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                c = Void.class;
            }
            forgeTag = c;
        }
        return c;
    }

    /** In place of tag.contains(effect) in EffectSyncEvents.tracked. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean contains(ITag tag, Object effect, Operation<Boolean> original) {
        if (!enabled || effect == null || !(tag instanceof Contents c) || tag.getClass() != forgeTagClass() || !PLAIN_EQUALS.get(effect.getClass()))
            return original.call(tag, effect);
        List<Object> list = c.bons$l2library_effects_c49Contents();
        Snapshot s = snapshot;
        if (s == null || s.list != list) {
            Set<Object> members = Collections.newSetFromMap(new IdentityHashMap<>(Math.max(4, list.size() * 2)));
            members.addAll(list);
            snapshot = s = new Snapshot(list, members);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: l2library_tracked_effect_memo applies (L2 Library's tracked-effect tag is asked through a set kept per tag binding)");
            }
        }
        return s.members.contains(effect);
    }

    /** For the harness: is this effect class one whose equals is Object's. */
    public static boolean plainEquals(MobEffect e) {
        return PLAIN_EQUALS.get(e.getClass());
    }
}
