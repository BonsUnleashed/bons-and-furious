package bons.furious.mixin.skarriermobs_coo49;

import bons.furious.patch.skarriermobs_coo49.SkarrierScans;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * skarriermobs_region_scan_narrowing (Skarrier Mobs 1.1.2 and 1.0.6, both sides; exact): in TunnelGoreOnEntityTickUpdateProcedure, the "always true"
 * predicate of the narrowed getEntitiesOfClass(Entity.class, box, e -> true) calls becomes the test the loop over the
 * sorted result starts with, so the list holds the same elements the loop acts on, in the same order. See SkarrierScans.
 */
@Mixin(targets = "net.mcreator.skarriermobs.procedures.TunnelGoreOnEntityTickUpdateProcedure", remap = false)
public abstract class NarrowTunnelGoreMixin {
    /** Search 0 (+-4, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 0),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow0(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 1 (+-4, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 1),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow1(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 2 (+-100, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 2),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow2(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 3 (+-4, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 3),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow3(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 4 (+-100, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 4),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow4(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 5 (+-3.5, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 5),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow5(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 6 (+-125, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 6),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow6(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 8 (+-100, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 8),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow8(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 9 (+-25, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 9),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow9(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }

    /** Search 10 (+-12.5, players): the loop skips every element that fails SkarrierScans.PLAYER. */
    @ModifyArg(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", ordinal = 10),
            index = 2, require = 1, allow = 1)
    private static Predicate<?> bons$skarriermobsCoo49Narrow10(Predicate<?> original) {
        return SkarrierScans.narrow(original, SkarrierScans.PLAYER);
    }
}
