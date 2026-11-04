package bons.furious.patch.mowziesmobs_attach_c49;

import com.bobmowzie.mowziesmobs.server.capability.AbilityCapability;
import com.bobmowzie.mowziesmobs.server.capability.FrozenCapability;
import com.bobmowzie.mowziesmobs.server.capability.LivingCapability;
import com.bobmowzie.mowziesmobs.server.capability.PlayerCapability;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mowziesmobs_capability_attach_dedupe (Mowzie's Mobs 1.7.3, also checked on 1.8.2; both sides), an
 * exact variant. Mowzie's Mobs has its own licence (treated as All Rights Reserved): no Mowzie code is carried, only an early
 * return in front of one of its listeners. Ours, idea from Collections Of Optimizations (mowziesmobs.dedupeCapabilityAttach),
 * idea text only.
 *
 * What Mowzie's Mobs does: two of its listeners attach capabilities to every entity. ServerEventHandler.onAttachCapabilities
 * (registered first) gives every LivingEntity a frozen (mowziesmobs:frozen), a last-damage (mowziesmobs:last_damage) and an
 * ability (mowziesmobs:ability) provider and every Player also a player provider (mowziesmobs:player);
 * CapabilityHandler.attachEntityCapability (registered after it) adds a second living and frozen provider to every
 * LivingEntity and a second player and ability provider to every Player, under other ids. Every one of those providers
 * answers its own capability with a final LazyOptional that is never invalidated, and Forge's dispatcher answers a lookup
 * with the first provider that has the capability, in the order they were added: the second set is never read, but it is
 * built for every living entity, walked by every capability lookup of any mod, and saved (and on save its instances are
 * created) with every entity.
 *
 * What the switch does: attachEntityCapability returns at once when the event already holds, under ServerEventHandler's ids,
 * Mowzie's own providers (exact classes) for every capability it would add: frozen and living for a LivingEntity, and also
 * player and ability for a Player. Otherwise (another order, a missing or replaced provider, a non-living entity) it runs as
 * shipped. Exact variant: every Mowzie capability lookup answers the same provider's instance as before (the first set's);
 * living entities stop carrying and saving the never-read second copy, so their saved ForgeCaps lose that copy's tags
 * mowziesmobs:living_cap and mowziesmobs:frozen_cap (players also mowziesmobs:player_cap and mowziesmobs:ability_cap), which
 * only ever held default values (old saves load: Forge ignores tags without a provider), and lookups of other capabilities
 * walk two (players four) providers fewer.
 *
 * Flag: -Dbons_and_furious.mowziesmobsCapabilityAttachDedupe=false restores the second listener at runtime.
 */
public final class MowzieAttachDedupe {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsCapabilityAttachDedupe", "true"));
    private static final ResourceLocation FROZEN = new ResourceLocation("mowziesmobs", "frozen");
    private static final ResourceLocation LAST_DAMAGE = new ResourceLocation("mowziesmobs", "last_damage");
    private static final ResourceLocation ABILITY = new ResourceLocation("mowziesmobs", "ability");
    private static final ResourceLocation PLAYER = new ResourceLocation("mowziesmobs", "player");
    private static volatile boolean announced;

    private MowzieAttachDedupe() {
    }

    /** True when CapabilityHandler.attachEntityCapability would only add providers that are never read for this event. */
    public static boolean alreadyAttached(AttachCapabilitiesEvent<Entity> event) {
        if (!enabled) return false;
        Object entity = event.getObject();
        if (!(entity instanceof LivingEntity)) return false;
        Map<ResourceLocation, ICapabilityProvider> caps = event.getCapabilities();
        if (!is(caps, FROZEN, FrozenCapability.FrozenProvider.class) || !is(caps, LAST_DAMAGE, LivingCapability.LivingProvider.class)) return false;
        if (entity instanceof Player && (!is(caps, PLAYER, PlayerCapability.PlayerProvider.class) || !is(caps, ABILITY, AbilityCapability.AbilityProvider.class))) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mowziesmobs_capability_attach_dedupe applies (entities keep one set of Mowzie's Mobs capabilities instead of two)");
        }
        return true;
    }

    private static boolean is(Map<ResourceLocation, ICapabilityProvider> caps, ResourceLocation id, Class<?> provider) {
        ICapabilityProvider p = caps.get(id);
        return p != null && p.getClass() == provider;
    }
}
