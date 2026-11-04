package bons.furious.mixin.iceandfire_client_c49;

import bons.furious.patch.iceandfire_client_c49.DragonTextureKey;
import com.github.alexthe666.iceandfire.client.render.entity.RenderDragonBase;
import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * iceandfire_dragon_texture_key (Ice and Fire 2.1.13-1.20.1-beta-5, client, since 1.0.49): RenderDragonBase.getTextureLocation
 * is wrapped; bons.furious.patch.iceandfire_client_c49.DragonTextureKey answers from this renderer's own table (one @Unique
 * field, made on first use) or runs the original.
 */
@Mixin(value = RenderDragonBase.class, remap = false)
public abstract class RenderDragonBaseTextureMixin {
    @Unique
    private DragonTextureKey.Memo bons$textureMemo;

    @WrapMethod(method = "getTextureLocation(Lcom/github/alexthe666/iceandfire/entity/EntityDragonBase;)Lnet/minecraft/resources/ResourceLocation;", remap = false)
    private ResourceLocation bons$textureKey(EntityDragonBase dragon, Operation<ResourceLocation> original) {
        DragonTextureKey.Memo m = bons$textureMemo;
        if (m == null) bons$textureMemo = m = new DragonTextureKey.Memo();
        return DragonTextureKey.texture(m, dragon, original);
    }
}
