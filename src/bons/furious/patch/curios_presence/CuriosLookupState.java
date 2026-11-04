package bons.furious.patch.curios_presence;

/**
 * Bons and Furious switch curios_inventory_lookup_layer (since 1.0.49): the per-entity slot LivingEntityCuriosLookupMixin
 * adds to LivingEntity. It holds an immutable CuriosInventoryLookup.Kept record (the capability dispatcher it was learned
 * for, and Curios' own inventory LazyOptional or null for "the dispatcher answered empty"), or null until the first
 * lookup that can be kept. Only CuriosInventoryLookup reads or writes it.
 */
public interface CuriosLookupState {
    Object bons$curiosLookup();

    void bons$setCuriosLookup(Object kept);
}
