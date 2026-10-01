package bons.pure.terrain;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * A density function that remembers, per thread, its last (x, z) and value. It only ever wraps a subtree that
 * {@link DensityAudit} found to read x and z alone and to contain only known pure types, so the value at the same x and z
 * is the same at every y: returning the remembered value is exact. Used by distanthorizons_rough_surface_xz_cache.
 */
public final class XzCache implements DensityFunction {
    private final DensityFunction wrapped;
    private final ThreadLocal<Slot> last = ThreadLocal.withInitial(Slot::new);

    private static final class Slot {
        boolean valid;
        int x, z;
        double value;
    }

    public XzCache(DensityFunction wrapped) {
        this.wrapped = wrapped;
    }

    public DensityFunction wrapped() {
        return wrapped;
    }

    @Override
    public double m_207386_(DensityFunction.FunctionContext context) {
        int x = context.m_207115_(), z = context.m_207113_();
        Slot s = last.get();
        if (s.valid && s.x == x && s.z == z) return s.value;
        double v = wrapped.m_207386_(context);
        s.x = x;
        s.z = z;
        s.value = v;
        s.valid = true;
        return v;
    }

    @Override
    public void m_207362_(double[] values, DensityFunction.ContextProvider provider) {
        provider.m_207207_(values, this);
    }

    @Override
    public DensityFunction m_207456_(DensityFunction.Visitor visitor) {
        return visitor.m_214017_(new XzCache(wrapped.m_207456_(visitor)));
    }

    @Override
    public double m_207402_() {
        return wrapped.m_207402_();
    }

    @Override
    public double m_207401_() {
        return wrapped.m_207401_();
    }

    /** Never serialised (it only lives in Distant Horizons' rough-surface parameters); the wrapped function's codec. */
    @Override
    @SuppressWarnings("unchecked")
    public KeyDispatchDataCodec<? extends DensityFunction> m_214023_() {
        return wrapped.m_214023_();
    }
}
