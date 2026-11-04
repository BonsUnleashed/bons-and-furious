package bons.furious.mixin.terramity_client_c49;

import bons.furious.patch.terramity_client_c49.PhasingShader;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mcreator.terramity.procedures.PhasingShaderProcedure;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * terramity_phasing_shader_local_only (Terramity 0.9.8; client; since 1.0.49; a gameplay fix, off by default): Terramity's
 * player-tick screen effects act only for this client's own player and shut down only the post effect they loaded
 * (PhasingShader). Our own logic only.
 */
@Mixin(value = PhasingShaderProcedure.class, remap = false)
public abstract class TerramityPhasingShaderMixin {
    private static final String EXECUTE = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V";

    @WrapMethod(method = EXECUTE, remap = false)
    private static void bons$terramityPhasingLocal(Event event, LevelAccessor world, Entity entity, Operation<Void> original) {
        PhasingShader.execute(event, world, entity, original);
    }

    @WrapOperation(method = EXECUTE,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;m_109128_(Lnet/minecraft/resources/ResourceLocation;)V", remap = false),
            require = 4, allow = 4, remap = false)
    private static void bons$terramityPhasingLoad(GameRenderer renderer, ResourceLocation id, Operation<Void> original) {
        PhasingShader.load(renderer, id, original);
    }

    @WrapOperation(method = EXECUTE,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;m_109086_()V", remap = false),
            require = 1, allow = 1, remap = false)
    private static void bons$terramityPhasingShutdown(GameRenderer renderer, Operation<Void> original) {
        PhasingShader.shutdown(renderer, original);
    }
}
