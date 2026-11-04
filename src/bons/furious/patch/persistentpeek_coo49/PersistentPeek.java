package bons.furious.patch.persistentpeek_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bons and Furious switches industrialforegoing_stasis_filter_peek (Industrial Foregoing 3.5.22, MIT; both sides: the
 * filter runs on every living tick) and mobgrindingutils_chicken_tag_peek (Mob Grinding Utils 1.1.0, All Rights Reserved;
 * server fuse event and client swell renderer), exact variants. Ours; no target code is carried. Idea: Collections Of
 * Optimizations (industrialforegoing.skipStasisTagChurn, mobgrindingutils.leanChickenTag), idea text only.
 *
 * Forge's getPersistentData() creates an entity's ForgeData compound on the first ask, even to read one key, and the
 * entity then keeps it and saves it as ForgeData:{}. Industrial Foregoing's Stasis Chamber filter asks every mob on every
 * living tick contains("StasisChamberTime"); Mob Grinding Utils asks every chicken on every living event (server) and every
 * rendered chicken (client) contains("shouldExplode"). Those first calls only read the answer (the filter returns it; the
 * fuse and the renderer return when it is false, and every later read or write sits behind that test, which an absent
 * compound can never pass).
 *
 * stasis() / chicken() / swell() answer the call: the entity's own compound when it exists (the object getPersistentData()
 * returns); otherwise the filter and the renderer get null, which the redirected contains(key) right after it (has())
 * answers false, and the fuse, which keeps the answer in a local, one shared empty compound that nothing writes: what
 * getPersistentData()'s new empty compound would have answered.
 * Per entity the class state is looked at once (FORGE_DATA): a class that declares its own getPersistentData() (none in
 * the pack; a mod could add one) is always asked through it. Switch off: getPersistentData() as shipped. The flags are
 * read once (constants, so the steady state is one byte and one field read: as fast as stock's own null check).
 *
 * Exact variant: an entity that nothing writes ForgeData to keeps no empty ForgeData compound (in memory, in its saved NBT
 * and in /data output); whatever later calls getPersistentData() gets it then, empty as before.
 *
 * Flags: -Dbons_and_furious.industrialforegoingStasisFilterPeek=false, -Dbons_and_furious.mobgrindingutilsChickenTagPeek=false.
 */
public final class PersistentPeek {
    public static final boolean STASIS_ENABLED = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.industrialforegoingStasisFilterPeek", "true"));
    public static final boolean CHICKEN_ENABLED = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mobgrindingutilsChickenTagPeek", "true"));
    /** The shared read-only empty compound (harness: must stay empty). */
    public static final CompoundTag EMPTY = new CompoundTag();
    public static final byte FORGE = 1, OWN = 2;

    /** True for entity classes on whose way to Entity no class declares getPersistentData() (Forge's code answers). */
    static final ClassValue<Boolean> FORGE_DATA = new ClassValue<>() {
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

    private PersistentPeek() {
    }

    static byte classState(Entity entity) {
        return FORGE_DATA.get(entity.getClass()) ? FORGE : OWN;
    }

    /** The redirected getPersistentData() of Industrial Foregoing's Stasis Chamber filter: the compound to ask, or null when
     *  an entity of a plain class has none (the redirected contains(key) right after it, has(), answers false then, as the
     *  new empty compound would). */
    public static CompoundTag stasis(LivingEntity entity) {
        if (!STASIS_ENABLED) return entity.getPersistentData();
        PpStasisDuck d = (PpStasisDuck) entity;
        byte s = d.bons$persistentpeekCoo49StasisClass();
        if (s != FORGE) {
            if (s == 0) d.bons$persistentpeekCoo49StasisSetClass(s = classState(entity));
            if (s != FORGE) return entity.getPersistentData();
        }
        return d.bons$persistentpeekCoo49StasisData();
    }

    /** The redirected first getPersistentData() of Mob Grinding Utils' chicken fuse event (the answer is kept in a local
     *  there, so an absent compound is answered with the shared empty one). */
    public static CompoundTag chicken(LivingEntity entity) {
        if (!CHICKEN_ENABLED) return entity.getPersistentData();
        PpChickenDuck d = (PpChickenDuck) entity;
        byte s = d.bons$persistentpeekCoo49ChickenClass();
        if (s != FORGE) {
            if (s == 0) d.bons$persistentpeekCoo49ChickenSetClass(s = classState(entity));
            if (s != FORGE) return entity.getPersistentData();
        }
        CompoundTag tag = d.bons$persistentpeekCoo49ChickenData();
        return tag != null ? tag : EMPTY;
    }

    /** The redirected first getPersistentData() of Mob Grinding Utils' swell renderer: as stasis(), null when absent. */
    public static CompoundTag swell(LivingEntity entity) {
        if (!CHICKEN_ENABLED) return entity.getPersistentData();
        PpChickenDuck d = (PpChickenDuck) entity;
        byte s = d.bons$persistentpeekCoo49ChickenClass();
        if (s != FORGE) {
            if (s == 0) d.bons$persistentpeekCoo49ChickenSetClass(s = classState(entity));
            if (s != FORGE) return entity.getPersistentData();
        }
        return d.bons$persistentpeekCoo49ChickenData();
    }

    /** The redirected contains(key) asked of stasis() / swell()'s answer: false for an absent compound. */
    public static boolean has(CompoundTag tag, String key) {
        return tag != null && tag.m_128441_(key);
    }
}
