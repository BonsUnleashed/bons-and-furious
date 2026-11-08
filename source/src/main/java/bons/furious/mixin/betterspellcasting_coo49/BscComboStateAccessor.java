package bons.furious.mixin.betterspellcasting_coo49;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * better_spellcasting_queued_spell_expiry (Better Spellcasting, LGPL-2.1-or-later; 1.21.1 tested build: Better
 * Spellcasting 1.21.1-1.2.0; server): read access to the tick of the swing a combo state was written for (the private
 * record AttackSpellHandler$ComboState, field lastSwingTick), used by BscQueuedSpellExpiryMixin through BscSpells. Nothing
 * of the mod is carried.
 */
@Mixin(targets = "net.kayn.better_spellcasting.event.AttackSpellHandler$ComboState", remap = false)
public interface BscComboStateAccessor {
    @Accessor(value = "lastSwingTick", remap = false)
    long bons$betterspellcastingCoo49LastSwingTick();
}
