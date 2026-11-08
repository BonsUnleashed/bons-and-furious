package bons.furious.patch.legendaryspellbooks_coo49;

/**
 * legendary_spellbooks_annihilation_bonus_memo: what LsAttributeVersionMixin adds to the vanilla AttributeInstance. The
 * version counts every setDirty call (every base value, modifier, replaceFrom and load change goes through it); dirty is
 * the instance's own "cached value is stale" flag. Member names carry the group (shared vanilla class).
 */
public interface LsAttributeVersionDuck {
    int bons$legendaryspellbooksCoo49Version();

    boolean bons$legendaryspellbooksCoo49Dirty();
}
