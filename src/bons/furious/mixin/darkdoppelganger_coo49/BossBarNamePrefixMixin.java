package bons.furious.mixin.darkdoppelganger_coo49;

import bons.furious.patch.darkdoppelganger_coo49.DoppelNamePrefix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * darkdoppelganger_boss_bar_prefix_check (Dark Doppelganger 9.9.0, All Rights Reserved; client): MixinExtras
 * @WrapOperation on the Component.getString() call in ClientEvents.onCustomizeBossBar, whose result is only asked
 * startsWith("Dark Doppelganger"): for a plain vanilla component tree DoppelNamePrefix answers that question by visiting
 * the name only as far as needed and returns a string with the same answer; otherwise the original call. Our own logic
 * only.
 */
@Mixin(targets = "net.bandit.darkdoppelganger.event.ClientEvents", remap = false)
public abstract class BossBarNamePrefixMixin {
    @WrapOperation(method = "onCustomizeBossBar(Lnet/minecraftforge/client/event/CustomizeGuiOverlayEvent$BossEventProgress;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"),
            require = 1, allow = 1)
    private static String bons$darkdoppelgangerCoo49NamePrefix(Component name, Operation<String> original) {
        return DoppelNamePrefix.answer(name, original);
    }
}
