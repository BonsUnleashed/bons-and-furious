package bons.furious.mixin.architectury_mop49;

import bons.furious.patch.architectury_mop49.ReceiverLock;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.transformers.PacketTransformer;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * architectury_receiver_registration_lock (Architectury 13.0.11 for NeoForge 1.21.1, both sides; since 1.0.49).
 *
 * The four public registration methods of NetworkAggregator (registerReceiver(Side, ResourceLocation, List,
 * NetworkReceiver), registerReceiver(Side, Type, StreamCodec, List, NetworkReceiver), registerS2CType(ResourceLocation, List)
 * and registerS2CType(Type, StreamCodec, List)) are the only writers of its eight plain HashMaps after its static
 * initialiser (the private registerC2SReceiver / registerS2CReceiver are called from them only). Mods are constructed and set
 * up on several loading threads at once, so two registrations can write the same map at the same time and one entry can be
 * lost. All four methods are wrapped whole and run under ReceiverLock.LOCK: they run one at a time, each exactly as before
 * (its puts, PacketTransformer.concat, the adaptor's listener registration), in the order the threads reach the lock; the
 * nested registerS2CType call re-enters the same monitor. Fix: no registration can be lost any more; a single thread's
 * registrations behave exactly as before. On Architectury 9.2.14 for Forge the same maps lived in
 * dev.architectury.networking.forge.NetworkManagerImpl; on 13.0.11 that class only builds the adaptor.
 */
@Mixin(targets = "dev.architectury.impl.NetworkAggregator", remap = false)
public abstract class NetworkAggregatorLockMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapMethod(method = "registerReceiver(Ldev/architectury/networking/NetworkManager$Side;Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;Ldev/architectury/networking/NetworkManager$NetworkReceiver;)V")
    private static void bons$architecturyMop49LockedReceiverById(NetworkManager.Side side, ResourceLocation id, List<PacketTransformer> transformers,
                                                                NetworkManager.NetworkReceiver receiver, Operation<Void> original) {
        if (!ReceiverLock.enabled) {
            original.call(side, id, transformers, receiver);
            return;
        }
        ReceiverLock.announce();
        synchronized (ReceiverLock.LOCK) {
            original.call(side, id, transformers, receiver);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapMethod(method = "registerReceiver(Ldev/architectury/networking/NetworkManager$Side;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/List;Ldev/architectury/networking/NetworkManager$NetworkReceiver;)V")
    private static void bons$architecturyMop49LockedReceiverByType(NetworkManager.Side side, CustomPacketPayload.Type type, StreamCodec codec,
                                                                  List<PacketTransformer> transformers, NetworkManager.NetworkReceiver receiver,
                                                                  Operation<Void> original) {
        if (!ReceiverLock.enabled) {
            original.call(side, type, codec, transformers, receiver);
            return;
        }
        ReceiverLock.announce();
        synchronized (ReceiverLock.LOCK) {
            original.call(side, type, codec, transformers, receiver);
        }
    }

    @WrapMethod(method = "registerS2CType(Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;)V")
    private static void bons$architecturyMop49LockedS2CTypeById(ResourceLocation id, List<PacketTransformer> transformers, Operation<Void> original) {
        if (!ReceiverLock.enabled) {
            original.call(id, transformers);
            return;
        }
        ReceiverLock.announce();
        synchronized (ReceiverLock.LOCK) {
            original.call(id, transformers);
        }
    }

    @SuppressWarnings("rawtypes")
    @WrapMethod(method = "registerS2CType(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$Type;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/List;)V")
    private static void bons$architecturyMop49LockedS2CTypeByType(CustomPacketPayload.Type type, StreamCodec codec, List<PacketTransformer> transformers,
                                                                 Operation<Void> original) {
        if (!ReceiverLock.enabled) {
            original.call(type, codec, transformers);
            return;
        }
        ReceiverLock.announce();
        synchronized (ReceiverLock.LOCK) {
            original.call(type, codec, transformers);
        }
    }
}
