package bons.furious.mixin.architectury_mop49;

import bons.furious.patch.architectury_mop49.ReceiverLock;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.architectury.networking.NetworkManager;
import dev.architectury.networking.forge.NetworkManagerImpl;
import dev.architectury.networking.transformers.PacketTransformer;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * architectury_receiver_registration_lock (Architectury 9.2.14 for Forge, both sides; since 1.0.49).
 *
 * registerS2CReceiver and registerC2SReceiver each put the receiver and the combined transformer into two of
 * NetworkManagerImpl's four plain HashMaps (the only writers of those maps after its static initialiser). Mods are
 * constructed and set up on several loading threads at once, so two registrations can write the same map at the same
 * time and one entry can be lost. Both methods are wrapped whole and run under ReceiverLock.LOCK: they run one at a
 * time, each exactly as before (its log line, both puts, PacketTransformer.concat), in the order the threads reach the
 * lock. Fix: no registration can be lost any more; a single thread's registrations behave exactly as before.
 */
@Mixin(value = NetworkManagerImpl.class, remap = false)
public abstract class NetworkManagerImplLockMixin {
    // require = 0: Architectury marks registerS2CReceiver @OnlyIn(Dist.CLIENT), so a dedicated server has no such method
    // (the guard table's "dedicated_server" build expects it absent there); registerC2SReceiver stays required
    @WrapMethod(method = "registerS2CReceiver(Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;Ldev/architectury/networking/NetworkManager$NetworkReceiver;)V",
            require = 0)
    private static void bons$architecturyMop49LockedS2C(ResourceLocation id, List<PacketTransformer> transformers, NetworkManager.NetworkReceiver receiver,
                                                       Operation<Void> original) {
        if (!ReceiverLock.enabled) {
            original.call(id, transformers, receiver);
            return;
        }
        ReceiverLock.announce();
        synchronized (ReceiverLock.LOCK) {
            original.call(id, transformers, receiver);
        }
    }

    @WrapMethod(method = "registerC2SReceiver(Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;Ldev/architectury/networking/NetworkManager$NetworkReceiver;)V")
    private static void bons$architecturyMop49LockedC2S(ResourceLocation id, List<PacketTransformer> transformers, NetworkManager.NetworkReceiver receiver,
                                                       Operation<Void> original) {
        if (!ReceiverLock.enabled) {
            original.call(id, transformers, receiver);
            return;
        }
        ReceiverLock.announce();
        synchronized (ReceiverLock.LOCK) {
            original.call(id, transformers, receiver);
        }
    }
}
