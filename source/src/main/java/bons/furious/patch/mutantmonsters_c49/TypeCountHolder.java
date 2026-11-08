package bons.furious.patch.mutantmonsters_c49;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * mutantmonsters_spawn_limit_index (since 1.0.49): what bons.furious.mixin.mutantmonsters_c49.EntityLookupTypeCountMixin adds
 * to EntityLookup: its TypeCount (created on first use) and its byId map. Ours.
 */
public interface TypeCountHolder {
    TypeCount bons$mutantTypeCount(boolean create);

    Int2ObjectMap<?> bons$mutantById();
}
