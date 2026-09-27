package agentcraft.pure;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
/** Called only inside one Kismet send invocation, with its unchanged immutable burst. */
public final class EncodedBurst {
    private EncodedBurst() {}
    public static Packet<?> send(SimpleChannel channel,PacketDistributor.PacketTarget target,Object message,Packet<?> encoded) {
        if(encoded==null)encoded=channel.toVanillaPacket(message,target.getDirection());
        target.send(encoded);
        return encoded;
    }
}
