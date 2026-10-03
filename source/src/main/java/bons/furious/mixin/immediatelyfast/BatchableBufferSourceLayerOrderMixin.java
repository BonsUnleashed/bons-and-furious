package bons.furious.mixin.immediatelyfast;

import net.raphimc.immediatelyfast.feature.core.BatchableBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * immediatelyfast_offset_layer_prefixes (ImmediatelyFast 1.5.5+1.20.4, Forge).
 *
 * getLayerOrder cut horse and villager texture paths after "textures/entity/horse/" (22 characters) or
 * "textures/entity/villager/" (25) with substring() before testing their prefixes, a new String per such layer on every
 * call. The substring() calls now keep the full path and all five prefix tests that read it test at that offset
 * (startsWith(prefix, 22 or 25)), so every texture gets ImmediatelyFast's own order without the copy. This includes the
 * villager "profession_level/" test that the 1.0.14 to 1.0.19 patch forgot, which gave level badge layers order 1 instead
 * of 4. The body is not an @Overwrite because it reads vanilla members that ImmediatelyFast opens with its own access
 * transformer, which our build cannot compile against.
 */
@Mixin(value = BatchableBufferSource.class, remap = false)
public abstract class BatchableBufferSourceLayerOrderMixin {
    /** horseTexturePath / villagerTexturePath = path.substring(prefix.length()): keep the full path. */
    @Redirect(method = "getLayerOrder(Lnet/minecraft/client/renderer/RenderType;)I",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;substring(I)Ljava/lang/String;"), require = 2, allow = 2)
    private String bons$keepFullPath(String path, int prefixLength) {
        return path;
    }

    /** horseTexturePath.startsWith("horse_markings"), the second startsWith call. */
    @Redirect(method = "getLayerOrder(Lnet/minecraft/client/renderer/RenderType;)I",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", ordinal = 1))
    private boolean bons$horseMarkings(String path, String prefix) {
        return path.startsWith(prefix, 22);
    }

    /** horseTexturePath.startsWith("armor/"), the third startsWith call. */
    @Redirect(method = "getLayerOrder(Lnet/minecraft/client/renderer/RenderType;)I",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", ordinal = 2))
    private boolean bons$horseArmor(String path, String prefix) {
        return path.startsWith(prefix, 22);
    }

    /** villagerTexturePath.startsWith("type/"), the fifth startsWith call. */
    @Redirect(method = "getLayerOrder(Lnet/minecraft/client/renderer/RenderType;)I",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", ordinal = 4))
    private boolean bons$villagerType(String path, String prefix) {
        return path.startsWith(prefix, 25);
    }

    /** villagerTexturePath.startsWith("profession/"), the sixth startsWith call. */
    @Redirect(method = "getLayerOrder(Lnet/minecraft/client/renderer/RenderType;)I",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", ordinal = 5))
    private boolean bons$villagerProfession(String path, String prefix) {
        return path.startsWith(prefix, 25);
    }

    /** villagerTexturePath.startsWith("profession_level/"), the seventh startsWith call (missed by 1.0.14 to 1.0.19). */
    @Redirect(method = "getLayerOrder(Lnet/minecraft/client/renderer/RenderType;)I",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z", ordinal = 6))
    private boolean bons$villagerProfessionLevel(String path, String prefix) {
        return path.startsWith(prefix, 25);
    }
}
