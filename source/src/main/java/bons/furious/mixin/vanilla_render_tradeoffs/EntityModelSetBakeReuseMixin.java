package bons.furious.mixin.vanilla_render_tradeoffs;

import bons.furious.patch.vanilla_render_tradeoffs.ArmorBakeReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_armor_layer_bake_reuse (Minecraft 1.21.1 on NeoForge 21.1.252, client; since 1.0.49; trade-off, off by default):
 *  - @WrapMethod around EntityModelSet.bakeLayer (roots.get(layer) or IllegalArgumentException, then
 *    LayerDefinition.bakeRoot()): inside an armour model lookup on the render thread the layer's first baked tree is handed
 *    back again (ArmorBakeReuse.bake); otherwise the method runs unchanged. Entity Model Features' RETURN injector and
 *    Fusion's return-value modifier stay inside the wrapped method (they run when a layer is baked).
 *  - HEAD and RETURN of onResourceManagerReload (roots = copyOf(LayerDefinitions.createRoots())): the memo is dropped, so
 *    trees baked from the old layer definitions are never handed out after a reload.
 * The memo is a plain map in a new field of the EntityModelSet (render thread only). No Minecraft code is carried.
 */
@Mixin(value = EntityModelSet.class, remap = false)
public abstract class EntityModelSetBakeReuseMixin {
    @Unique
    private Map<ModelLayerLocation, ModelPart> bons$armorBakes;

    @WrapMethod(method = "bakeLayer")
    private ModelPart bons$reuseArmorBake(ModelLayerLocation layer, Operation<ModelPart> original) {
        if (!ArmorBakeReuse.active()) return original.call(layer);
        Map<ModelLayerLocation, ModelPart> memo = this.bons$armorBakes;
        if (memo == null) this.bons$armorBakes = memo = new HashMap<>();
        return ArmorBakeReuse.bake(memo, layer, original);
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void bons$dropArmorBakesBefore(ResourceManager resourceManager, CallbackInfo ci) {
        this.bons$armorBakes = null;
    }

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void bons$dropArmorBakesAfter(ResourceManager resourceManager, CallbackInfo ci) {
        this.bons$armorBakes = null;
    }
}
