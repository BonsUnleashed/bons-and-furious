package bons.furious.patch.reliquary_charm;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reliquary.util.RegistryHelper;

/**
 * Bons and Furious switch reliquary_mob_charm_type_gate (Reliquary 2.0.45.1248, licence unstated: treated as all rights
 * reserved, so no Reliquary code is carried; server side; since 1.0.46). Helper of the reliquary_charm mixins.
 *
 * <p>Reliquary's MobCharmItem listens to every LivingTickEvent. For a Mob on the server it looks for a real player as the
 * mob's target, its last attacker, or in five of its brain memories, and only when one is found asks MobCharmRegistry for
 * the charm definition of the mob's type (registry name of entity.getType() as a string, looked up in
 * ENTITY_NAME_CHARM_DEFINITIONS); without a definition nothing happens. That map is private to MobCharmRegistry and written
 * only by registerMobCharmDefinition (its static initialiser and registerDynamicCharmDefinitions at FMLLoadCompleteEvent:
 * every MONSTER type not blocked). So for a mob whose TYPE has no definition the whole handler can only end without an
 * effect: the reads before the lookup (getTarget, getLastHurtByMob, getBrain and Brain.hasMemoryValue / getMemory) are pure
 * in this pack (census of every getTarget, getLastHurtByMob, getBrain and getType override, 2026-10-19:
 * evidence/census/reliquary-overrides.txt; no mixin touches Reliquary's classes, and Brain's foreign hooks act only when a
 * brain is built or inside its guarded setMemoryInternal).
 *
 * <p>The gate answers "this type has no definition" from a memo on the EntityType itself (one int: the registry version it
 * was computed for and the answer), recomputed whenever registerMobCharmDefinition ran since (its TAIL hands us the map and
 * bumps a version counter), and returns from the handler at its head. Its lookup is the same function of the type Reliquary
 * uses (ForgeRegistries.ENTITY_TYPES.getKey(type).toString() in that map); a type without a registry key (where Reliquary's
 * own requireNonNull would throw once a player is found) is never gated, and neither is anything before the map was handed
 * over. Charmed types, client levels and non-mobs run Reliquary's handler as shipped.
 *
 * <p>-Dbons_and_furious.reliquaryMobCharmTypeGate=false turns the gate off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.reliquaryMobCharmTypeGate.shadow=true never skips; where the gate would, it repeats Reliquary's own
 * lookup (RegistryHelper.getRegistryName(entity).toString() in the map) and counts SHADOW_CHECKS / SHADOW_MISMATCHES (a
 * definition found; WARN for the first 20), then the handler runs as shipped.
 */
public final class ReliquaryCharmGate {
    /** Runtime switch. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.reliquaryMobCharmTypeGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.reliquaryMobCharmTypeGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes and the rig (plain field; the server thread writes it). */
    public static long skipped;
    /** MobCharmRegistry.ENTITY_NAME_CHARM_DEFINITIONS (read only), handed over at its first registration; null before. */
    private static volatile Map<String, ?> definitions;
    /** Bumped at the TAIL of every MobCharmRegistry.registerMobCharmDefinition: every memo computed before is stale. */
    private static volatile int version;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private ReliquaryCharmGate() {
    }

    /** The TAIL of MobCharmRegistry.registerMobCharmDefinition, with Reliquary's per-entity definition map. */
    public static void charmsChanged(Map<String, ?> byEntityName) {
        synchronized (ReliquaryCharmGate.class) {
            definitions = byEntityName;
            version++;
        }
    }

    /**
     * True when Reliquary's per-tick mob-charm handler can do nothing for {@code entity}: a server-side Mob whose type has no
     * charm definition. False means "unknown or charmed": the handler runs.
     */
    public static boolean noCharm(LivingEntity entity) {
        if (!enabled || !(entity instanceof Mob) || entity.m_9236_().m_5776_()) return false;
        Map<String, ?> map = definitions;
        if (map == null) return false;
        EntityType<?> type = entity.m_6095_();
        if (!(type instanceof CharmTypeMemo memo)) return false;
        int v = version;
        int stamp = (v + 1) & 0x3FFFFFFF;
        int m = memo.bons$charmMemo();
        boolean none;
        if (m != 0 && (m >>> 1) == stamp) none = (m & 1) != 0;
        else {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null) return false;
            none = map.get(key.toString()) == null;
            memo.bons$charmMemo((stamp << 1) | (none ? 1 : 0));
        }
        if (!none) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: reliquary_mob_charm_type_gate: Reliquary's mob-charm tick returns at once for mob types that have no charm{}",
                    SHADOW ? " (SHADOW MODE: the handler runs every time and is compared)" : "");
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            if (map.get(RegistryHelper.getRegistryName(entity).toString()) != null && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: reliquary_mob_charm_type_gate SHADOW MISMATCH {}: the memo says {} has no charm, Reliquary has one",
                        SHADOW_MISMATCHES.get(), type);
            return false;
        }
        skipped++;
        return true;
    }
}
