package bons.furious.patch.persistentpeek_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Bons and Furious switch mobgrindingutils_chicken_tag_peek (Mob Grinding Utils 1.1.10+mc1.21.1, All Rights Reserved;
 * server fuse event and client swell renderer), exact variant. Ours; no target code is carried. Idea: Collections Of
 * Optimizations (mobgrindingutils.leanChickenTag), idea text only.
 *
 * NeoForge's Entity.getPersistentData() creates an entity's persistent compound on the first ask, even to read one key, and
 * the entity then keeps it and saves it as NeoForgeData:{} (Entity.saveWithoutId writes it whenever it is not null). Mob
 * Grinding Utils asks every chicken on every server tick (ChickenFuseEvent.startChickenFuse, an EntityTickEvent.Post
 * listener) and every rendered chicken (client) contains("shouldExplode"). Those first calls only read the answer (the
 * fuse and the renderer return when it is false, and every later read or write sits behind that test, which an absent
 * compound can never pass).
 *
 * chicken() / swell() answer the call: the entity's own compound when it exists (the object getPersistentData() returns);
 * otherwise the renderer gets null, which the redirected contains(key) right after it (has()) answers false, and the fuse,
 * which keeps the answer in a local, one shared empty compound that nothing writes: what getPersistentData()'s new empty
 * compound would have answered. Per entity the class state is looked at once (ENTITY_DATA): a class that declares its own
 * getPersistentData() (a mod could add one) is always asked through it. Switch off: getPersistentData() as shipped. The
 * flag is read once (a constant, so the steady state is one byte and one field read: as fast as stock's own null check).
 *
 * Exact variant: a chicken that nothing writes persistent data to keeps no empty NeoForgeData compound (in memory, in its
 * saved NBT and in /data output); whatever later calls getPersistentData() gets it then, empty as before.
 *
 * Flag: -Dbons_and_furious.mobgrindingutilsChickenTagPeek=false.
 */
public final class PpChickenPeek {
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mobgrindingutilsChickenTagPeek", "true"));
    /** The shared read-only empty compound (harness: must stay empty). */
    public static final CompoundTag EMPTY = new CompoundTag();
    public static final byte NEOFORGE = 1, OWN = 2;

    /** True for entity classes on whose way to Entity no class declares getPersistentData() (Entity's code answers). */
    static final ClassValue<Boolean> ENTITY_DATA = new ClassValue<>() {
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

    private PpChickenPeek() {
    }

    static byte classState(Entity entity) {
        return ENTITY_DATA.get(entity.getClass()) ? NEOFORGE : OWN;
    }

    /** The redirected first getPersistentData() of Mob Grinding Utils' chicken fuse event (the answer is kept in a local
     *  there, so an absent compound is answered with the shared empty one). */
    public static CompoundTag chicken(Entity entity) {
        if (!ENABLED) return entity.getPersistentData();
        PpChickenDuck d = (PpChickenDuck) entity;
        byte s = d.bons$persistentpeekCoo49ChickenClass();
        if (s != NEOFORGE) {
            if (s == 0) d.bons$persistentpeekCoo49ChickenSetClass(s = classState(entity));
            if (s != NEOFORGE) return entity.getPersistentData();
        }
        CompoundTag tag = d.bons$persistentpeekCoo49ChickenData();
        return tag != null ? tag : EMPTY;
    }

    /** The redirected first getPersistentData() of Mob Grinding Utils' swell renderer: the compound, null when absent. */
    public static CompoundTag swell(Entity entity) {
        if (!ENABLED) return entity.getPersistentData();
        PpChickenDuck d = (PpChickenDuck) entity;
        byte s = d.bons$persistentpeekCoo49ChickenClass();
        if (s != NEOFORGE) {
            if (s == 0) d.bons$persistentpeekCoo49ChickenSetClass(s = classState(entity));
            if (s != NEOFORGE) return entity.getPersistentData();
        }
        return d.bons$persistentpeekCoo49ChickenData();
    }

    /** The redirected contains(key) asked of swell()'s answer: false for an absent compound. */
    public static boolean has(CompoundTag tag, String key) {
        return tag != null && tag.contains(key);
    }
}
