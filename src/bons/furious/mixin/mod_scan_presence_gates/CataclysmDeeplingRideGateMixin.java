package bons.furious.mixin.mod_scan_presence_gates;

import bons.furious.patch.mod_scan_presence_gates.ModScanGates;
import com.github.L_Ender.cataclysm.entity.Deepling.AbstractDeepling;
import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.Coralssus_Entity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_deepling_ride_presence_gate (L_Ender's Cataclysm 3.16, 3.31 identical; CC-BY-NC-ND-4.0, no code carried; server in
 * practice: goals run where the mob's AI runs). AbstractDeepling$RidingCoralssus.canUse searches Coralssus_Entity +-15
 * blocks around the Deepling before it tests whether the Deepling is a passenger or dry (moistness <= 300). canUse is
 * wrapped: while the Deepling is a passenger or dry, the search's walk is left out (vanilla_entity_walk_skip, UNUSED: the
 * found Coralssus is never read, canUse is false either way). The search itself (in canUse, start and tick) is wrapped: while
 * the level holds no Coralssus_Entity (vanilla_entity_class_count_layer) its walk is left out (EMPTY). canUse, start and tick
 * then run unchanged. See ModScanGates.
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.Deepling.AbstractDeepling$RidingCoralssus", remap = false)
public abstract class CataclysmDeeplingRideGateMixin {
    @Shadow
    @Final
    private AbstractDeepling drowned;

    @WrapMethod(method = "m_8036_")
    private boolean bons$rideFirst(Operation<Boolean> original) {
        if (!ModScanGates.deepling) return original.call();
        AbstractDeepling d = this.drowned;
        return ModScanGates.deeplingCanUse(d.m_20159_() || d.getMoistness() <= 300, d.m_9236_(), Coralssus_Entity.class, original);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "getClosestCoralssus_Entity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$coralssusScan(Level level, Class<?> searched, AABB box, Operation<List> original) {
        return ModScanGates.scan(ModScanGates.deepling, ModScanGates.DEEPLING, level, searched, box, original);
    }
}
