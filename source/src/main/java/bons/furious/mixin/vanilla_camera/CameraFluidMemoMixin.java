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
 * vanilla_camera_fluid_memo (Minecraft 1.20.1 client), part 1 of 2: Camera.getFluidInCamera (getFluidInCamera) answers repeats
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
    private boolean initialized;           // initialized

    @Shadow
    private BlockGetter level;       // level

    @Shadow
    private Vec3 position;              // position

    @Shadow
    @Final
    private BlockPos.MutableBlockPos blockPosition;   // blockPosition

    @Shadow
    @Final
    private Vector3f forwards;          // forwards

    @Shadow
    @Final
    private Vector3f up;          // up

    @Shadow
    @Final
    private Vector3f left;          // left

    @WrapMethod(method = "getFluidInCamera")
    private FogType bons$fluidInCameraMemo(Operation<FogType> original) {
        return CameraFluidMemo.fogType((Camera) (Object) this, original, this.initialized, this.level, this.position, this.blockPosition,
                this.forwards, this.up, this.left);
    }
}
