package bons.furious.mixin.fusion;

import bons.furious.patch.fusion.ConnectionLookups;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * fusion_connection_lookups, part 1 (Fusion 1.3.14+a, client).
 *
 * Remembers the hash of ConnectingTextureType.QuadPredicatesKey (a private record, the context key of Fusion's
 * PREDICATES_CACHE property, hashed on every property-store get/set while CTM faces are meshed). A record's hashCode is
 * not Fusion's code: javac emits an invokedynamic to java.lang.runtime.ObjectMethods.bootstrap with the record class,
 * "side;orientation;predicate" and the three component getters. This class's static initializer obtains exactly that
 * method handle from the same JDK bootstrap with the same arguments (ConnectionLookups.recordHash, with the record's own
 * private lookup), so the value computed here is the one the original method computes, on any JDK. The first call per
 * key computes it; later calls return the remembered value. The components are immutable, so the hash never changes.
 * The two fields follow java.lang.String's racy single-check idiom: each is written only with its final value, so a
 * thread sees either "not computed yet" (and computes the same number again) or the correct hash. With the runtime
 * flag off the value is computed on every call, as before.
 *
 * An overwrite rather than an injection: callback objects (a wrapped method's Operation, an injection's
 * CallbackInfoReturnable) were allocated on every call here, 14.6 B per lookup, because the JIT did not inline the
 * handlers into the record's hashCode. Fusion is All Rights Reserved: the short new body is entirely our own logic.
 */
@Mixin(targets = "com.supermartijn642.fusion.texture.types.connecting.ConnectingTextureType$QuadPredicatesKey", remap = false)
public abstract class ConnectionKeyHashMixin {
    @Unique
    private static final MethodHandle bons$RECORD_HASH = ConnectionLookups.recordHash(MethodHandles.lookup());

    @Unique
    private int bons$hash;
    @Unique
    private boolean bons$hashIsZero;

    /**
     * @author BonsUnleashed
     * @reason Remember the record hash (immutable components); the value comes from the JDK's own record bootstrap.
     */
    @Overwrite
    public final int hashCode() {
        if (!ConnectionLookups.enabled) return ConnectionLookups.hash(bons$RECORD_HASH, this);
        int h = this.bons$hash;
        if (h == 0 && !this.bons$hashIsZero) {
            h = ConnectionLookups.hash(bons$RECORD_HASH, this);
            if (h == 0) this.bons$hashIsZero = true;
            else this.bons$hash = h;
        }
        return h;
    }
}
