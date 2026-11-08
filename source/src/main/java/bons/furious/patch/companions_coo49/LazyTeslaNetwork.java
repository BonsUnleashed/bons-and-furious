package bons.furious.patch.companions_coo49;

/**
 * companions_tesla_lazy_distances: implemented by Companions' TeslaNetwork (TeslaNetworkLazyMixin). flushDistances()
 * runs the network's own recalculateDistances() when a mutation is pending, else nothing.
 */
public interface LazyTeslaNetwork {
    void bons$flushDistances();
}
