package bons.furious.patch.legendaryspellbooks_coo49;

/**
 * legendary_spellbooks_annihilation_bonus_memo: what LsLivingMemoMixin adds to the vanilla LivingEntity: the entity's memo of
 * its last full Annihilation Bonus run (an LsAnnihilationMemo.Memo, null until the first run). Member names carry the group
 * (shared vanilla class).
 */
public interface LsAnnihilationHolder {
    Object bons$legendaryspellbooksCoo49Memo();

    void bons$legendaryspellbooksCoo49Memo(Object memo);
}
