package bons.furious.mixin.arselemental_coo49;

import alexthw.ars_elemental.client.ClientEvents;
import bons.furious.patch.arselemental_coo49.PouchKeyFix;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * arselemental_curio_pouch_keybind (Ars Elemental 0.6.7.8 / 0.6.8.0, client): at the end of ClientEvents.registerKeyBindings
 * (the mod registers the pouch key there, once) PouchKeyFix registers the mod's own instance handler on the Forge bus.
 */
@Mixin(value = ClientEvents.class, remap = false)
public abstract class CurioPouchKeyMixin {
    @Inject(method = "registerKeyBindings", at = @At("TAIL"))
    private static void bons$registerPouchHandler(RegisterKeyMappingsEvent event, CallbackInfo ci) {
        PouchKeyFix.register();
    }
}
