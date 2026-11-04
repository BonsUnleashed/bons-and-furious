package bons.furious.mixin.species_c49;

import bons.furious.patch.species_c49.ScanIntervals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ninni.species.server.entity.mob.update_1.Limpet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * species_limpet_scare_scan_interval (Species 3.5, server, since 1.0.49, off by default): the player query of
 * Limpet.aiStep runs every N-th tick of the limpet (bons.furious.patch.species_c49.ScanIntervals); on the other ticks it
 * answers an empty list.
 */
@Mixin(value = Limpet.class, remap = false)
public abstract class LimpetScareScanMixin {
    @WrapOperation(method = "m_8107_()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"), remap = false)
    private List<?> bons$scareScan(Level level, Class<?> type, AABB box, Predicate<?> filter, Operation<List<?>> original) {
        return ScanIntervals.limpetScans((Entity) (Object) this) ? original.call(level, type, box, filter) : ScanIntervals.none();
    }
}
