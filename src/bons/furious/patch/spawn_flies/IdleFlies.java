package bons.furious.patch.spawn_flies;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/**
 * Bons and Furious switch spawn_flyless_particle_check (Spawn 4.0.7 for Minecraft 1.20.1, All Rights Reserved; server
 * side, including the integrated server). SRG member names. No Spawn code is carried: this class states what Spawn's
 * method does for an entity without flies and does that with the calls in a different order.
 *
 * Spawn's ForgeEvents.livingTick runs FlyData.spawnRandomFlyParticles(entity, flies) for every living entity with its fly
 * capability, every tick; almost every entity has 0 flies. With amount = 0 the method's only effects are: on a server
 * level, unless entity.isInWaterOrBubble() or entity.isOnFire(), on ticks where tickCount % 100 == 0, one
 * entity.getRandom().nextFloat() (the sound roll against a chance of 0, which never plays a sound; the particle loop runs
 * 0 times). isInWaterOrBubble is wasTouchingWater || isInBubbleColumn(), a block-state read at the entity's position
 * (Valkyrien Skies wraps isInBubbleColumn with a @WrapMethod); together the two checks were 0.6-1.7% of the integrated
 * server thread (JFR, Bons and Furious 1.0.26).
 *
 * The fast path (amount == 0) tests the level and tickCount % 100 first and makes the same two checks and the same random
 * draw only on those ticks: the same draws from the same random source, nothing else either way. Exact because the two
 * checks have no effects of their own once the entity's Forge persistent-data tag exists:
 *  - isInWaterOrBubble/isInBubbleColumn read a field and the block state of the entity's own (loaded, ticking) chunk;
 *    Valkyrien Skies' wrap reads its sealed-area field and config; Radium's getBlockState/getChunk overwrites read too;
 *  - isOnFire reads fireImmune, the remaining fire ticks and, on a client level only, a synched flag. Three mods hook
 *    fireImmune: Quark (its pickarang vehicle check) and Domestication Innovation (tamed pets: Citadel's synched tag and its
 *    config) only read; Create reads entity.getPersistentData(), and Forge's getPersistentData() CREATES the tag when it
 *    is still null, which saveWithoutId then writes as ForgeData. So the fast path runs only when that tag already exists
 *    (persistentData != null, then getPersistentData is a plain read); before that Spawn's method runs every tick and
 *    creates it at the same tick as without the switch.
 * The block read goes through Radium's getChunk, which can add an UNKNOWN chunk ticket (first cache-missing access of the
 * chunk in a tick); baseTick's water update reads the same column in the same tick (no installed mod cancels the tick
 * event), so the end-of-tick ticket set is the same. Assumption: a Mob or Player class's tick reaches Entity.baseTick (no
 * such class in the pack replaces it without calling super); one whose tick never read its chunk column would let the
 * switch drop that tick's UNKNOWN ticket, which only affects the unload timing of a chunk held at level 33 by nothing else.
 * An entity whose class overrides isInWaterOrBubble, isOnFire or fireImmune or any of Entity's water-update methods
 * (OWN_CHECKS; isInBubbleColumn is private), any call with flies, and a Forge without that field run Spawn's method
 * unchanged. notes/spawn_flies.md lists the mixins on every skipped call and the chunk-ticket census.
 *
 * -Dbons_and_furious.spawnFlylessParticleCheck=false runs Spawn's method for every entity.
 * -Dbons_and_furious.spawnFlylessParticleCheck.shadow=true (verification runs only) runs Spawn's method for every call the
 * fast path would take and checks its premise: on ticks where the fast path skips the checks, no field of the entity may
 * change during Spawn's method (a field the checks assign lazily, such as Forge's persistent-data tag), and repeated
 * checks must answer the same. SHADOW_CHECKS counts the calls, SHADOW_MISMATCHES the violations (first 20 logged). It sees
 * field assignments on the entity, not changes inside objects the entity points to or elsewhere.
 */
public final class IdleFlies {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.spawnFlylessParticleCheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.spawnFlylessParticleCheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;
    /** Forge's Entity.persistentData field (a Forge-added name, not SRG); null when it is not there: no fast path. */
    private static final VarHandle PERSISTENT_DATA = persistentDataHandle();

    /**
     * Entity's water update, which baseTick runs later in the same tick and which reads the entity's own chunk column again
     * (updateInWaterStateAndDoFluidPushing m_20073_, updateInWaterStateAndDoWaterCurrentPushing m_20074_,
     * updateFluidHeightAndDoFluidPushing m_204031_ and Forge's two updateFluidHeightAndDoFluidPushing overloads).
     */
    private static final Set<String> WATER_UPDATE = Set.of("m_20073_", "m_20074_", "m_204031_", "updateFluidHeightAndDoFluidPushing");

    /**
     * True for entity classes that run Spawn's method: their own isInWaterOrBubble, isOnFire or fireImmune
     * (isInBubbleColumn is private to Entity, so no subclass can replace it), or their own version of any WATER_UPDATE
     * method, with or without a super call. The skipped block read can add Radium's per-tick chunk ticket for the entity's
     * chunk; the fast path relies on baseTick's water update reading the same column in the same tick, so a class that
     * replaces that update keeps Spawn's read. A class whose methods cannot be listed runs Spawn's method too.
     */
    private static final ClassValue<Boolean> OWN_CHECKS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                if (type.getMethod("m_20072_").getDeclaringClass() != Entity.class
                        || type.getMethod("m_6060_").getDeclaringClass() != Entity.class
                        || type.getMethod("m_5825_").getDeclaringClass() != Entity.class) return true;
                for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        if (WATER_UPDATE.contains(m.getName()) && !Modifier.isStatic(m.getModifiers())) return true;
                    }
                }
                return false;
            } catch (NoSuchMethodException | SecurityException | LinkageError e) {
                return true;
            }
        }
    };

    private IdleFlies() {
    }

    private static VarHandle persistentDataHandle() {
        try {
            return MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup()).findVarHandle(Entity.class, "persistentData", CompoundTag.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Bons and Furious: spawn_flyless_particle_check stands down (no Forge persistent-data field on Entity: {})", e.toString());
            return null;
        }
    }

    /** FlyData.spawnRandomFlyParticles(LivingEntity, int), wrapped. */
    public static void spawnRandomFlyParticles(LivingEntity entity, int amount, Operation<Void> original) {
        if (amount != 0 || !enabled || PERSISTENT_DATA == null || PERSISTENT_DATA.get((Entity) entity) == null
                || OWN_CHECKS.get(entity.getClass())) {
            original.call(entity, amount);
            return;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: spawn_flyless_particle_check applies (entities without flies skip Spawn's water and fire checks between its 100-tick rolls){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            shadow(entity, original);
            return;
        }
        if (entity.m_9236_() instanceof ServerLevel && entity.f_19797_ % 100 == 0 && !entity.m_20072_() && !entity.m_6060_()) {
            entity.m_217043_().m_188501_();
        }
    }

    /** Every instance field of an entity class (shadow mode only). */
    private static final ClassValue<Field[]> FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            List<Field> out = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && f.trySetAccessible()) out.add(f);
                }
            }
            return out.toArray(new Field[0]);
        }
    };

    private static Object[] snapshot(Entity entity, Field[] fields) {
        Object[] v = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            try {
                v[i] = fields[i].get(entity);
            } catch (IllegalAccessException e) {
                v[i] = e;
            }
        }
        return v;
    }

    private static void shadow(LivingEntity entity, Operation<Void> original) {
        SHADOW_CHECKS.incrementAndGet();
        boolean server = entity.m_9236_() instanceof ServerLevel;
        boolean skippedTick = server && entity.f_19797_ % 100 != 0;
        Field[] fields = skippedTick ? FIELDS.get(entity.getClass()) : null;
        Object[] before = skippedTick ? snapshot(entity, fields) : null;
        original.call(entity, 0);
        if (skippedTick) {
            Object[] after = snapshot(entity, fields);
            for (int i = 0; i < fields.length; i++) {
                Object a = before[i], b = after[i];
                boolean same = a == b || (a != null && fields[i].getType().isPrimitive() && a.equals(b));
                if (!same) {
                    mismatch(entity, "field " + fields[i].getDeclaringClass().getSimpleName() + "." + fields[i].getName()
                            + " changed during the water/fire checks the fast path skips on this tick");
                    break;
                }
            }
        }
        boolean water = server && entity.m_20072_(), fire = server && !water && entity.m_6060_();
        boolean water2 = server && entity.m_20072_(), fire2 = server && !water2 && entity.m_6060_();
        if (water != water2 || fire != fire2) {
            mismatch(entity, "its water/fire checks answered differently on a repeat (" + water + "/" + fire + " then " + water2 + "/" + fire2 + ")");
        }
    }

    private static void mismatch(Entity entity, String what) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) {
            LOGGER.warn("Bons and Furious: spawn_flyless_particle_check shadow mismatch #{}: {}: {}", m, entity.getClass().getName(), what);
        }
    }
}
