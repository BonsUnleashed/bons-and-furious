package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouEntitySets;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mahoutsukai_all_entities_memo (Mahou Tsukai 1.21.1-v1.36.31, All Rights Reserved; client): MixinExtras @WrapOperation on
 * the ClientProxy.getAllEntities call in RenderSpatialDisorientationMahoujinEntity.spatialRender: the set built for the
 * client's current entity list is handed out again while that list is unchanged. The method only tests the set for null,
 * asks its size and iterates it. See MahouEntitySets. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "stepsword.mahoutsukai.render.entity.RenderSpatialDisorientationMahoujinEntity", remap = false)
public abstract class MahouAllEntitiesSpatialMixin {
    @WrapOperation(method = "spatialRender(Lnet/minecraft/world/entity/Entity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At(value = "INVOKE", target = "Lstepsword/mahoutsukai/proxy/ClientProxy;getAllEntities(Lnet/minecraft/client/multiplayer/ClientLevel;)Ljava/util/HashSet;"),
            require = 1, allow = 1)
    private static HashSet<Entity> bons$mahoutsukaiCoo49AllEntitiesSpatial(ClientLevel level, Operation<HashSet<Entity>> original) {
        return MahouEntitySets.allEntities(level, original);
    }
}
