package bons.furious.patch.forge_payload;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Bons and Furious switch forge_custom_payload_heap_copy (Forge 47.4.16, ClientboundCustomPayloadPacket). Fix.
 *
 * ClientboundCustomPayloadPacket.getData (m_132045_) hands out ByteBuf.copy() of the packet's data. A packet decoded from
 * the network holds a pooled direct buffer, so the copy is pooled direct as well. Forge's network event for mod channels
 * keeps that copy (NetworkEvent stores getInternalData(), which is getData()) and nothing ever releases it, so on a client
 * connected to a server every mod packet leaves one pooled direct buffer behind in Netty's arena: memory outside the heap
 * that the garbage collector cannot give back (Forge issue 10861; Forge 47.4.24 makes the copy a heap buffer).
 *
 * The copy is now an unpooled heap buffer with exactly what ByteBuf.copy() gives: the readable bytes of the data from its
 * reader index, reader index 0, writer index and capacity equal to that length, the same maximum capacity, big-endian
 * order; the data's own indices are not touched. Every handler reads the same bytes. The buffer is garbage collected
 * like any object once nothing references it (releasing it, as vanilla's handler does, still works).
 */
public final class PayloadCopies {
    private PayloadCopies() {
    }

    /** ByteBuf.copy() of src, as an unpooled heap buffer. */
    public static ByteBuf heapCopy(ByteBuf src) {
        int length = src.readableBytes();
        ByteBuf copy = Unpooled.buffer(length, src.maxCapacity());
        copy.writeBytes(src, src.readerIndex(), length);
        return copy;
    }
}
