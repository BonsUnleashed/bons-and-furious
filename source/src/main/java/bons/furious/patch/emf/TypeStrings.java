package bons.furious.patch.emf;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import traben.entity_model_features.utils.EMFEntity;

/**
 * Entity Model Features' type string for a block entity is {@code getType().toString()}: for a BlockEntityType that is
 * Object.toString (class name, '@', hexadecimal identity hash), rebuilt for every rendered block entity every frame
 * and then hashed again as a map key. It is fixed for the lifetime of the type object, so it is built once per type.
 *
 * Only when the block entity's emf$getTypeString is EMF's own BlockEntity method and the type's class keeps Object's
 * toString, hashCode and equals; anything else (entities included) calls EMF's method as before. The returned string
 * is equal to the one EMF would build, and EMF only uses it as a map key. Checks use the JVM's own method resolution
 * (MethodHandles), cached per class; a class the public lookup cannot see falls back to EMF's method.
 */
public final class TypeStrings {
    private static final MethodType STRING = MethodType.methodType(String.class);
    private static final MethodType EQUALS = MethodType.methodType(boolean.class, Object.class);
    private static final MethodType HASH_CODE = MethodType.methodType(int.class);
    private static final ConcurrentHashMap<BlockEntityType<?>, String> STRINGS = new ConcurrentHashMap<>();

    /** The block entity class uses EMF's BlockEntity implementation of emf$getTypeString. */
    private static final ClassValue<Boolean> EMF_BLOCK_ENTITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                return lookup.revealDirect(lookup.findVirtual(type, "emf$getTypeString", STRING)).getDeclaringClass() == BlockEntity.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    /** The class keeps Object's toString, hashCode and equals. */
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                return lookup.revealDirect(lookup.findVirtual(type, "toString", STRING)).getDeclaringClass() == Object.class
                        && lookup.revealDirect(lookup.findVirtual(type, "hashCode", HASH_CODE)).getDeclaringClass() == Object.class
                        && lookup.revealDirect(lookup.findVirtual(type, "equals", EQUALS)).getDeclaringClass() == Object.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private TypeStrings() {}

    public static String of(EMFEntity entity) {
        if (entity instanceof BlockEntity blockEntity && EMF_BLOCK_ENTITY.get(blockEntity.getClass())) {
            BlockEntityType<?> type = blockEntity.getType();
            if (type != null && PLAIN.get(type.getClass())) {
                String known = STRINGS.get(type);
                if (known == null) {
                    String built = type.toString();
                    known = STRINGS.putIfAbsent(type, built);
                    if (known == null) {
                        known = built;
                    }
                }
                return known;
            }
        }
        return entity.emf$getTypeString();
    }
}
