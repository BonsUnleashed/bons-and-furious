package bons.furious.mixin.client_class_warmup;

import bons.furious.patch.client_class_warmup.ClassWarmup;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * client_class_warmup (Minecraft 1.21.1 on NeoForge 21.1.252, client; exact variant), part 2 of 3: after
 * ClientPacketListener.handleLogin (only its render-thread pass returns normally - the network-thread pass leaves through
 * PacketUtils.ensureRunningOnSameThread's RunningOnDifferentThreadException) the world is joined: the recorded window
 * (3 minutes) opens and the warm-up's 10-second limit starts. The method runs unchanged. Helper: {@link ClassWarmup}.
 */
@Mixin(value = ClientPacketListener.class, remap = false)
public abstract class ClientPacketListenerWarmupMixin {
    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("RETURN"), require = 0)
    private void bons$classWarmupJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClassWarmup.onJoin();
    }
}
