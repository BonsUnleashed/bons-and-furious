package bons.furious.mixin.vanilla_outline_memos;

import bons.furious.patch.vanilla_outline_memos.OutlineStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_outline_style_memo (Minecraft 1.20.1 Font, Forge 47.4.16 jar; client).
 *
 * drawInBatch8xOutline (m_168645_) draws the text eight times through a lambda (m_168654_) that, for every glyph, calls
 * style.withColor(outlineColour) (m_178520_) and hands the result to the renderer. This wraps that one call (MixinExtras
 * @WrapOperation): the recoloured style of the last (style, colour) pair is handed out again for the same Style object
 * and colour; anything else calls withColor. See OutlineStyle. No Minecraft code is carried.
 */
@Mixin(value = Font.class, remap = false)
public abstract class FontOutlineStyleMixin {
    @WrapOperation(method = "m_168654_", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Style;m_178520_(I)Lnet/minecraft/network/chat/Style;"),
            require = 1, allow = 1)
    private Style bons$outlineStyle(Style style, int colour, Operation<Style> original) {
        return OutlineStyle.recolour(style, colour, original);
    }
}
