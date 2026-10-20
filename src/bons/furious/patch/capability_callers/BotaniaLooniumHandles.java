package bons.furious.patch.capability_callers;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import vazkii.botania.forge.internal_caps.ForgeInternalEntityCapabilities;

/**
 * Bons and Furious switch botania_capability_handles (Botania 1.20.1-456, both sides, since 1.0.46). No Botania code here:
 * this class only names Botania's LOONIUM_DROP constant.
 *
 * Botania's ForgeXplatImpl.looniumComponent(entity) is getCapability(LOONIUM_DROP).orElse(null), asked for living
 * entities from Botania's per-tick and drop handlers (0.21 % of the 1.0.44 entity-scene server thread, all of it the
 * dispatcher walk). LOONIUM_DROP is attached to every Mob by CapabilityUtil.makeSavedProvider, whose CapProvider answers
 * with its protected final LazyOptional field (cap.orEmpty(queryCap, lazyInstanceButNotReally)), so the answer is kept in
 * the forge_capability_handle_layer table (CallerCapabilityHandles: the LazyOptional entry, or absent for living entities
 * that are not Mobs) and handed out again while it is the same answer.
 *
 * -Dbons_and_furious.botaniaCapabilityHandles=false asks the dispatcher every time; .shadow=true (verification runs only)
 * asks it again on every hand-out and counts non-identical answers in SHADOW_CHECKS / SHADOW_MISMATCHES.
 */
public final class BotaniaLooniumHandles {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.botaniaCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.botaniaCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final CallerCapabilityHandles.Owner OWNER = CallerCapabilityHandles.register("botania_capability_handles", SHADOW,
            SHADOW_CHECKS, SHADOW_MISMATCHES, true, ForgeInternalEntityCapabilities.LOONIUM_DROP);

    private BotaniaLooniumHandles() {
    }

    /** The redirect target of looniumComponent's entity.getCapability(LOONIUM_DROP). */
    public static <T> LazyOptional<T> lookup(LivingEntity entity, Capability<T> capability) {
        if (!enabled) return entity.getCapability(capability);
        return CallerCapabilityHandles.lookup(entity, capability, OWNER);
    }
}
