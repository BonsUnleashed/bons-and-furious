package bons.furious.mixin.legendaryspellbooks_coo49;

import bons.furious.patch.legendaryspellbooks_coo49.LsAnnihilationHolder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * legendary_spellbooks_annihilation_bonus_memo (vanilla LivingEntity, Minecraft 1.21.1, both sides; applied only while
 * Legendary Spellbooks is installed): one field holding the entity's Annihilation Bonus memo (LsAnnihilationMemo), null
 * until its first full run. Nothing of the entity's behaviour changes.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class LsLivingMemoMixin implements LsAnnihilationHolder {
    @Unique
    private Object bons$legendaryspellbooksCoo49Memo;

    @Override
    public Object bons$legendaryspellbooksCoo49Memo() {
        return this.bons$legendaryspellbooksCoo49Memo;
    }

    @Override
    public void bons$legendaryspellbooksCoo49Memo(Object memo) {
        this.bons$legendaryspellbooksCoo49Memo = memo;
    }
}
