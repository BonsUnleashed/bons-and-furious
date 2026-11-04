package bons.furious.mixin.tomeofblood_coo49;

import bons.furious.patch.tomeofblood_coo49.TobUuids;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tomeofblood_living_upgrade_uuid_memo (Tome of Blood: Rebirth 0.4.5, LGPL-3.0; both sides; exact variant): MixinExtras
 * @WrapOperation on the UUID.nameUUIDFromBytes call in LivingMageArmorItem.lambda$getAttributeModifiers$0, the per-upgrade
 * step of the chestplate's getAttributeModifiers that builds each trained upgrade's modifier id from its key's bytes. Bytes
 * seen before are answered with the UUID the original computed for them. See TobUuids. Our own logic only.
 */
@Mixin(targets = "com.mystchonky.tomeofblood.common.items.LivingMageArmorItem", remap = false)
public abstract class TobLivingUpgradeUuidMixin {
    @WrapOperation(method = "lambda$getAttributeModifiers$0(Lwayoftime/bloodmagic/core/living/LivingStats;Lcom/google/common/collect/Multimap;Lwayoftime/bloodmagic/core/living/LivingUpgrade;Ljava/lang/Double;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/UUID;nameUUIDFromBytes([B)Ljava/util/UUID;"),
            require = 1, allow = 1, remap = false)
    private static UUID bons$tomeofbloodCoo49UpgradeUuid(byte[] bytes, Operation<UUID> original) {
        return TobUuids.uuid(bytes, original);
    }
}
