package bons.furious.mixin.mysticalagriculture_coo49;

import bons.furious.patch.mysticalagriculture_coo49.CropNameKeys;
import com.blakebr0.mysticalagriculture.api.crop.Crop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * mysticalagriculture_crop_name_key_memo (Mystical Agriculture 7.0.24, both sides; kind opt, default on; no Mystical
 * Agriculture code carried). Crop.getDisplayName(): the String.format of the translation key (reached only when no display
 * name is set) answers the key this crop made the first time, for a crop whose class keeps Crop's id getters; any other
 * crop gets the same text made each time (CropNameKeys.format: the same characters String.format gives for this format
 * and two String arguments); the component around it is still made new on every call. A crop with a display name never
 * reaches this code. require = 0: when another mod already changed this call, the switch stands aside (one INFO line from
 * MysticalAgriculturePlugin). See CropNameKeys.
 */
@Mixin(value = Crop.class, remap = false)
public abstract class CropNameKeyMixin {
    @Unique
    private String bons$mysticalagricultureNameKey;

    @Redirect(method = "getDisplayName()Lnet/minecraft/network/chat/Component;",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"),
            require = 0, allow = 1)
    private String bons$mysticalagricultureCropName(String format, Object[] args) {
        if (!CropNameKeys.enabled) return String.format(format, args);
        String key = this.bons$mysticalagricultureNameKey;
        if (key != null && key != CropNameKeys.NOT_PLAIN) return key;
        String made = CropNameKeys.format(format, args);
        if (key == null) this.bons$mysticalagricultureNameKey = CropNameKeys.plain(((Crop) (Object) this).getClass()) ? CropNameKeys.kept(made) : CropNameKeys.NOT_PLAIN;
        return made;
    }
}
