package bons.furious.mixin.dh_loader;

import bons.furious.patch.dh_loader.UnlockedByteStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import java.io.ByteArrayInputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_unlocked_byte_stream (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge, LGPL-3.0; both sides).
 *
 * The two ByteArrayInputStream constructions in DhDataInputStream.create(byte[], EDhApiDataCompressionMode,
 * PhantomArrayListCheckout) build {@link UnlockedByteStream} instead: the same stream without a monitor around each read.
 * NEW ordinal 0 is the decompressed Z_STD_BLOCK frame, new ByteArrayInputStream(byte[], int, int); ordinal 1 the raw array
 * for every other mode, new ByteArrayInputStream(byte[]). No other mod mixes into this class; no DH code is carried.
 *
 * Ported to 1.21.1: no change (DhDataInputStream is byte-identical in DH 3.3.3: same two NEW sites in the same order).
 */
@Mixin(value = DhDataInputStream.class, remap = false)
public abstract class UnlockedByteStreamMixin {
    private static final String CREATE = "create([BLcom/seibel/distanthorizons/api/enums/config/EDhApiDataCompressionMode;"
            + "Lcom/seibel/distanthorizons/core/util/objects/pooling/PhantomArrayList/PhantomArrayListCheckout;)"
            + "Lcom/seibel/distanthorizons/core/util/objects/dataStreams/DhDataInputStream;";

    @Redirect(method = CREATE, at = @At(value = "NEW", target = "java/io/ByteArrayInputStream", ordinal = 0))
    private static ByteArrayInputStream bons$unlockedSlice(byte[] buf, int offset, int length) {
        return UnlockedByteStream.of(buf, offset, length);
    }

    @Redirect(method = CREATE, at = @At(value = "NEW", target = "java/io/ByteArrayInputStream", ordinal = 1))
    private static ByteArrayInputStream bons$unlockedArray(byte[] buf) {
        return UnlockedByteStream.of(buf);
    }
}
