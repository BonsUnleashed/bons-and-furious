package bons.furious.patch.l2library_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import dev.xkmc.l2library.base.effects.ClientEffectCap;
import dev.xkmc.l2library.capability.entity.GeneralCapabilityHolder;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.slf4j.Logger;

/**
 * Bons and Furious switch l2library_effect_icon_fast_path (L2 Library 2.5.1-slim, the copy Jar-in-Jar'd by Ars Delight
 * 1.2.2; client only). No L2 code is carried here.
 *
 * ClientEffectRenderEvents.onLivingRenderEvents runs after every rendered living entity, every frame (RenderLivingEvent.Post,
 * and GeoRenderEvent.Entity.Post for GeckoLib entities). It looks the entity's ClientEffectCap up (isProper), checks the
 * entity's "ClientOnly" tag, looks the same capability up a second time (HOLDER.get = getCapability(cap).resolve().get()
 * .check()), collects the entries of the capability's effect map that are client render effects, and returns when there
 * are none. The map only fills when the server syncs a tracked effect (none are tracked in this pack, so it is always empty).
 *
 * Now the first lookup is made by the wrapped isProper call itself (the body of isProper, fingerprinted:
 * getCapability(HOLDER.capability).isPresent()), and when the capability is present but its effect map is empty the
 * method returns there. Identical: with an empty map every path of the original ends in a plain return (at the tag check
 * or after iterating nothing); the tag check (Entity.getTags().contains) and the empty iteration are pure reads; the
 * capability object is the one the second lookup would return. Absent capability: the same single lookup and return as
 * before. Non-empty map (or a capability object of another class): the original continues unchanged (tag check, second
 * lookup, icon pass), so it makes exactly the lookups it made before.
 *
 * ONE DOCUMENTED INTERNAL-ONLY DIFFERENCE: for an entity whose effect map is empty (and that has no "ClientOnly" tag) the
 * second, identical capability lookup of the same entity is not made. Capability lookups are queries (Forge's contract);
 * all 443 getCapability(Capability, Direction) implementations in the tested pack were audited (no counting or logging;
 * the lazily initialising ones are idempotent), so the lookup has no effect anyone can observe.
 *
 * SHADOW MODE for rigs: -Dbons_and_furious.l2libraryEffectIconFastPath.shadow=true lets the original run on every call
 * and, where the fast path would have returned, checks that the second lookup gives the same capability object with the
 * map still empty (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN at most 20). Shadow mode makes one extra lookup per such call.
 */
public final class EffectIconFastPath {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.l2libraryEffectIconFastPath=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2libraryEffectIconFastPath", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.l2libraryEffectIconFastPath.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced;

    private EffectIconFastPath() {
    }

    /** In place of {@code ClientEffectCap.HOLDER.isProper(entity)} at the start of onLivingRenderEvents. */
    public static boolean proper(GeneralCapabilityHolder<?, ?> holder, ICapabilityProvider entity, Operation<Boolean> original) {
        if (!enabled || holder != ClientEffectCap.HOLDER) return original.call(holder, entity);
        LazyOptional<?> found = entity.getCapability(holder.capability);    // isProper's own lookup
        if (!found.isPresent()) return false;
        if (found.orElse(null) instanceof ClientEffectCap cap && cap.map.isEmpty()) {
            if (SHADOW) {
                shadow(holder, entity, cap);
                return true;
            }
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: l2library_effect_icon_fast_path: L2 Library's effect-icon pass returns at once for entities without client effects");
            }
            return false;
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void shadow(GeneralCapabilityHolder holder, ICapabilityProvider entity, ClientEffectCap cap) {
        Object again = holder.get(entity);
        SHADOW_CHECKS.incrementAndGet();
        if (again != cap || !cap.map.isEmpty()) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: l2library_effect_icon_fast_path SHADOW MISMATCH {}: the second lookup of {} gave {} (first {}), map size {}",
                    n, entity, again, cap, cap.map.size());
        }
    }
}
