package bons.furious.patch.pehkui_c49;

/**
 * pehkui_scale_cache_all_types (Pehkui 3.8.3+1.21 for NeoForge): the dense index our mixin gives every Pehkui ScaleType at
 * construction (ScaleTypeIndexMixin), 0 for the first type constructed, 1 for the next, and so on.
 */
public interface PehkuiTypeIndex {
    int bons$pehkuiC49Index();
}
