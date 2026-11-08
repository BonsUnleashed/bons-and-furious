package bons.furious.mixin.cataclysm_client_c49;

import bons.furious.patch.cataclysm_client_c49.TentacleLight;
import com.github.L_Ender.cataclysm.client.render.entity.The_Leviathan_Renderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_tentacle_light_memo (L_Ender's Cataclysm; 1.21.1 tested build: L_Ender's Cataclysm 1.21.1-3.33; client; since
 * 1.0.49): both calls of the private getLightColor(Entity, Vec3) in The_Leviathan_Renderer.render (tongue segments and its
 * end) go through TentacleLight, which keeps each block position's answer for the rest of that render() call. Our own logic
 * only.
 *
 * Ported to 1.21.1: 3.33's render and getLightColor are the 3.16 code (BlockPos.containing, hasChunkAt, two
 * LevelRenderer.getLightColor reads).
 */
@Mixin(value = The_Leviathan_Renderer.class, remap = false)
public abstract class LeviathanTongueLightMixin {
    @WrapOperation(method = "render(Lcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/The_Leviathan/The_Leviathan_Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/github/L_Ender/cataclysm/client/render/entity/The_Leviathan_Renderer;getLightColor(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)I"),
            require = 2, allow = 2)
    private int bons$tongueLight(The_Leviathan_Renderer self, Entity head, Vec3 position, Operation<Integer> original, @Share("bons$lightCall") LocalIntRef call) {
        return TentacleLight.light(head, position, original, self, call);
    }
}
