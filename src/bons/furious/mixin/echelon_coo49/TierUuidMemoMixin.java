package bons.furious.mixin.echelon_coo49;

import bons.furious.patch.echelon_coo49.TierUuids;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * echelon_tier_uuid_memo (Echelon - Linggango Variant 1.2.6, both sides): the UUID.nameUUIDFromBytes call of
 * ForgeTieredAttributeSubscriber.onItemAttributeModifiers goes through TierUuids (an equal UUID from a bounded table when
 * the same bytes were seen before). A Redirect, so the handler's code is otherwise byte for byte the shipped one (no
 * wrapper on its paths that never reach the call). Our own logic only (MIT target; none of its code is carried).
 */
@Mixin(targets = "elocindev.tierify.forge.ForgeTieredAttributeSubscriber", remap = false)
public abstract class TierUuidMemoMixin {
    @Redirect(method = "onItemAttributeModifiers(Lnet/minecraftforge/event/ItemAttributeModifierEvent;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/UUID;nameUUIDFromBytes([B)Ljava/util/UUID;"), require = 1, allow = 1)
    private static UUID bons$echelon_coo49Uuid(byte[] name) {
        return TierUuids.uuid(name);
    }
}
