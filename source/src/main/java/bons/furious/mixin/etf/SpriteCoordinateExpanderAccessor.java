package bons.furious.mixin.etf;

import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * etf_sprite_texture_id_memo (Entity Texture Features 7.1, client).
 *
 * SpriteTextureVariantMixin reads the sprite of the SpriteCoordinateExpander that Material.buffer returns, as ETF's
 * own handler does (ETF reaches the private field through its access transformer).
 */
@Mixin(value = SpriteCoordinateExpander.class, remap = false)
public interface SpriteCoordinateExpanderAccessor {
    @Accessor("sprite")
    TextureAtlasSprite bons$sprite();
}
