package bons.furious.mixin.forge_status;

import bons.furious.patch.forge_status.StatusPingMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ServerStatusPing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * forge_status_ping_memo (Forge 47.4.16, both sides).
 *
 * ServerStatusPing.toBuf() (the mod/channel blob of the server-list status) and the private encodeOptimized (which packs
 * those bytes into the "d" string) are recomputed on every status rebuild although the mod list does not change. Both are
 * wrapped with a memo of the last input (helper StatusPingMemo; why the results are identical is described there). Forge
 * is LGPL-2.1; the mixin carries none of its code, only the memo around the original calls.
 */
@Mixin(value = ServerStatusPing.class, remap = false)
public abstract class ServerStatusPingMemoMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, ServerStatusPing.ChannelData> channels;

    @Shadow
    @Final
    private Map<String, String> mods;

    @WrapMethod(method = "toBuf")
    private ByteBuf bons$rememberBuf(Operation<ByteBuf> original) {
        if (!StatusPingMemo.enabled) return original.call();
        Object[] snapshot = StatusPingMemo.snapshot(this.channels, this.mods);
        ByteBuf cached = StatusPingMemo.cachedBuf(snapshot);
        if (cached != null) {
            if (StatusPingMemo.SHADOW) {
                ByteBuf own = original.call();
                StatusPingMemo.shadow("toBuf", Arrays.equals(StatusPingMemo.readable(own), StatusPingMemo.readable(cached)));
            }
            return cached;
        }
        ByteBuf result = original.call();
        StatusPingMemo.rememberBuf(snapshot, result);
        return result;
    }

    @WrapMethod(method = "encodeOptimized")
    private static String bons$rememberText(ByteBuf buf, Operation<String> original) {
        if (!StatusPingMemo.enabled) return original.call(buf);
        byte[] shadowCopy = StatusPingMemo.SHADOW ? StatusPingMemo.readable(buf) : null;
        String cached = StatusPingMemo.cachedText(buf);
        if (cached != null) {
            if (shadowCopy != null) StatusPingMemo.shadow("encodeOptimized", cached.equals(original.call(Unpooled.copiedBuffer(shadowCopy))));
            return cached;
        }
        byte[] in = shadowCopy != null ? shadowCopy : StatusPingMemo.readable(buf);
        String result = original.call(buf);
        StatusPingMemo.rememberText(in, result);
        return result;
    }
}
