package bons.furious.mixin.goetycataclysm_coo49;

import bons.furious.patch.goetycataclysm_coo49.GcGolems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * goety_cataclysm_want_looting_key_check (Goety Cataclysm 1.20-1.9.2, all rights reserved: no code carried; gameplay fix,
 * off by default; acts on the server only, where LootEvents runs its test). The two Component.getString() calls that
 * read the killing entity's type description in GCEvents.LootEvents return the untranslated key for a translatable
 * description (GcGolems.lootName), so the test for "cataclysm" sees entity.cataclysm.* instead of the translated name.
 * require 1: the second test ("goety_cataclysm") is implied by the first one, so one wrapped call already gives the fix.
 */
@Mixin(targets = "com.Polarice3.goety_cataclysm.common.events.GCEvents", remap = false)
public abstract class GcWantLootingKeyMixin {
    // the bare name: "LootEvents(L...;)V" starts with L and holds a ';', which Mixin reads as an owner-qualified selector
    @WrapOperation(method = "LootEvents",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"),
            require = 1, allow = 2)
    private static String bons$goetycataclysmLootName(Component description, Operation<String> original) {
        return GcGolems.lootName(description, original);
    }
}
