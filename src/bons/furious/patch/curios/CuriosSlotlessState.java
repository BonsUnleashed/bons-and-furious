package bons.furious.patch.curios;

/**
 * Bons and Furious switch curios_slotless_tick_skip: the per-entity slot LivingEntityCuriosStateMixin adds to
 * LivingEntity. It holds the capability dispatcher of that entity for which a full lookup of Curios' inventory
 * capability came back empty while the entity's type had no Curios slots and its capabilities were valid (null until
 * then). Only CuriosSlotlessTick reads or writes it.
 */
public interface CuriosSlotlessState {
    Object bons$curiosEmptyDispatcher();

    void bons$setCuriosEmptyDispatcher(Object dispatcher);
}
