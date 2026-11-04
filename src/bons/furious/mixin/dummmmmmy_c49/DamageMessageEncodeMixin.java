package bons.furious.mixin.dummmmmmy_c49;

import bons.furious.patch.dummmmmmy_c49.DamageNumberFixes;
import net.mehvahdjukaar.dummmmmmy.Dummmmmmy;
import net.mehvahdjukaar.dummmmmmy.network.ClientBoundDamageNumberMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * dummmmmmy_inline_damage_type_fallback (MmmMmmMmmMmm 1.20-2.0.12; server side): a cancellable HEAD inject on
 * ClientBoundDamageNumberMessage.encodeDamage answers the mod's TRUE_DAMAGE id for a source whose type holder has no registry
 * key (where the mod throws an AssertionError); every other source runs the mod's own code.
 */
@Mixin(value = ClientBoundDamageNumberMessage.class, remap = false)
public abstract class DamageMessageEncodeMixin {
    @Inject(method = "encodeDamage(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$dummyC49Encode(Level level, DamageSource source, CallbackInfoReturnable<ResourceLocation> cir) {
        if (DamageNumberFixes.inlineFallback && DamageNumberFixes.inlineType(source)) cir.setReturnValue(Dummmmmmy.TRUE_DAMAGE);
    }
}
