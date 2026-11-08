package bons.furious.patch.persistentpeek_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Bons and Furious switch industrialforegoing_stasis_filter_peek (Industrial Foregoing, MIT; 1.21.1 tested build
 * 1.21-3.6.39; both sides: the filter runs on every entity tick), exact variant. Ours; no target code is carried. Idea:
 * Collections Of Optimizations (industrialforegoing.skipStasisTagChurn), idea text only.
 *
 * NeoForge's Entity.getPersistentData() creates an entity's NeoForgeData compound on the first ask, even to read one key,
 * and the entity then keeps it and saves it as NeoForgeData:{} (saveWithoutId writes it whenever it exists). Industrial
 * Foregoing's Stasis Chamber filter (ModuleMisc.lambda$generateFeatures$21, an EntityTickEvent.Pre filter) asks every mob
 * on every tick contains("StasisChamberTime"). That call only reads the answer: the filter returns it, and the processor
 * that reads the compound again runs only when the filter passed (the compound exists then).
 *
 * stasis() answers the call: the entity's own compound when it exists (the object getPersistentData() returns); otherwise
 * null, which the redirected contains(key) right after it (has()) answers false: what getPersistentData()'s new empty
 * compound would have answered. Per entity the class state is looked at once (NEO_DATA): a class that declares its own
 * getPersistentData() (none in the tested targets; a mod could add one) is always asked through it. Switch off:
 * getPersistentData() as shipped. The flag is read once (a constant, so the steady state is one byte and one field read: as
 * fast as NeoForge's own null check).
 *
 * Exact variant: a mob that nothing writes NeoForgeData to keeps no empty NeoForgeData compound (in memory, in its saved
 * NBT and in /data output); whatever later calls getPersistentData() gets it then, empty as before.
 *
 * Flag: -Dbons_and_furious.industrialforegoingStasisFilterPeek=false.
 */
public final class StasisPeek {
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.industrialforegoingStasisFilterPeek", "true"));
    public static final byte NEO = 1, OWN = 2;

    /** True for entity classes on whose way to Entity no class declares getPersistentData() (NeoForge's code answers). */
    static final ClassValue<Boolean> NEO_DATA = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> k = type; k != null && k != Entity.class; k = k.getSuperclass()) {
                try {
                    k.getDeclaredMethod("getPersistentData");
                    return Boolean.FALSE;
                } catch (NoSuchMethodException e) {
                    // not declared here: look at the super class
                } catch (Throwable t) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
    };

    private StasisPeek() {
    }

    static byte classState(Entity entity) {
        return NEO_DATA.get(entity.getClass()) ? NEO : OWN;
    }

    /** The redirected getPersistentData() of Industrial Foregoing's Stasis Chamber filter: the compound to ask, or null when
     *  an entity of a plain class has none (the redirected contains(key) right after it, has(), answers false then, as the
     *  new empty compound would). */
    public static CompoundTag stasis(Entity entity) {
        if (!ENABLED) return entity.getPersistentData();
        StasisPeekDuck d = (StasisPeekDuck) entity;
        byte s = d.bons$persistentpeekCoo49StasisClass();
        if (s != NEO) {
            if (s == 0) d.bons$persistentpeekCoo49StasisSetClass(s = classState(entity));
            if (s != NEO) return entity.getPersistentData();
        }
        return d.bons$persistentpeekCoo49StasisData();
    }

    /** The redirected contains(key) asked of stasis()'s answer: false for an absent compound. */
    public static boolean has(CompoundTag tag, String key) {
        return tag != null && tag.contains(key);
    }
}
