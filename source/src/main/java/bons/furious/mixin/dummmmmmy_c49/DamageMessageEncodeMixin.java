package bons.furious.mixin.dummmmmmy_c49;

import bons.furious.patch.dummmmmmy_c49.DamageNumberFixes;
import net.mehvahdjukaar.dummmmmmy.Dummmmmmy;
import net.mehvahdjukaar.dummmmmmy.network.ClientBoundDamageNumberMessage;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * dummmmmmy_inline_damage_type_fallback (MmmMmmMmmMmm; 1.21.1 tested build: MmmMmmMmmMmm 1.21-2.1.2 for NeoForge; server
 * side): a cancellable HEAD inject on ClientBoundDamageNumberMessage.encodeDamage answers the mod's TRUE_DAMAGE holder for a
 * source whose damage type has no id in the level's damage type registry (the message could not be written: see
 * DamageNumberFixes); every other source runs the mod's own code.
 *
 * Ported to 1.21.1: encodeDamage(DamageSource, Level) returns the source's type holder itself (TRUE_DAMAGE's holder for a
 * null source), and the record writes it with DamageType.STREAM_CODEC, a registry-id codec.
 */
@Mixin(value = ClientBoundDamageNumberMessage.class, remap = false)
public abstract class DamageMessageEncodeMixin {
    @Inject(method = "encodeDamage(Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$dummyC49Encode(DamageSource source, Level level, CallbackInfoReturnable<Holder<DamageType>> cir) {
        if (DamageNumberFixes.inlineFallback && DamageNumberFixes.unencodable(source, level)) cir.setReturnValue(Dummmmmmy.TRUE_DAMAGE.getHolder(level));
    }
}
