package bons.furious.mixin.dh_loader;

import bons.furious.patch.dh_loader.PooledStringIndex;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.util.objects.pooling.StringPool;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * distanthorizons_pooled_string_index (Distant Horizons 3.3.2, LGPL-3.0; both sides).
 *
 * FullDataPointIdMap.deserialize interns the biome text and the block-state text of every id-map entry through
 * StringPool.INSTANCE.getPooledString (DH's per-character trie). Both calls go through {@link PooledStringIndex#pooled},
 * which returns the trie's own canonical String for the text (from its table when the trie already answered for that
 * text, otherwise from the trie). The two calls are the pool's only users in DH 3.3.2. No other mod mixes into this
 * class; no DH code is carried.
 */
@Mixin(value = FullDataPointIdMap.class, remap = false)
public abstract class PooledStringIndexMixin {
    @Redirect(method = "deserialize", require = 2, allow = 2, at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/util/objects/pooling/StringPool;getPooledString(Lit/unimi/dsi/fastutil/chars/CharArrayList;)Ljava/lang/String;"))
    private static String bons$pooledString(StringPool pool, CharArrayList chars) {
        return PooledStringIndex.pooled(pool, chars);
    }
}
