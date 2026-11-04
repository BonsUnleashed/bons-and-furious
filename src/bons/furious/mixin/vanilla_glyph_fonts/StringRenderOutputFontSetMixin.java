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
 * vanilla_glyph_font_set_memo (Minecraft 1.20.1 on Forge 47.4.28, client): MixinExtras @WrapOperation of the
 * Font.getFontSet(style.getFont()) call (m_92863_) in Font$StringRenderOutput.accept (m_6411_, once per glyph). The output
 * object keeps the last font id (compared by identity) and its FontSet; a glyph asking for the same id object gets that
 * FontSet without the call, any other id calls through and is remembered. See GlyphFontSets for why every glyph gets the
 * same FontSet as before and when the switch stands aside (ImmediatelyFast's fast_text_lookup redirect of the same call).
 * Jade's StringRenderOutputMixin (an @Inject later in accept that captures the fontset local) and ImmediatelyFast's
 * getBuffer redirect are untouched; the class is package private, so it is named by string. Carries no Minecraft code.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput", remap = false)
public abstract class StringRenderOutputFontSetMixin {
    @Unique
    private ResourceLocation bons$fontId;
    @Unique
    private FontSet bons$fontSet;

    @WrapOperation(method = "m_6411_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;m_92863_(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/gui/font/FontSet;"))
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
