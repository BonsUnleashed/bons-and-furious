package bons.furious.mixin.goetyhostility_coo49;

import bons.furious.patch.goetyhostility_coo49.GhScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * goetyhostility_squall_golem_scan_skip (Goety Hostility 1.4.4, all rights reserved: no code carried; server; exact).
 * HostileSquallGolem.tick's proximity search (Level.getEntitiesOfClass(LivingEntity, box +6)) only feeds "found one OR
 * has a target -> activeTime = 100"; while the golem has a target the search is marked UNUSED so that
 * vanilla_entity_walk_skip leaves out its walk (the call itself runs). See GhScans.squallScan.
 */
@Mixin(targets = "com.ratrod.goetyhostility.common.entities.hostile.HostileSquallGolem", remap = false)
public abstract class GhSquallGolemScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "m_8119_()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$goetyhostilitySquallScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return GhScans.squallScan(this, level, searched, box, original);
    }
}
