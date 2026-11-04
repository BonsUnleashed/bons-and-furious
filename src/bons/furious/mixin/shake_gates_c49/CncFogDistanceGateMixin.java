package bons.furious.mixin.shake_gates_c49;

import bons.furious.patch.shake_gates_c49.ShakeGates49;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cnc_wechuge_fog_scan_presence_gate (Critters n' Crawlers 2.2.5, all rights reserved: no code carried; client).
 * FogDistanceSetupProcedure.execute (RenderFog) searches WechugeEntity in a 24 and then a 12 block box around the camera
 * entity and pulls the fog in when one is found. While the level holds no Wechuge (vanilla_entity_class_count_layer), both
 * searches are marked so that vanilla_entity_walk_skip leaves out their empty walks; the procedure runs unchanged on the same
 * empty lists. See ShakeGates49 and CncFogColorGateMixin.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.FogDistanceSetupProcedure", remap = false)
public abstract class CncFogDistanceGateMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 2, allow = 2)
    private static List bons$wechugeScan(LevelAccessor level, Class<?> searched, AABB box, Predicate<?> predicate, Operation<List> original) {
        return ShakeGates49.scan(ShakeGates49.cncWechuge, ShakeGates49.CNC_WECHUGE, level, searched, box, predicate, original);
    }
}
