package bons.furious.mixin.skarriermobs_coo49;

import bons.furious.patch.skarriermobs_coo49.SkarrierScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * skarriermobs_target_proximity_scan_lean (Skarrier Mobs 1.1.2, both sides; trade-off, off by default): the first search of each (Blare +-15, Bowlder +-3, Dragger +-10, Eater +-3, Quake +-4, Slither Matriarch +-8, Trawler +-16): a
 * getEntitiesOfClass(Entity.class, box, e -> true) whose sorted result feeds a loop that acts only on the element that is
 * the procedure entity's own target answers SkarrierScans.targetOnly: [target] or an empty list, without the search.
 */
@Mixin(targets = {"net.mcreator.skarriermobs.procedures.BlareOnTickUpdateProcedure", "net.mcreator.skarriermobs.procedures.BowlderOnEntityTickUpdateProcedure", "net.mcreator.skarriermobs.procedures.DraggerOnEntityTickUpdateProcedure", "net.mcreator.skarriermobs.procedures.EaterOnEntityTickUpdateProcedure", "net.mcreator.skarriermobs.procedures.QuakeOnEntityTickUpdateProcedure", "net.mcreator.skarriermobs.procedures.SlitherMatriarchOnEntityTickUpdateProcedure", "net.mcreator.skarriermobs.procedures.TrawlerOnTickUpdateProcedure"}, remap = false)
public abstract class TargetOnlyFirstSiteMixin {
    @WrapOperation(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 0),
            require = 1, allow = 1)
    private static List<?> bons$skarriermobsCoo49TargetOnly(LevelAccessor world, Class<?> type, AABB box, Predicate<?> pred, Operation<List<?>> original,
                                                           @Local(argsOnly = true) Entity entity) {
        return SkarrierScans.targetOnly(world, type, box, pred, original, entity);
    }
}
