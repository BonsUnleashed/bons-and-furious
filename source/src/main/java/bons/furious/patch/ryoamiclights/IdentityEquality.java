package bons.furious.patch.ryoamiclights;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether a class compares by identity: its equals(Object) and hashCode() are Object's own. Only then can a HashSet
 * that holds no object of that class never report one of its instances as contained.
 *
 * The answer comes from the JVM's own method resolution (MethodHandles.findVirtual + revealDirect) rather than from
 * Class.getMethod, which would build reflection data for every public method and could load unrelated classes named
 * in their signatures. Any class the public lookup cannot see, or any failure, answers false (the caller then keeps
 * the original locked path). One computation per class, cached in a ClassValue.
 */
public final class IdentityEquality {
    private static final MethodType EQUALS = MethodType.methodType(boolean.class, Object.class);
    private static final MethodType HASH_CODE = MethodType.methodType(int.class);
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> equals = lookup.revealDirect(lookup.findVirtual(type, "equals", EQUALS)).getDeclaringClass();
                Class<?> hashCode = lookup.revealDirect(lookup.findVirtual(type, "hashCode", HASH_CODE)).getDeclaringClass();
                return equals == Object.class && hashCode == Object.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private IdentityEquality() {}

    public static boolean holdsFor(Class<?> type) {
        return PLAIN.get(type);
    }
}
