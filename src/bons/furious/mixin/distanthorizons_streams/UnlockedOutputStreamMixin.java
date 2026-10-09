package bons.furious.mixin.distanthorizons_streams;

import bons.furious.patch.distanthorizons_streams.UnlockedOutputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import java.io.ByteArrayOutputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_unlocked_output_stream (Distant Horizons 3.3.2, LGPL-3.0; both sides; tested with DH 3.3.2-1.20.1).
 *
 * The one construction in DhDataOutputStream.create(EDhApiDataCompressionMode, ByteArrayList), new ByteArrayOutputStream(),
 * builds {@link UnlockedOutputStream} instead: the same buffer without a monitor around each write (see the helper for why
 * the stored bytes are the same bytes). The output-side sibling of 1.0.29's distanthorizons_unlocked_byte_stream. No other
 * mod mixes into this class; no DH code is carried.
 */
@Mixin(value = DhDataOutputStream.class, remap = false)
public abstract class UnlockedOutputStreamMixin {
    @Redirect(method = "create(Lcom/seibel/distanthorizons/api/enums/config/EDhApiDataCompressionMode;Lit/unimi/dsi/fastutil/bytes/ByteArrayList;)"
            + "Lcom/seibel/distanthorizons/core/util/objects/dataStreams/DhDataOutputStream;",
            at = @At(value = "NEW", target = "java/io/ByteArrayOutputStream"))
    private static ByteArrayOutputStream bons$unlockedBuffer() {
        return UnlockedOutputStream.create();
    }
}
