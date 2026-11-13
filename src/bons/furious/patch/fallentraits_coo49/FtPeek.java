package bons.furious.patch.fallentraits_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * Bons and Furious switch fallentraits_unset_persistent_data_skip (Fallen Traits & Relics 1.0.0, LGPL-2.1-or-later; server
 * side: every listed read returns on client levels first). Ours. Idea: Collections Of Optimizations
 * (fallentraits.skipUnsetPersistentData), idea text only.
 *
 * FTEvents.onCloneTick (every living entity, every tick), onCloneDamaged and onCloneDeath ask
 * entity.getPersistentData().getBoolean("fallen_traits_is_clone") and WrathOfFenrir.restoreExpiredTraits (every mob with
 * L2 Hostility's trait capability, every tick) asks getPersistentData().contains("ft.wrath_disabled_traits", 9); Forge's
 * getPersistentData() creates an empty ForgeData compound on the first ask, which the entity then saves. readOnly() answers
 * the entity's compound when it exists and otherwise one shared empty compound that nothing writes: each of those call
 * sites only reads the answer and returns at once when the key is absent (the clone and expiry paths, which write, are only
 * reached when the key is present, i.e. when the compound exists). Per entity the class state is looked at once
 * (FORGE_DATA): a class that declares its own getPersistentData() (none in the pack; a mod could add one) is always asked
 * through it.
 *
 * Exact variant: entities that nothing writes ForgeData to no longer save an empty ForgeData tag.
 *
 * Flag: -Dbons_and_furious.fallentraitsUnsetPersistentDataSkip=false.
 */
public final class FtPeek {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fallentraitsUnsetPersistentDataSkip", "true"));
    /** The shared read-only empty compound (harness: must stay empty). */
    public static final CompoundTag EMPTY = new CompoundTag();
    public static final byte FORGE = 1, OWN = 2;

    /** True for entity classes on whose way to Entity no class declares getPersistentData() (Forge's code answers). */
    static final ClassValue<Boolean> FORGE_DATA = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            // the JVM's resolution of that one method (bons.furious.guard.Declared): Class.getDeclaredMethod resolves the types of every
            // method the class declares, and on a dedicated server Forge logged its red "invalid dist" ERROR for each entity class with a
            // client-typed method (bug sweep 2026-11-12). A class that cannot be asked keeps the original getter.
            return bons.furious.guard.Declared.inherits(type, Entity.class, "getPersistentData", java.lang.invoke.MethodType.methodType(CompoundTag.class));
        }
    };

    private FtPeek() {
    }

    /** The redirected entity.getPersistentData() of the listed read sites. */
    public static CompoundTag readOnly(Entity entity) {
        FtPeekDuck d = (FtPeekDuck) entity;
        byte s = d.bons$fallentraitsCoo49PeekClass();
        if (s == 0) d.bons$fallentraitsCoo49PeekSetClass(s = FORGE_DATA.get(entity.getClass()) ? FORGE : OWN);
        if (s == FORGE && enabled) {
            CompoundTag tag = d.bons$fallentraitsCoo49PersistentData();
            return tag != null ? tag : EMPTY;
        }
        return entity.getPersistentData();
    }
}
