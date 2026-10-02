package bons.furious.mixin.vanilla_camera;

import bons.furious.patch.vanilla_camera.CameraFluidMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_camera_fluid_memo (Minecraft 1.20.1 client), part 1 of 2: Camera.getFluidInCamera (m_167685_) answers repeats
 * inside one render pass from CameraFluidMemo, keyed on every camera field the method and its wrappers read.
 *
 * The wrapper must be the OUTERMOST layer of the method, outside Valkyrien Skies' own @WrapMethod and the Alex's Caves,
 * Blast from the Past, Snow! Real Magic, Clockwork and Valkyrien Skies fluid-fix injections, so a remembered answer skips
 * all of them. MixinExtras nests @WrapMethod handlers by mixin priority (the later-applied, higher-priority mixin wraps
 * the others), hence priority 2000; the offline neighbour check (all 17 indexed Camera mixins applied with this one)
 * confirms the order and fingerprints the merged method. Vivecraft's XRCamera override calls this method through super.
 */
@Mixin(value = Camera.class, priority = 2000, remap = false)
public abstract class CameraFluidMemoMixin {
    @Shadow
    private boolean f_90549_;           // initialized

    @Shadow
    private BlockGetter f_90550_;       // level

    @Shadow
    private Vec3 f_90552_;              // position

    @Shadow
    @Final
    private BlockPos.MutableBlockPos f_90553_;   // blockPosition

    @Shadow
    @Final
    private Vector3f f_90554_;          // forwards

    @Shadow
    @Final
    private Vector3f f_90555_;          // up

    @Shadow
    @Final
    private Vector3f f_90556_;          // left

    @WrapMethod(method = "m_167685_")
    private FogType bons$fluidInCameraMemo(Operation<FogType> original) {
        return CameraFluidMemo.fogType((Camera) (Object) this, original, this.f_90549_, this.f_90550_, this.f_90552_, this.f_90553_,
                this.f_90554_, this.f_90555_, this.f_90556_);
    }
}
