package bons.furious.mixin.chimes_coo49;

import bons.furious.patch.chimes_coo49.ChimesScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * chimes_client_phantom_scan_skip (Chimes 2.0.1, client; no Chimes code carried). On a client level WindBellBE.tick's
 * Phantom search can only matter for a phantom whose getTarget() is not null (the loop skips every other one). While no
 * client-side phantom can have a target (ChimesScans), the search is marked UNUSED: vanilla_entity_walk_skip leaves out
 * its walk and the loop finds nothing to act on, as with the full list. See ChimesScans.
 */
@Mixin(targets = "com.nick.chimes.block.entity.WindBellBE", remap = false)
public abstract class WindBellClientSkipMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$chimesBellClientScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ChimesScans.clientSkip(level, searched, box, original);
    }
}
