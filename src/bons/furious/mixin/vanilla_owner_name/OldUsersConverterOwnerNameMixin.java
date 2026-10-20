package bons.furious.mixin.vanilla_owner_name;

import bons.furious.patch.vanilla_owner_name.OwnerNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import net.minecraft.server.players.OldUsersConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_owner_name_empty_skip (Minecraft 1.20.1 on Forge 47.4.16, logical server, since 1.0.45): MixinExtras
 * @WrapOperation on the one UUID.fromString call in OldUsersConverter.convertMobOwnerIfNecessary (m_11083_). The empty
 * owner name of every wild animal returns null without the explicit IllegalArgumentException UUID.fromString("") throws
 * and m_11083_ catches into the same null; every other name goes to UUID.fromString. Why it is identical: see OwnerNames.
 * require = 0: a mod that rewrites the method leaves the switch out. Minecraft: no code carried, our own logic only.
 */
@Mixin(value = OldUsersConverter.class, remap = false)
public abstract class OldUsersConverterOwnerNameMixin {
    @WrapOperation(method = "m_11083_", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/util/UUID;fromString(Ljava/lang/String;)Ljava/util/UUID;"))
    private static UUID bons$emptyOwnerName(String name, Operation<UUID> original) {
        return OwnerNames.fromString(name, original);
    }
}
