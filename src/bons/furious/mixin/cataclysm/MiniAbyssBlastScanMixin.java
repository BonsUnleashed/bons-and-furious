package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.github.L_Ender.cataclysm.entity.projectile.Mini_Abyss_Blast_Entity;

/**
 * cataclysm_beam_client_scans (Mini_Abyss_Blast_Entity, L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; acts on
 * the client).
 *
 * tick() calls raytraceEntities(level, ...) on both sides; it clips the beam against blocks (kept: the renderer reads the
 * collide position and side) and then adds every living entity whose padded box the beam crosses to the result, which
 * tick() uses only inside if (!level.isClientSide) (bytecode: the only read of the result's entities). On a client level
 * the entity query returns an empty list instead; the loop over it only read.
 */
@Mixin(value = Mini_Abyss_Blast_Entity.class, remap = false)
public abstract class MiniAbyssBlastScanMixin {
    @WrapOperation(method = "raytraceEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> bons$noClientBeamScan(Level level, Class<T> type, AABB box, Operation<List<T>> original) {
        if (CataclysmWorldScans.skipBeamScan(level)) return List.of();
        return original.call(level, type, box);
    }
}
