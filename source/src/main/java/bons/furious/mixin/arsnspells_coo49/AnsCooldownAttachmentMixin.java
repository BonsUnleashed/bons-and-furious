package bons.furious.mixin.arsnspells_coo49;

import bons.furious.patch.arsnspells_coo49.AnsCooldowns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * arsnspells_keep_cooldowns_on_clone (Ars 'n' Spells 3.3.5 for NeoForge 1.21.1, GPL-3.0: no code carried; gameplay fix, off
 * by default). AttachmentTypes.lambda$static$1 is the supplier of the "cooldown" attachment type (builder(CooldownData::new)
 * .serialize(CooldownData.CODEC).build(), registered as COOLDOWN); MixinExtras @WrapOperation on its build() call marks the
 * builder copyOnDeath first (AnsCooldowns.copyOnDeath), as the mod's affinity and progression types are, so NeoForge's
 * PlayerEvent.Clone copy carries the cooldowns to the respawned player. On Forge 1.20.1 the same fix was an @Inject in the
 * mod's own ModCapabilityProvider.onPlayerClone, which the 1.21.1 build no longer has.
 */
@Mixin(targets = "com.otectus.arsnspells.data.AttachmentTypes", remap = false)
public abstract class AnsCooldownAttachmentMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(method = "lambda$static$1()Lnet/neoforged/neoforge/attachment/AttachmentType;",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/attachment/AttachmentType$Builder;build()Lnet/neoforged/neoforge/attachment/AttachmentType;"),
            require = 1, allow = 1)
    private static AttachmentType bons$arsnspellsCooldownsCopyOnDeath(AttachmentType.Builder builder, Operation<AttachmentType> original) {
        return original.call(AnsCooldowns.copyOnDeath(builder));
    }
}
