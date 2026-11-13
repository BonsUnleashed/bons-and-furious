package bons.furious.mixin.cosmichorizons_start;

import bons.furious.patch.cosmichorizons_start.CosmosStart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.lointain.cosmos.network.CosmosModVariables;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cosmichorizons_sync_without_players_skip (Cosmic Horizons 0.0.7.3; both sides: the server and the integrated server).
 *
 * One wrapper around the SimpleChannel.send call of WorldVariables.syncData, no Cosmic Horizons code: when no player is
 * in the dimension the packet is meant for (the broadcast's own test, CosmosStart.nobodyThere), the send, and with it
 * the encoding of the whole variable set, is left out. The data stays marked dirty as before.
 */
@Mixin(value = CosmosModVariables.WorldVariables.class, remap = false)
public abstract class WorldVariablesSyncMixin {
    @WrapOperation(method = "syncData", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V"))
    private void bons$nobodyThere(SimpleChannel channel, PacketDistributor.PacketTarget target, Object message, Operation<Void> original,
                                  @Local(argsOnly = true) LevelAccessor world) {
        if (CosmosStart.nobodyThere(world)) return;
        original.call(channel, target, message);
    }
}
