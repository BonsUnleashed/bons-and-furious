package bons.furious.patch.enigmaticdelicacy_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch enigmaticdelicacy_unset_persistent_data_skip (Enigmatic Delicacy 1.0.1, both sides; exact
 * variant). Ours; no Enigmatic Delicacy code is carried. Idea: Collections Of Optimizations
 * (enigmaticdelicacy.skipUnsetPersistentData), idea text only.
 *
 * EnigmaticDelightEventHandler.onTick asks getPersistentData().getInt("Slicing") of every living entity on every tick on
 * both sides; Forge's getPersistentData() creates an empty ForgeData compound the first time, so every living entity keeps
 * (and saves) one. readOnly answers that call with the entity's own compound when it exists, else one shared empty compound
 * (Forge's persistentData field read through a VarHandle, never creating it); the handler only reads "Slicing" from it
 * (and acts on a positive value, which an empty compound never has). The one difference (exact variant): an entity that
 * nothing writes ForgeData to keeps no empty ForgeData compound in memory and in its save. Entity classes with their own
 * getPersistentData() are asked through it. Flag: -Dbons_and_furious.enigmaticdelicacyUnsetPersistentDataSkip=false.
 */
public final class SlicingPeek {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticdelicacyUnsetPersistentDataSkip", "true"));
    public static final AtomicLong PEEKS = new AtomicLong();
    private static final VarHandle PERSISTENT_DATA = handle();
    private static final CompoundTag EMPTY = new CompoundTag();
    private static volatile boolean broken, announced;
    private static Class<?> lastPlain;

    private SlicingPeek() {
    }

    private static VarHandle handle() {
        try {
            return MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup()).findVarHandle(Entity.class, "persistentData", CompoundTag.class);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: enigmaticdelicacy_unset_persistent_data_skip cannot read Forge's persistentData field ({}); the handler asks getPersistentData() as shipped", t.toString());
            return null;
        }
    }

    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> k = type; k != null && k != Entity.class; k = k.getSuperclass()) {
                try {
                    k.getDeclaredMethod("getPersistentData");
                    return Boolean.FALSE;
                } catch (NoSuchMethodException e) {
                    // not declared here
                } catch (Throwable t) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
    };

    /** The wrapped getPersistentData() call. */
    public static CompoundTag readOnly(LivingEntity e, Operation<CompoundTag> original) {
        if (enabled && !broken && PERSISTENT_DATA != null && e != null) {
            Class<?> c = e.getClass();
            if (c == lastPlain || plain(c)) {
                CompoundTag tag = (CompoundTag) PERSISTENT_DATA.get((Entity) e);
                if (tag != null) return tag;
                if (EMPTY.m_128456_()) {
                    PEEKS.incrementAndGet();
                    if (!announced) announce();
                    return EMPTY;
                }
                breakPeek();
            }
        }
        return original.call(e);
    }

    /** True for the shared empty compound (its "Slicing" is 0). */
    public static boolean isShared(CompoundTag tag) {
        return tag == EMPTY;
    }

    private static boolean plain(Class<?> c) {
        if (!PLAIN.get(c)) return false;
        lastPlain = c;
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: enigmaticdelicacy_unset_persistent_data_skip applies (Enigmatic Delicacy's slicing check reads ForgeData without creating it)");
    }

    private static synchronized void breakPeek() {
        if (broken) return;
        broken = true;
        LOGGER.warn("Bons and Furious: enigmaticdelicacy_unset_persistent_data_skip found its shared read-only compound written to ({}); the handler asks getPersistentData() again from now on", EMPTY);
    }
}
