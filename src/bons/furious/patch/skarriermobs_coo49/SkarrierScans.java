package bons.furious.patch.skarriermobs_coo49;

import bons.furious.mixin.skarriermobs_coo49.EntityBoundDataAccessor;
import bons.furious.mixin.skarriermobs_coo49.EntityResisteelDataAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.function.Predicate;
import net.mcreator.skarriermobs.entity.FloreHeadEntity;
import net.mcreator.skarriermobs.init.SkarrierMobsModItems;
import net.mcreator.skarriermobs.init.SkarrierMobsModMobEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, group skarriermobs_coo49 (Skarrier Mobs 1.1.2, All Rights Reserved; both sides; since 1.0.49): the
 * helper of three switches. No code of Skarrier Mobs is carried: the hooks hand the mod's own calls other arguments or
 * decide whether its own code runs.
 *
 * <p>skarriermobs_region_scan_narrowing (exact, on by default). Skarrier's MCreator procedures search the level for EVERY
 * entity in a box (getEntitiesOfClass(Entity.class, box, e -> true)), sort the whole list by distance and loop over it, and
 * the loop's first statement skips every element that fails a test without any other effect (it is a player, a monster,
 * a flore head, a living entity, an entity type in a tag). For those call sites the search gets that test as its
 * predicate instead of "always true": the same section walk, so the list holds exactly the elements of the original list
 * that pass the test, in the same order (Stream.sorted is stable), and the loop does the same thing for each of them; the
 * elements it skipped are no longer collected and sorted. Tunnel Gore's searches reach 100 to 125 blocks. Bytecode of every
 * site (both Skarrier 1.1.2 and COO's pinned 1.0.6) is checked offline: the list feeds only stream().sorted().toList() and
 * one loop, whose first test is the predicate's. Runtime flag -Dbons_and_furious.skarriermobsRegionScanNarrowing=false.
 *
 * <p>skarriermobs_region_scan_narrowing_all (trade-off, off by default). Four more of those searches (the Carnager's +-5
 * rider search, the Endorah body's three +-10 head searches) start their loop with "skip the element unless its saved data
 * string EndorahBindingTag / CarnagerRidingTag equals this mob's UUID", and reading the saved data creates it: every entity
 * in the box that had none gets an empty ForgeData compound, which is then saved with it. With the switch on the search
 * keeps only the elements that pass that test, read without creating anything, so the loop acts on the same elements in
 * the same order. Difference: the other entities in the box no longer get that empty compound. Runtime flag
 * -Dbons_and_furious.skarriermobsRegionScanNarrowingAll=false.
 *
 * <p>skarriermobs_resisteel_set_lean (trade-off, off by default). ResisteelArmorEquippedEffectProcedure runs for every
 * living entity (every 10th tick in 1.1.2, every tick in 1.0.6): it writes five Resisteel doubles into the entity's
 * persistent data (creating that compound) and, when nothing is worn, removes two effects (a MobEffectEvent.Remove post
 * each). With the switch on, an entity that wears no Resisteel piece, has neither of the two effects, and has no Resisteel
 * key other than a plain 0.0 is left alone. Difference: such entities no longer get those five zero keys (nor, when nothing
 * else wrote one, a ForgeData compound) in their saved data, and listeners no longer see the two removals of effects that
 * are not there; every reader of the keys still reads 0.0. Runtime flag -Dbons_and_furious.skarriermobsResisteelSetLean=false.
 *
 * <p>skarriermobs_target_proximity_scan_lean (trade-off, off by default). Eighteen Skarrier searches (radius 3 to 18,
 * every Skarrier mob, every tick, both sides) only ask whether the mob's own target is in the box: the loop acts on the
 * element that is the target and on nothing else. With the switch on the list is [target] when the target is in this
 * level, not removed, its entity section lies in the range the search walks and its box meets the search box, else empty.
 * Difference: a target whose section is loaded but not accessible (entity-hidden ground at the edge of the loaded area),
 * or a target or mob on a Valkyrien Skies ship, can count as near (or not) where the search would say otherwise. Runtime
 * flag -Dbons_and_furious.skarriermobsTargetProximityScanLean=false.
 */
public final class SkarrierScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    public static volatile boolean narrow = flag("skarriermobsRegionScanNarrowing");
    public static volatile boolean narrowAll = flag("skarriermobsRegionScanNarrowingAll");
    public static volatile boolean resisteel = flag("skarriermobsResisteelSetLean");
    public static volatile boolean target = flag("skarriermobsTargetProximityScanLean");
    private static volatile boolean announcedNarrow, announcedNarrowAll, announcedResisteel, announcedTarget;

    /** The flore head host tag the FloreHead procedures build per element (TagKey.create interns: the same key). */
    public static final TagKey<EntityType<?>> FLORE_HEAD_HOST = TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("skarrier_mobs:flore_head_host"));
    /** The raiders tag the Sorcerer procedure builds per element. */
    public static final TagKey<EntityType<?>> RAIDERS = TagKey.m_203882_(Registries.f_256939_, new ResourceLocation("minecraft:raiders"));

    /** The loop tests the narrowed searches take as their predicate (each pure: a type test or an entity type tag test). */
    public static final Predicate<Entity> PLAYER = e -> e instanceof Player;
    public static final Predicate<Entity> SERVER_PLAYER = e -> e instanceof ServerPlayer;
    public static final Predicate<Entity> MONSTER = e -> e instanceof Monster;
    public static final Predicate<Entity> LIVING = e -> e instanceof LivingEntity;
    public static final Predicate<Entity> FLORE_HEAD = e -> e instanceof FloreHeadEntity;
    public static final Predicate<Entity> FLORE_HOST = e -> e.m_6095_().m_204039_(FLORE_HEAD_HOST);
    public static final Predicate<Entity> RAIDER = e -> e.m_6095_().m_204039_(RAIDERS);

    /** The five persistent-data keys of the Resisteel set procedure. */
    private static final String[] RESISTEEL_KEYS = {"ResisteelHelmet", "ResisteelChestplate", "ResisteelLeggings", "ResisteelBoots", "ResisteelSetCompletion"};

    private SkarrierScans() {
    }

    private static boolean flag(String camel) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
    }

    // ---------------------------------------------------------------------------------------------- narrowing

    /** skarriermobs_region_scan_narrowing: the predicate a narrowed search runs with (the original while switched off). */
    public static Predicate<?> narrow(Predicate<?> original, Predicate<Entity> keep) {
        if (!narrow) return original;
        if (!announcedNarrow) announce(1);
        return keep;
    }

    /**
     * skarriermobs_region_scan_narrowing_all: the predicate of a search whose loop starts with "skip unless
     * element.getPersistentData().getString(key) equals the caller's UUID string": that test, reading the element's saved
     * data without creating it (an element without saved data fails it, as the loop's test fails on the empty compound it
     * would create). The original while switched off, or without a caller.
     */
    public static Predicate<?> narrowBound(Predicate<?> original, Entity self, String key) {
        if (!narrowAll || self == null) return original;
        if (!announcedNarrowAll) announce(4);
        String uuid = self.m_20149_();
        return (Predicate<Entity>) e -> {
            CompoundTag d = ((EntityBoundDataAccessor) e).bons$skarriermobsCoo49BoundData();
            return d != null && d.m_128461_(key).equals(uuid);
        };
    }

    // ---------------------------------------------------------------------------------------------- resisteel

    /**
     * skarriermobs_resisteel_set_lean: true when the procedure's body would only write five zero keys and remove two absent
     * effects for this entity (asked where the body starts: in 1.1.2 after its every-10th-tick test).
     */
    public static boolean resisteelUnused(Entity entity) {
        if (!resisteel || !(entity instanceof LivingEntity living)) return false;
        if (living.m_6844_(EquipmentSlot.HEAD).m_41720_() == (Item) SkarrierMobsModItems.RESISTEEL_HELMET.get()
                || living.m_6844_(EquipmentSlot.CHEST).m_41720_() == (Item) SkarrierMobsModItems.RESISTEEL_CHESTPLATE.get()
                || living.m_6844_(EquipmentSlot.LEGS).m_41720_() == (Item) SkarrierMobsModItems.RESISTEEL_LEGGINGS.get()
                || living.m_6844_(EquipmentSlot.FEET).m_41720_() == (Item) SkarrierMobsModItems.RESISTEEL_BOOTS.get())
            return false;
        if (living.m_21023_((MobEffect) SkarrierMobsModMobEffects.DEFENCE_BOOST.get())
                || living.m_21023_((MobEffect) SkarrierMobsModMobEffects.RESISTEEL_SLOWNESS_EFFECT.get()))
            return false;
        CompoundTag data = ((EntityResisteelDataAccessor) entity).bons$skarriermobsCoo49PersistentData();
        if (data != null) {
            for (String k : RESISTEEL_KEYS) {
                // a key the body would leave unchanged: absent (allowed) or a double +0.0 (the body writes exactly that;
                // DoubleTag.valueOf stores every zero as its cached +0.0); one map lookup per key
                Tag t = data.m_128423_(k);
                if (t != null && !(t instanceof DoubleTag d && Double.doubleToRawLongBits(d.m_7061_()) == 0L)) return false;
            }
        }
        if (!announcedResisteel) announce(2);
        return true;
    }

    // ---------------------------------------------------------------------------------------------- target proximity

    /**
     * skarriermobs_target_proximity_scan_lean: the list a target-only search answers: [target] or empty (see the class
     * javadoc); the original search while switched off.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<?> targetOnly(LevelAccessor world, Class<?> type, AABB box, Predicate pred, Operation<List<?>> original, Entity mob) {
        if (!target) return original.call(world, type, box, pred);
        if (world instanceof Level level) level.m_46473_().m_6174_("getEntities");
        LivingEntity t = mob instanceof Mob m ? m.m_5448_() : null;
        if (!announcedTarget) announce(3);
        if (t == null || t.m_213877_() || t.m_9236_() != world || !type.isInstance(t) || !inWalkRange(t, box) || !t.m_20191_().m_82381_(box)
                || !pred.test(t))
            return List.of();
        return List.of(t);
    }

    /** The entity's section lies in the range vanilla's section walk visits for this box (EntitySectionStorage). */
    public static boolean inWalkRange(Entity e, AABB box) {
        int sx = e.m_20183_().m_123341_() >> 4, sy = e.m_20183_().m_123342_() >> 4, sz = e.m_20183_().m_123343_() >> 4;
        return sx >= (Mth.m_14107_(box.f_82288_ - 2.0) >> 4) && sx <= (Mth.m_14107_(box.f_82291_ + 2.0) >> 4)
                && sy >= (Mth.m_14107_(box.f_82289_ - 4.0) >> 4) && sy <= (Mth.m_14107_(box.f_82292_) >> 4)
                && sz >= (Mth.m_14107_(box.f_82290_ - 2.0) >> 4) && sz <= (Mth.m_14107_(box.f_82293_ + 2.0) >> 4);
    }

    private static void announce(int which) {
        synchronized (SkarrierScans.class) {
            if (which == 1 && !announcedNarrow) {
                announcedNarrow = true;
                LOGGER.info("Bons and Furious: skarriermobs_region_scan_narrowing: Skarrier Mobs' area searches collect only the entities their loops act on");
            } else if (which == 2 && !announcedResisteel) {
                announcedResisteel = true;
                LOGGER.info("Bons and Furious: skarriermobs_resisteel_set_lean: the Resisteel set handler leaves entities that never wore Resisteel alone");
            } else if (which == 3 && !announcedTarget) {
                announcedTarget = true;
                LOGGER.info("Bons and Furious: skarriermobs_target_proximity_scan_lean: Skarrier Mobs' target-near searches test the target alone");
            } else if (which == 4 && !announcedNarrowAll) {
                announcedNarrowAll = true;
                LOGGER.info("Bons and Furious: skarriermobs_region_scan_narrowing_all: the Carnager's rider and the Endorah body's head searches collect only bound entities");
            }
        }
    }
}
