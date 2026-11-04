package bons.furious.mixin.skarriermobs_coo49;

import bons.furious.patch.skarriermobs_coo49.SkarrierScans;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * skarriermobs_region_scan_narrowing (Skarrier Mobs 1.1.2 and 1.0.6, both sides; exact): in SereneOnEntityTickUpdateProcedure, the "always true"
 * predicate of the narrowed getEntitiesOfClass(Entity.class, box, e -> true) calls becomes the test the loop over the
 * sorted result starts with, so the list holds the same elements the loop acts on, in the same order. See SkarrierScans.
 */
@Mixin(targets = "net.mcreator.skarriermobs.procedures.SereneOnEntityTickUpdateProcedure", remap = false)
public abstract class NarrowSereneMixin {
    /** Search 0 (+-32, monsters): the loop skips every element that fails SkarrierScans.MONSTER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 0),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow0(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.MONSTER);
    }

    /** Search 1 (+-5, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 1),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow1(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 6 (+-5, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 6),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow6(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }
}
