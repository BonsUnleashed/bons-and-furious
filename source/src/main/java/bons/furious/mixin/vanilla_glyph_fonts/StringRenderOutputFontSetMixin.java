package bons.furious.mixin.vanilla_glyph_fonts;

import bons.furious.patch.vanilla_glyph_fonts.GlyphFontSets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_glyph_font_set_memo (Minecraft 1.21.1, client): MixinExtras @WrapOperation of the Font.getFontSet(style.getFont())
 * call in Font$StringRenderOutput.accept(int, Style, int) (once per glyph). The output object keeps the last font id
 * (compared by identity) and its FontSet; a glyph asking for the same id object gets that FontSet without the call, any
 * other id calls through and is remembered. Glyph info, the baked glyph, colours, effects and the advance are computed as
 * before from that FontSet. See GlyphFontSets for why every glyph gets the same FontSet as before and when the switch stands
 * aside (ImmediatelyFast's fast_text_lookup redirect of the same call). ImmediatelyFast's getBuffer redirect in the same
 * method is untouched; the class is package private, so it is named by string. Carries no Minecraft code.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput", remap = false)
public abstract class StringRenderOutputFontSetMixin {
    @Unique
    private ResourceLocation bons$fontId;
    @Unique
    private FontSet bons$fontSet;

    @WrapOperation(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;getFontSet(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;"))
    private FontSet bons$glyphFontSet(Font font, ResourceLocation id, Operation<FontSet> original) {
        if (!GlyphFontSets.memo()) return original.call(font, id);
        FontSet last = this.bons$fontSet;
        if (last != null && id == this.bons$fontId) {
            if (!GlyphFontSets.SHADOW) return last;
            FontSet fresh = original.call(font, id);
            GlyphFontSets.verify(last, fresh, id);
            this.bons$fontSet = fresh;
            return fresh;
        }
        FontSet set = original.call(font, id);
        this.bons$fontId = id;
        this.bons$fontSet = set;
        return set;
    }
}
