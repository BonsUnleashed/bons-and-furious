package bons.furious.patch.datapack_selectors;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_selector_single_type (Minecraft 1.21.1 with NeoForge 21.1.252; server side incl. the
 * integrated server). Mojang member names. Idea: Bellows ("entities are cached based on their type ... perform the @e
 * search only on the smallest cache"), idea text only; the form (vanilla's own type test, byId order, the whole predicate
 * chain) is ours.
 *
 * What vanilla does. A whole-level selector with one entity type (`@e[type=minecraft:zombie,...]`) hands its EntityType to
 * ServerLevel.getEntities as the type test, and EntityLookup.getEntities walks every loaded entity of the level and asks
 * the type (EntityType.tryCast: entity.getType() == type) for each.
 *
 * What the switch does. In EntitySelector.addEntities the type test is wrapped in a SingleTypeTest (same tryCast and base
 * class, delegated to the EntityType). EntityLookup.getEntities (vanilla_selector_type_index's EntityLookupMixin) answers
 * a SingleTypeTest from the level's TypeIndex: the entities filed under that type plus the entities of classes with their
 * own isAlive/getHealth/getType (which still get tryCast), merged in byId's order; the selector's predicate chain then
 * runs on each, exactly as before.
 *
 * Why the result is identical. An entity's type is a final field and Entity.getType returns it for every class the index
 * files by type, so tryCast lets through exactly the entities filed under the type, plus those of the "own" classes for
 * which tryCast (still called) says so; the order is byId's (TypeIndex's sequence numbers), so the selected list, its order
 * and the limit's cut-off are vanilla's. Without the index (vanilla_selector_type_index off) the wrapped test simply goes
 * through vanilla's full walk with the same answers. Other getEntities callers (not selectors) never see a SingleTypeTest.
 *
 * -Dbons_and_furious.selectorSingleType=false switches it off at run time.
 *
 * Shadow mode (-Dbons_and_furious.selectorSingleType.shadow=true, for the in-game check): before every wrapped scan, the
 * entities the index hands a SingleTypeTest walk (ServerLevel.getEntities with an accept-all predicate) are compared, by
 * identity and order, with vanilla's answer: every entity of the level's getAllEntities (byId order) that the EntityType's
 * own tryCast lets through. The selector itself then runs once, as with the switch on. SHADOW_CHECKS / SHADOW_MISMATCHES
 * count the comparisons; the first 20 mismatches are logged as WARN.
 *
 * Ported to 1.21.1: unchanged. EntityType.tryCast (getType() == this), getBaseClass, the whole-level ServerLevel.getEntities
 * call in addEntities, LevelEntityGetterAdapter.get and EntityLookup.getEntities are the same code; "@n" with one type
 * (new in 1.21) takes the same whole-level call and is covered the same way (its sort and limit run after the scan).
 */
public final class SingleTypeTest implements EntityTypeTest<Entity, Entity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.selectorSingleType", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.selectorSingleType.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;
    final EntityType<?> type;

    SingleTypeTest(EntityType<?> type) {
        this.type = type;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Entity tryCast(Entity entity) {
        return (Entity) ((EntityTypeTest) this.type).tryCast(entity);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Class<? extends Entity> getBaseClass() {
        return ((EntityTypeTest) this.type).getBaseClass();
    }

    /** EntitySelector.addEntities' whole-level getEntities call: an EntityType test becomes a SingleTypeTest. */
    public static void scan(ServerLevel level, EntityTypeTest<?, ?> test, Predicate<?> predicate, List<?> out, int limit, Operation<Void> original) {
        if (enabled && test instanceof EntityType<?> type) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_selector_single_type applies (entity selectors with one entity type visit only that type's entities){}",
                        SHADOW ? " - shadow verification on" : "");
            }
            if (SHADOW) shadowCheck(level, type);
            SingleTypeTest single = last;   // immutable: the last one is reused while scans ask for the same type
            if (single == null || single.type != type) last = single = new SingleTypeTest(type);
            original.call(level, single, predicate, out, SelectorPrefilter.boxed(limit));
            return;
        }
        original.call(level, test, predicate, out, SelectorPrefilter.boxed(limit));
    }

    private static volatile SingleTypeTest last;

    /** Shadow mode: the index's walk for this type against vanilla's (byId order, the type's own tryCast). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void shadowCheck(ServerLevel level, EntityType<?> type) {
        List<Entity> ours = new ArrayList<>();
        level.getEntities((EntityTypeTest) new SingleTypeTest(type), e -> true, (List) ours, Integer.MAX_VALUE);
        List<Entity> vanilla = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            Object cast = ((EntityTypeTest) type).tryCast(e);
            if (cast != null) vanilla.add((Entity) cast);
        }
        SHADOW_CHECKS.incrementAndGet();
        boolean same = ours.size() == vanilla.size();
        for (int i = 0; same && i < ours.size(); i++) same = ours.get(i) == vanilla.get(i);
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: vanilla_selector_single_type shadow mismatch for {} in {}: index walk {} entities, vanilla {} (check {})",
                    type, level.dimension().location(), ours.size(), vanilla.size(), SHADOW_CHECKS.get());
        }
    }
}
