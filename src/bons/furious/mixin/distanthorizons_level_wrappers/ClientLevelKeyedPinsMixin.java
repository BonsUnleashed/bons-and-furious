package bons.furious.mixin.distanthorizons_level_wrappers;

import bons.furious.patch.distanthorizons_level_wrappers.KeyedLevelPins;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * distanthorizons_keyed_level_pins (Distant Horizons 3.3.2), client only: every ClientLevel carries the server-keyed
 * wrapper Distant Horizons' KeyedClientLevelManager_forge cached for it (pairs cache map, wrapper; copy-on-write under
 * KeyedLevelPins' lock), so the wrapper lives exactly as long as its level. Null until DH caches a wrapper for the level.
 * Nothing else of ClientLevel is touched.
 */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class ClientLevelKeyedPinsMixin implements KeyedLevelPins.Holder {
    @Unique
    private volatile Object[] bons$dhKeyedPins;

    @Override
    public Object[] bons$dhKeyedPins() {
        return this.bons$dhKeyedPins;
    }

    @Override
    public void bons$setDhKeyedPins(Object[] pins) {
        this.bons$dhKeyedPins = pins;
    }
}
