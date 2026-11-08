package bons.furious.mixin.shake_gates_c49;

import bons.furious.patch.shake_gates_c49.CncWechugeFogGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cnc_wechuge_fog_scan_presence_gate (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, all rights reserved: no code carried;
 * client). FogColorSetupProcedure.execute (ViewportEvent.ComputeFogColor, every frame) searches WechugeEntity in a 24 and
 * then a 12 block box around the camera entity and tints the fog when one is found. While the level holds no Wechuge
 * (vanilla_entity_class_count_layer), both searches are marked so that vanilla_entity_walk_skip leaves out their empty walks
 * over the entity sections; the procedure runs unchanged on the same empty lists. See CncWechugeFogGate.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.FogColorSetupProcedure", remap = false)
public abstract class CncFogColorGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 2, allow = 2)
    private static List bons$wechugeScan(LevelAccessor level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return CncWechugeFogGate.scan(level, searched, box, predicate, original);
    }
}
