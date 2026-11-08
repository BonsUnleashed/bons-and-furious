package bons.furious.mixin.darkdoppelganger_coo49;

import bons.furious.patch.darkdoppelganger_coo49.DoppelNamePrefix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * darkdoppelganger_boss_bar_prefix_check (Dark Doppelganger, All Rights Reserved; 1.21.1 tested build: Dark Doppelganger
 * 4.0.0 for NeoForge 1.21.1, darkdoppelganger-4.0.0-1.21.1.jar; client): MixinExtras @WrapOperation on the
 * Component.getString() call in DarkDoppelBossBar.renderBossBar, whose result is only asked startsWith("Dark Doppelganger"):
 * for a plain vanilla component tree DoppelNamePrefix answers that question by visiting the name only as far as needed and
 * returns a string with the same answer; otherwise the original call. Our own logic only.
 *
 * Ported to 1.21.1: the check moved from ClientEvents.onCustomizeBossBar (9.9.0) to DarkDoppelBossBar.renderBossBar, which
 * GuiOverlayHandler.renderBossBar calls for every CustomizeGuiOverlayEvent.BossEventProgress (every boss bar, every frame);
 * it still makes exactly one getString() call and asks only startsWith of it.
 */
@Mixin(targets = "net.bandit.darkdoppelganger.client.gui.DarkDoppelBossBar", remap = false)
public abstract class BossBarNamePrefixMixin {
    @WrapOperation(method = "renderBossBar(Lnet/neoforged/neoforge/client/event/CustomizeGuiOverlayEvent$BossEventProgress;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;getString()Ljava/lang/String;"),
            require = 1, allow = 1)
    private static String bons$darkdoppelgangerCoo49NamePrefix(Component name, Operation<String> original) {
        return DoppelNamePrefix.answer(name, original);
    }
}
