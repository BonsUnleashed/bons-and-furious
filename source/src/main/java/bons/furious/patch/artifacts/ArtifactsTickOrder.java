package bons.furious.patch.artifacts;

import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch artifacts_living_tick_order (Artifacts; 1.21.1 tested build: Artifacts 13.2.5 for NeoForge
 * 1.21.1, with Curios 9.5.1; both sides). No Artifacts code here; the reordered conditions are in
 * ArtifactHooksNeoForgeTickOrderMixin and UmbrellaItemGlideOrderMixin.
 *
 * Artifacts' living-tick listener (ArtifactHooksNeoForge.onLivingUpdate, NeoForge EntityTickEvent.Post: every living
 * entity, every tick, client and server) runs two checks:
 *  - kitty slippers (ArtifactHooksNeoForge.onKittySlippersLivingUpdate): lastHurtByMob != null && the CREEPER_REPELLENT
 *    ability is active on lastHurtByMob (EquipmentHelper.hasAbilityActive: a scan of the attacker's armor and Curios
 *    slots) && the entity is a creeper. While an entity's last attacker is set (100 ticks after any hit by a living
 *    attacker) the middle test scans the attacker's equipment every tick, although only creepers can pass the last test;
 *  - umbrella (ArtifactHooks.livingUpdate -> UmbrellaItem.onLivingUpdate -> UmbrellaItem.shouldGlide, also the player
 *    gravity modifier's test): !onGround && falling && no slow falling && the glider option && (not in water || the
 *    SINKING ability is active: the same equipment scan of the entity) && holding an umbrella upright. The scan runs for
 *    every falling entity in water (fish, drowned, sinking mobs) although only one holding an umbrella can pass.
 * The mixins evaluate the same tests in a cheaper order: the creeper test before the attacker scan, and the water /
 * sinking test after the umbrella-held test.
 *
 * Why the result is identical: each condition is a conjunction of tests that only read (entity fields, an entity-type
 * tag, held items and use state, active effects, a config value, the equipment scan), so the order of its operands does
 * not change its value, and the branch taken (clearing lastHurtByMob; the fall-distance reset and the gravity modifier,
 * which follow shouldGlide's result) is the same. The one write any operand can make is Curios 9.5.1's lazy
 * initialisation inside the equipment scan: Curios looks every living entity up when it is constructed
 * (EntityConstructing), so the inventory attachment always exists, but the first lookup after the entity was loaded
 * from disk rebuilds the stored inventory against the current slot layout (CurioInventoryCapability's constructor ->
 * reset -> CurioInventory.init). That result depends only on the entity's slot layout and stored data, every Curios read
 * path performs it first (each lookup builds a new CurioInventoryCapability), and Curios looks every ticking living
 * entity up every tick (CuriosEventHandler.tick) and every entity a player starts tracking (playerStartTracking), so
 * performing it a moment later (in the entity's own tick instead of in a neighbour's kitty slippers check, or in Curios'
 * handler instead of Artifacts' for the same entity) changes nothing anyone reads. The only trace: a last attacker with
 * Curios slots that has not ticked, been tracked by a player or been looked up since it was loaded (in practice the
 * owner of a projectile, out of every player's tracking range in a chunk that does not tick entities), saved before its
 * first lookup, keeps its stored Curios data as loaded instead of the rebuilt copy. Errors differ by design: an
 * equipment scan that throws now throws only when the cheap tests pass.
 *
 * Applies with Artifacts' vanilla armor and Curios slot providers, the lookups checked above. When Accessories is
 * installed (the other slot provider Artifacts 13 supports on NeoForge; its lookup was not part of the check), the
 * original order runs.
 *
 * Ported to 1.21.1: Artifacts 13 replaced ArtifactEventsForge with ArtifactHooksNeoForge (EntityTickEvent.Post) and
 * moved the umbrella glide test into UmbrellaItem.shouldGlide (gravity now a DynamicAttributeModifier for players, the
 * fall-distance reset in UmbrellaItem.onLivingUpdate); the Curios lookups became EquipmentHelper.hasAbilityActive over
 * data-component abilities (CREEPER_REPELLENT, SINKING); the kitty slippers check gained a leading null test; and
 * shouldGlide already tests the water / sinking operand after the airborne, falling, slow-falling and glider-option
 * tests, so only its move behind the umbrella-held test remains. Curios' lazy first-lookup initialisation is new and is
 * covered above.
 *
 * -Dbons_and_furious.artifactsLivingTickOrder.shadow=true (verification runs only) also evaluates both conditions in the
 * original order and counts disagreements in SHADOW_CHECKS / SHADOW_MISMATCHES; the reordered result is the one used.
 */
public final class ArtifactsTickOrder {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.artifactsLivingTickOrder=false evaluates in Artifacts' order. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.artifactsLivingTickOrder", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.artifactsLivingTickOrder.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** True when Artifacts' equipment scan goes only through the slot providers checked for this switch (no Accessories). */
    private static final boolean CHECKED_PROVIDERS = checkedProviders();

    static {
        LOGGER.info("Bons and Furious: artifacts_living_tick_order {}", !CHECKED_PROVIDERS
                ? "keeps Artifacts' order (Accessories is installed; its equipment lookup was not checked for this switch)"
                : "applies (Artifacts' living tick tests the creeper type before the kitty slippers scan and the charm of sinking last)"
                        + (SHADOW ? " - shadow verification on" : enabled ? "" : " - runtime switch off"));
    }

    private ArtifactsTickOrder() {
    }

    private static boolean checkedProviders() {
        try {
            ModList mods = ModList.get();
            return mods != null && !mods.isLoaded("accessories");
        } catch (Throwable t) {
            return false;
        }
    }

    /** The reordered conditions apply: the runtime switch is on and only the checked slot providers are in use. */
    public static boolean reorder() {
        return enabled && CHECKED_PROVIDERS;
    }

    /** Shadow bookkeeping: the reordered condition against the original-order one (verification runs only). */
    public static boolean shadow(String which, boolean reordered, boolean original) {
        SHADOW_CHECKS.incrementAndGet();
        if (reordered != original && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: artifacts_living_tick_order shadow mismatch in the {} check (reordered {}, original {})", which, reordered, original);
        return reordered;
    }
}
