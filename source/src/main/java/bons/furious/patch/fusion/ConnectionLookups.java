package bons.furious.patch.fusion;

import com.mojang.logging.LogUtils;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.lang.runtime.ObjectMethods;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

/**
 * Bons and Furious switch fusion_connection_lookups (Fusion 1.3.14+a, client).
 *
 * Two lookups on the connected-texture path that Fusion answers with general-purpose machinery:
 *
 * 1. ConnectingTextureType.QuadPredicatesKey is the record Fusion uses as the context key of its PREDICATES_CACHE
 *    property. Every PropertyStore get/set with it (two to three per CTM face while chunks are meshed) runs the
 *    record's generated hashCode: the side's and orientation's hashes plus the predicate's, and a block predicate's
 *    hash walks its whole block set with a virtual hashCode call per block. The key's components are final and
 *    immutable (Direction and TextureOrientation are enums; every Fusion ConnectionPredicate keeps only final fields
 *    and immutable sets), so its hash never changes; ConnectionKeyHashMixin remembers the first result per key with the
 *    racy single-check idiom of java.lang.String (each field is only ever written with its final value, so a reader
 *    sees either "not yet" and recomputes, or the correct hash). The value comes from recordHash: the JDK's record
 *    bootstrap called with the same arguments as the record's own invokedynamic, so it is the original value.
 *
 * 2. MatchBlockConnectionPredicate tests {@code blocks.contains(neighbour.getBlock())} on a Set.copyOf set, which
 *    calls the neighbour block's virtual equals (Set12) or its virtual hashCode plus floorMod and equals (SetN).
 *    For blocks, equality is identity: no Block class in the pack overrides equals or hashCode (5,165 Block subclasses
 *    in every installed jar checked statically), and identityView re-checks that at runtime for every registered block
 *    and every block in the set before it builds an identity view: the set's blocks in a Block[] scanned with ==.
 *    Membership in the view is then exactly Set.contains; a null argument still goes to the original set (which
 *    throws, as before). Sets larger than ARRAY_MAX keep Fusion's set: there the hashed probe beats a scan (measured:
 *    a fastutil ReferenceOpenHashSet was 13% slower than the original SetN for 9 blocks, a scan of 9-16 was faster).
 */
public final class ConnectionLookups {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.fusionConnectionLookups=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fusionConnectionLookups", "true"));
    static final int ARRAY_MAX = 16;
    private static final ClassValue<Boolean> PLAIN_IDENTITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("equals", Object.class).getDeclaringClass() == Object.class
                        && type.getMethod("hashCode").getDeclaringClass() == Object.class;
            } catch (Throwable e) {
                // 1.0.34: was NoSuchMethodException only. getMethod resolves the types of every public method of the class,
                // so a block class whose method signature names a missing class (an optional dependency) throws
                // NoClassDefFoundError: cannot tell, so not plain (Fusion's set is kept, as for an overriding class)
                return false;
            }
        }
    };
    /** 0 = not checked yet, 1 = every registered block keeps Object's equals/hashCode, 2 = some block does not. */
    private static volatile int registryPlain;

    private ConnectionLookups() {
    }

    /**
     * An identity view of a Fusion block set (its blocks as a Block[], up to ARRAY_MAX of them), or null when the set is
     * larger or identity would not be the same as the set's equals-based membership. Called once per predicate (at
     * resource load), never on the hot path.
     */
    public static Block[] identityView(Set<?> blocks) {
        try {
            if (blocks == null || blocks.size() > ARRAY_MAX || !registryPlain()) return null;
            for (Object o : blocks) {
                if (!(o instanceof Block) || !PLAIN_IDENTITY.get(o.getClass())) return null;
            }
            return blocks.toArray(new Block[0]);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: fusion_connection_lookups keeps Fusion's block set ({})", t.toString());
            return null;
        }
    }

    private static boolean registryPlain() {
        int state = registryPlain;
        if (state == 0) {
            state = 1;
            Class<?> offender = null;
            for (Block b : BuiltInRegistries.BLOCK) {
                if (!PLAIN_IDENTITY.get(b.getClass())) {
                    offender = b.getClass();
                    state = 2;
                    break;
                }
            }
            registryPlain = state;
            if (offender != null)
                LOGGER.warn("Bons and Furious: fusion_connection_lookups keeps Fusion's block sets: {} overrides equals or hashCode, or its methods "
                        + "cannot be listed", offender.getName());   // 1.0.34: the second case since PLAIN_IDENTITY catches every error
            else
                LOGGER.info("Bons and Furious: fusion_connection_lookups compares Fusion's connection blocks by identity");
        }
        return state == 1;
    }

    /**
     * The JDK's generated hashCode of the lookup's record class, typed (Object)int: java.lang.runtime.ObjectMethods.bootstrap
     * called exactly as javac's invokedynamic in a record's hashCode calls it (the record class, the component names
     * joined with ';' in declaration order, a getter for each component field), with the record's own full-privilege
     * lookup. Called once, from the record's static initializer.
     */
    public static MethodHandle recordHash(MethodHandles.Lookup lookup) {
        try {
            Class<?> type = lookup.lookupClass();
            RecordComponent[] components = type.getRecordComponents();
            MethodHandle[] getters = new MethodHandle[components.length];
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < components.length; i++) {
                getters[i] = lookup.findGetter(type, components[i].getName(), components[i].getType());
                if (i > 0) names.append(';');
                names.append(components[i].getName());
            }
            CallSite site = (CallSite) ObjectMethods.bootstrap(lookup, "hashCode", MethodType.methodType(int.class, type), type, names.toString(), getters);
            return site.getTarget().asType(MethodType.methodType(int.class, Object.class));
        } catch (Throwable t) {
            throw new IllegalStateException("Bons and Furious: fusion_connection_lookups could not obtain the record hash of " + lookup.lookupClass(), t);
        }
    }

    /** {@code (int) recordHash.invokeExact(key)}; exceptions of the components' hashCode propagate unchanged. */
    public static int hash(MethodHandle recordHash, Object key) {
        try {
            return (int) recordHash.invokeExact(key);
        } catch (Throwable t) {
            throw sneaky(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T {
        throw (T) t;
    }

    /** {@code set.contains(block)} for a Fusion block set, answered from its identity view when it has one. */
    public static boolean contains(Set<?> set, Block[] view, Object block) {
        if (!enabled || view == null || block == null) return set.contains(block);
        for (Block b : view) {
            if (b == block) return true;
        }
        return false;
    }
}
