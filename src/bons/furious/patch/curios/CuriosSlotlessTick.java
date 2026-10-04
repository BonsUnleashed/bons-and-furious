package bons.furious.patch.curios;

import bons.furious.mixin.curios.CapabilityProviderCuriosAccessor;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * Bons and Furious switch curios_slotless_tick_skip (Curios API 5.14.1+1.20.1, both sides).
 *
 * CuriosEventHandler.tick runs for every living entity every tick on both sides and starts with
 * CuriosApi.getCuriosInventory(entity) = entity.getCapability(CuriosCapability.INVENTORY): a walk over the entity's
 * capability providers. Curios attaches its inventory provider to every living entity, and that provider answers empty
 * whenever CuriosApi.getEntitySlots(entity) is empty, which is every entity type no datapack gives a slot to (animals,
 * fish, villagers, most modded mobs). So for those entities the walk asks every other provider and finds nothing, and
 * the handler then does nothing.
 *
 * CuriosEventHandlerSlotlessMixin sends that one call here. When the entity's type has no Curios slots (the same
 * CuriosApi.getEntitySlots(entity) question Curios' own provider asks) and a full lookup on this entity, with this same
 * capability dispatcher, already came back empty while the entity's capabilities were valid, the answer is
 * LazyOptional.empty() without the walk. Every other case runs the original lookup; an empty answer from it, while the
 * slots are empty and the capabilities valid, is remembered (the dispatcher object, on the entity).
 *
 * Why the answer is identical: with no slots, Curios' own provider answers empty, so the original lookup is empty exactly
 * when no other provider of the entity answers the inventory capability. The entity's providers are fixed when it is
 * built (Forge's CapabilityDispatcher.caps is final; the remembered dispatcher must still be the entity's), and the empty
 * answer was observed for that dispatcher; a provider answers as a function of the Capability object (the assumption of
 * every capability cache, ModernFix's faster_capabilities included), so it is empty now too. An entity on which another
 * mod's provider does answer the inventory capability is therefore never skipped; it is learned on its first lookup.
 * Slots are checked on every call, so a datapack reload that gives the type slots runs the original lookup at once.
 * An invalidated entity answers empty without asking anyone, in the original as here.
 *
 * -Dbons_and_furious.curiosSlotlessTickSkip.shadow=true (verification runs only) also runs the original lookup whenever
 * the walk is skipped, counts any present answer in SHADOW_CHECKS / SHADOW_MISMATCHES and returns the original answer.
 */
public final class CuriosSlotlessTick {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.curiosSlotlessTickSkip=false runs the lookup every tick. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosSlotlessTickSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.curiosSlotlessTickSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private CuriosSlotlessTick() {
    }

    /** CuriosApi.getCuriosInventory(entity), without the provider walk when its answer is known to be empty. */
    public static LazyOptional<ICuriosItemHandler> inventory(LivingEntity entity) {
        if (!enabled || entity == null || !CuriosApi.getEntitySlots(entity).isEmpty()) return CuriosApi.getCuriosInventory(entity);
        CuriosSlotlessState state = (CuriosSlotlessState) entity;
        CapabilityProviderCuriosAccessor caps = (CapabilityProviderCuriosAccessor) entity;
        CapabilityDispatcher dispatcher = caps.bons$curiosDispatcher();
        Object known = state.bons$curiosEmptyDispatcher();
        if (known != null && known == dispatcher) {
            if (!SHADOW) return LazyOptional.empty();
            LazyOptional<ICuriosItemHandler> original = CuriosApi.getCuriosInventory(entity);
            SHADOW_CHECKS.incrementAndGet();
            if (original.isPresent() && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: curios_slotless_tick_skip shadow mismatch: {} has a Curios inventory from another provider", entity);
            return original;
        }
        LazyOptional<ICuriosItemHandler> answer = CuriosApi.getCuriosInventory(entity);
        if (!answer.isPresent() && dispatcher != null && caps.bons$curiosCapsValid()) {
            state.bons$setCuriosEmptyDispatcher(dispatcher);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: curios_slotless_tick_skip applies (Curios' tick skips the capability lookup of entities without curio slots){}",
                        SHADOW ? " - shadow verification on" : "");
            }
        } else if (known != null) {
            state.bons$setCuriosEmptyDispatcher(null);
        }
        return answer;
    }
}
