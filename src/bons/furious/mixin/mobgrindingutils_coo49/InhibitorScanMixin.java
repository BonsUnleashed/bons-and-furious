package bons.furious.mixin.mobgrindingutils_coo49;

import bons.furious.patch.mobgrindingutils_coo49.InhibitorScan;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mob_grinding_utils.events.MGUEndermanInhibitEvent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * mobgrindingutils_inhibitor_scan_sections (Mob Grinding Utils 1.20.1-1.1.0, server side; no Mob Grinding Utils code is
 * carried). MGUEndermanInhibitEvent.getIsInhibited(entity), called for every teleport attempt, reads every block of the
 * entity's box grown by 8; the wrapper walks the same positions in the same order with the same chunk lookups but skips the
 * z-runs of chunk sections whose palette holds no active Ender Inhibitor: same answer, same chunk accesses. See
 * InhibitorScan.
 */
@Mixin(value = MGUEndermanInhibitEvent.class, remap = false)
public abstract class InhibitorScanMixin {
    @WrapMethod(method = "getIsInhibited(Lnet/minecraft/world/entity/LivingEntity;)Z")
    private boolean bons$mobgrindingutilsInhibitorScan(LivingEntity entity, Operation<Boolean> original) {
        return InhibitorScan.isInhibited(entity, original);
    }
}
