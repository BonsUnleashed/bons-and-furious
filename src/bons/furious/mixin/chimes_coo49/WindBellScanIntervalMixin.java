package bons.furious.mixin.chimes_coo49;

import bons.furious.patch.chimes_coo49.ChimesScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * chimes_phantom_scan_interval (Chimes 2.0.1, server; change, off by default; no Chimes code carried). On a server level
 * WindBellBE.tick's Phantom search runs only on the bell's scan ticks ((game time + an offset from its position) a multiple
 * of the value line's N, default 4); on the other ticks it is marked UNUSED and comes back empty, so a phantom that picked a
 * target inside the bell's box keeps it up to N-1 ticks longer. The rest of the tick is unchanged. See ChimesScans.
 */
@Mixin(targets = "com.nick.chimes.block.entity.WindBellBE", remap = false)
public abstract class WindBellScanIntervalMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$chimesBellIntervalScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ChimesScans.interval(level, ((BlockEntity) (Object) this).m_58899_(), searched, box, original);
    }
}
