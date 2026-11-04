package bons.furious.mixin.witherreincarnated_coo49;

import bons.furious.patch.witherreincarnated_coo49.WrWitherScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * witherreincarnated_wither_scan_presence_gate (Wither Reincarnated 1.0.5, All Rights Reserved; server side of a common
 * class): MixinExtras @WrapOperation on the Level.getEntitiesOfClass (m_6443_) call in the canUse (m_8036_) of the mod's
 * avoid-wither goal (ModifyAIEvent$5): while vanilla_entity_class_count_layer says the level holds no WitherBoss, the
 * search is marked EMPTY for vanilla_entity_walk_skip (its section walk is left out; the call itself runs). See
 * WrWitherScans. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.alexander.witherreincarnated.events.ModifyAIEvent$5", remap = false)
public abstract class AvoidWitherScanMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "m_8036_()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$witherreincarnatedCoo49AvoidScan(Level level, Class<?> searched, AABB box, Predicate predicate, Operation<List> original) {
        return WrWitherScans.avoidSearch(level, searched, box, predicate, original);
    }
}
