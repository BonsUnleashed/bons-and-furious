package bons.furious.mixin.tooltipoverhaul_c49;

import bons.furious.patch.tooltipoverhaul_c49.FrameTagKeys;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.xylonity.tooltipoverhaul.client.frame.CustomFrameData;
import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tooltipoverhaul_frame_tag_keys_memo (Tooltip Overhaul 2.0.7 for NeoForge 1.21.1, client, since 1.0.49; exact). MixinExtras
 * @WrapOperation on the getTagKeys() call inside CustomFrameData.matchScore: the definition keeps the list with a snapshot of
 * the tag strings it came from and answers with it while the tags list is unchanged (see FrameTagKeys for why the result is
 * identical). Tooltip Overhaul is GPL-3.0; no Tooltip Overhaul code is carried. require = 0.
 */
@Mixin(value = CustomFrameData.class, remap = false)
public abstract class FrameTagKeysMixin {
    @Shadow
    @Final
    private List<String> tags;

    @Unique
    private FrameTagKeys.Memo bons$tooltipoverhaulC49TagMemo;

    @WrapOperation(method = "matchScore", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/xylonity/tooltipoverhaul/client/frame/CustomFrameData;getTagKeys()Ljava/util/List;"))
    private List<TagKey<Item>> bons$tooltipoverhaulC49TagKeys(CustomFrameData self, Operation<List<TagKey<Item>>> original) {
        FrameTagKeys.Memo kept = this.bons$tooltipoverhaulC49TagMemo;
        if (kept != null && FrameTagKeys.enabled && !FrameTagKeys.SHADOW && kept.builtFrom(this.tags)) return kept.keys();
        FrameTagKeys.Memo next = FrameTagKeys.rebuild(self, this.tags, kept, original);
        this.bons$tooltipoverhaulC49TagMemo = next.keep() ? next : null;
        return next.keys();
    }
}
