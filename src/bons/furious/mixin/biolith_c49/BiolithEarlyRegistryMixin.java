package bons.furious.mixin.biolith_c49;

import bons.furious.patch.biolith_c49.BiolithLean;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.WorldStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * biolith_early_registry_capture (Biolith 1.0.1-beta.1 with BCLib, both sides): a @ModifyVariable (argsOnly, the
 * LayeredRegistryAccess argument) right after the record's super() call in WorldStem's constructor that returns the
 * argument unchanged and hands it to Biolith first (BiolithLean.early). Priority 1000: applied before BCLib's
 * WorldStem_Mixin (1500), whose @ModifyVariable sits at the same instruction; handlers at one instruction run in the order
 * they were applied, so this one runs first, directly after super() (checked on the transformed class). A @ModifyVariable
 * because Mixin 0.8.5 refuses an @Inject in the middle of a constructor.
 */
@Mixin(value = WorldStem.class, priority = 1000, remap = false)
public abstract class BiolithEarlyRegistryMixin {
    @ModifyVariable(method = "<init>", argsOnly = true,
            at = @At(value = "INVOKE", target = "Ljava/lang/Record;<init>()V", shift = At.Shift.AFTER))
    private LayeredRegistryAccess<RegistryLayer> bons$biolithEarly(LayeredRegistryAccess<RegistryLayer> registries) {
        return BiolithLean.early(registries);
    }
}
