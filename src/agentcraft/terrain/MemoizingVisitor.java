package agentcraft.terrain;

import java.util.IdentityHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/** A single NoiseChunk router traversal owns this memo. Never shared across chunks or threads. */
public final class MemoizingVisitor implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    private final IdentityHashMap<DensityFunction,DensityFunction> mapped = new IdentityHashMap<>();
    private static final boolean METRICS = Boolean.getBoolean("ac.terrain.metrics");
    public static final LongAdder LOOKUPS = new LongAdder(), HITS = new LongAdder(), MISSES = new LongAdder();
    public MemoizingVisitor(DensityFunction.Visitor delegate) { this.delegate=delegate; }
    public static DensityFunction.Visitor wrap(DensityFunction.Visitor delegate) {
        return Boolean.parseBoolean(System.getProperty("ac.terrain.enabled","true")) ? new MemoizingVisitor(delegate) : delegate;
    }
    public DensityFunction map(DensityFunctions.HolderHolder source) {
        DensityFunction original=source.f_208636_().m_203334_();
        if (METRICS) LOOKUPS.increment();
        DensityFunction result=mapped.get(original);
        if (result!=null) { if(METRICS)HITS.increment(); return result; }
        // Preserve vanilla post-order: map the child, wrap a direct holder, then apply the visitor.
        DensityFunction child=original.m_207456_(this);
        result=delegate.m_214017_(new DensityFunctions.HolderHolder(new Holder.Direct<>(child)));
        mapped.put(original,result);
        if(METRICS)MISSES.increment();
        return result;
    }
    @Override public DensityFunction m_214017_(DensityFunction f) { return delegate.m_214017_(f); }
    @Override public DensityFunction.NoiseHolder m_213918_(DensityFunction.NoiseHolder noise) { return delegate.m_213918_(noise); }
}
