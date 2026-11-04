package bons.furious.mixin.mod_scan_presence_gates;

import bons.furious.patch.mod_scan_presence_gates.ModScanGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * borninchaos_minion_scan_presence_gate (Born in Chaos 1.7.5, no code carried; both sides).
 * PumpkinstaffaKazhdyiTikVInvientarieProcedure.execute (every tick per carrier) searches every Entity within 6.5 blocks and
 * tames the MrPumpkinControlledEntity among them. While the level holds none (vanilla_entity_class_count_layer) the search
 * is marked UNUSED, so vanilla_entity_walk_skip leaves out its walk; the procedure then runs unchanged and finds nothing to
 * tame, as in the full list. See ModScanGates.
 */
@Mixin(targets = "net.mcreator.borninchaosv.procedures.PumpkinstaffaKazhdyiTikVInvientarieProcedure", remap = false)
public abstract class BicPumpkinStaffScanMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$minionScan(LevelAccessor level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return ModScanGates.minionScan(ModScanGates.BIC_PUMPKIN, level, searched, box, predicate, original);
    }
}
