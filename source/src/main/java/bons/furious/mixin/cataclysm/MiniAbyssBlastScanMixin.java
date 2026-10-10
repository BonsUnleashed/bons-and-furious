package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmWorldScans;
import com.github.L_Ender.cataclysm.entity.projectile.Mini_Abyss_Blast_Entity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cataclysm_beam_client_scans (Mini_Abyss_Blast_Entity; L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is
 * carried; 1.21.1 tested build: L_Ender's Cataclysm 1.21.1-3.33; acts on the client).
 *
 * tick() calls raytraceEntities(level, ...) on both sides; it clips the beam against blocks (kept: the renderer reads the
 * collide position and side) and then adds every living entity whose padded box the beam crosses to the result, which
 * tick() uses only inside if (!level.isClientSide) (the only read of the result's entities). On a client level the
 * entity query returns an empty list instead; the loop over it only read.
 *
 * Ported to 1.21.1: unchanged. In 3.33 tick() still reads the hit list only in its server branch, and a constant-pool scan
 * of the 3.33 jar and every pinned 1.21.1 target finds no other caller of raytraceEntities or reader of the hit list.
 */
@Mixin(value = Mini_Abyss_Blast_Entity.class, remap = false)
public abstract class MiniAbyssBlastScanMixin {
    @WrapOperation(method = "raytraceEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> bons$noClientBeamScan(Level level, Class<T> type, AABB box, Operation<List<T>> original) {
        if (CataclysmWorldScans.skipBeamScan(level)) return List.of();
        return original.call(level, type, box);
    }
}
