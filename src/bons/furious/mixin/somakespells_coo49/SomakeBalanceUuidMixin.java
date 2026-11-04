package bons.furious.mixin.somakespells_coo49;

import bons.furious.patch.somakespells_coo49.SomakeUuids;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;

/**
 * somakespells_balance_uuid_memo (Somake Spells 1.0.9, All Rights Reserved; both sides; exact variant): MixinExtras
 * @WrapMethod on AttributeBalanceHandler.uuid(String), the helper that turns a modifier key such as "weapon:attack_damage"
 * into UUID.nameUUIDFromBytes(key.getBytes(UTF_8)) for every attribute modifier the balance handler builds. The wrapper
 * answers a key it has seen before with the UUID the original computed for it (an equal value: nameUUIDFromBytes is a pure
 * function of the key's bytes); a new key runs the original. See SomakeUuids. Our own logic only; nothing of the mod is
 * carried.
 */
@Mixin(targets = "com.somakespells.event.AttributeBalanceHandler", remap = false)
public abstract class SomakeBalanceUuidMixin {
    @WrapMethod(method = "uuid(Ljava/lang/String;)Ljava/util/UUID;", remap = false)
    private static UUID bons$somakespellsCoo49Uuid(String key, Operation<UUID> original) {
        return SomakeUuids.uuid(key, original);
    }
}
