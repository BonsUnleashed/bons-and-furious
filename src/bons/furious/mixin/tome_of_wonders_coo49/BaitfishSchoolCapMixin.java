package bons.furious.mixin.tome_of_wonders_coo49;

import bons.furious.patch.tome_of_wonders_coo49.TowSchools;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tome_of_wonders_baitfish_school_cap (Tome of Wonders 1.0.0, MIT; server; trade-off, off by default): MixinExtras
 * @ModifyExpressionValue on the getMaxSchoolSize() (m_6031_, 72) call inside Baitfish.finalizeSpawn, the size of the
 * school a natural baitfish brings along ((int)(size * nextFloat()) more fish, added past the mob cap): the size becomes
 * min(size, the key's value). The single nextFloat draw stays; the schooling AI's own use of getMaxSchoolSize is not
 * touched. Our own logic only.
 */
@Mixin(targets = "com.platypushasnohat.tome_of_wonders.entities.Baitfish", remap = false)
public abstract class BaitfishSchoolCapMixin {
    @ModifyExpressionValue(method = "m_6518_(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;",
            at = @At(value = "INVOKE", target = "Lcom/platypushasnohat/tome_of_wonders/entities/Baitfish;m_6031_()I"),
            require = 1, allow = 1)
    private int bons$towCoo49SchoolCap(int original) {
        return TowSchools.cap(original);
    }
}
