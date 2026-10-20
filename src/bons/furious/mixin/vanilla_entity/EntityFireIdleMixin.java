package bons.furious.mixin.vanilla_entity;

import bons.furious.patch.vanilla_entity.FireIdleScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_fire_scan_idle_skip (Minecraft 1.20.1, both sides; since 1.0.45): the fire check at the end of Entity.move
 * (m_6478_). For an entity that was not burning and whose fire timer already holds the reset value, the check's answer can
 * only lead to writing that same value again, so the wrapped getBlockStatesIfLoaded call returns FireIdleScan's stream, whose
 * noneMatch answers true after the same hasChunksAt and chunk lookups, without reading blocks (see FireIdleScan for the
 * exact conditions and the one documented difference). Every other entity, and any other use of the stream, goes on to the
 * original call - through vanilla_fire_scan_loop's wrapper when that switch applies. Priority 1600 (above that wrapper's
 * 1500) makes this the outer wrapper; require = 0 as for the other wrapper. Our own logic only.
 */
@Mixin(value = Entity.class, remap = false, priority = 1600)
public abstract class EntityFireIdleMixin {
    @Shadow
    public boolean f_146810_;     // wasOnFire, set to isOnFire() at the start of move

    @Shadow
    private int f_19831_;         // remainingFireTicks

    @WrapOperation(method = "m_6478_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_46847_(Lnet/minecraft/world/phys/AABB;)Ljava/util/stream/Stream;"))
    private Stream<BlockState> bons$fireIdleSkip(Level level, AABB box, Operation<Stream<BlockState>> original) {
        return FireIdleScan.scan((Entity) (Object) this, this.f_146810_, this.f_19831_, level, box, original);
    }
}
