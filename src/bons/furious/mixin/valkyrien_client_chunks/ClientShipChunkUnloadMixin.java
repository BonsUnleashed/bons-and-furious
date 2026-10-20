package bons.furious.mixin.valkyrien_client_chunks;

import bons.furious.patch.valkyrien_client_chunks.ShipChunkUnload;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.util.collection.LongObjectMap;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * valkyrien_client_ship_chunk_unload (Valkyrien Skies 2.4.11), client only. [FIX]
 *
 * Valkyrien Skies' client.world.MixinClientChunkCache drops ship (shipyard) chunks from its own map without unloading their
 * block entities, in two places: preUnload (its cancelling HEAD handler of ClientChunkCache.drop, for the server's
 * forget-chunk packet) and removeShipChunk (a merged @Unique method, called for every chunk of a ship's claim when the
 * ship is unloaded on the client). Both wrap here the one LongObjectMap.remove call that takes the chunk out of the map:
 * the call runs unchanged, and the chunk it returns (if any) has its block entities unloaded as Minecraft's client
 * unloads those of a dropped chunk (bons.furious.patch.valkyrien_client_chunks.ShipChunkUnload). preUnload is reached
 * through MixinSquared's @TargetHandler; removeShipChunk is not a handler, so it is named directly (Valkyrien Skies'
 * mixin, priority 1000, has merged it before this one, priority 1500, is applied). Valkyrien Skies' code is not changed
 * or carried: it gets the same chunk back and goes on as before.
 */
@Mixin(value = ClientChunkCache.class, priority = 1500, remap = false)
public abstract class ClientShipChunkUnloadMixin {
    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.client.world.MixinClientChunkCache", name = "preUnload")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lio/netty/util/collection/LongObjectMap;remove(J)Ljava/lang/Object;"))
    private Object bons$unloadDroppedShipChunk(LongObjectMap<?> chunks, long pos, Operation<Object> original) {
        return ShipChunkUnload.removed(original.call(chunks, pos), "drop");
    }

    @WrapOperation(method = "removeShipChunk",
            at = @At(value = "INVOKE", target = "Lio/netty/util/collection/LongObjectMap;remove(J)Ljava/lang/Object;"))
    private Object bons$unloadRemovedShipChunk(LongObjectMap<?> chunks, long pos, Operation<Object> original) {
        return ShipChunkUnload.removed(original.call(chunks, pos), "ship unload");
    }
}
