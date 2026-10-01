package bons.furious.mixin.forge;

import bons.furious.patch.forge.StatusPingChannels;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ServerStatusPing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * forge_status_ping_channel_groups (Forge 47.4.16): toBuf groups the channels once (start), takes each mod's group from
 * that grouping instead of rescanning every channel (redirect), and drops it again (returns). Forge code is LGPL-2.1; only
 * the call is redirected. Once per server-list ping, on the network thread.
 */
@Mixin(value = ServerStatusPing.class, remap = false)
public abstract class StatusPingChannelGroupsMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, ServerStatusPing.ChannelData> channels;

    @Shadow
    private List<Map.Entry<ResourceLocation, ServerStatusPing.ChannelData>> getChannelsForMod(String modId) {
        throw new AssertionError();
    }

    @Inject(method = "toBuf", at = @At("HEAD"))
    private void bons$groupChannels(CallbackInfoReturnable<ByteBuf> cir) {
        StatusPingChannels.begin(this, this.channels);
    }

    @Redirect(method = "toBuf", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/network/ServerStatusPing;getChannelsForMod(Ljava/lang/String;)Ljava/util/List;"))
    @SuppressWarnings("unchecked")
    private List<Map.Entry<ResourceLocation, ServerStatusPing.ChannelData>> bons$groupForMod(ServerStatusPing self, String modId) {
        List<Map.Entry<ResourceLocation, ServerStatusPing.ChannelData>> group = StatusPingChannels.forMod(this, modId);
        return group != null ? group : this.getChannelsForMod(modId);
    }

    @Inject(method = "toBuf", at = @At("RETURN"))
    private void bons$dropGroups(CallbackInfoReturnable<ByteBuf> cir) {
        StatusPingChannels.end();
    }
}
