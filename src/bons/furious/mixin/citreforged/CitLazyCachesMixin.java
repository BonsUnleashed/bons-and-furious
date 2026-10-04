package bons.furious.mixin.citreforged;

import bons.furious.patch.citreforged.CitLazyCaches;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tomwmth.citreforged.cit.CITCache;
import dev.tomwmth.citreforged.defaults.cit.types.TypeArmor;
import dev.tomwmth.citreforged.defaults.cit.types.TypeElytra;
import dev.tomwmth.citreforged.defaults.cit.types.TypeEnchantment;
import dev.tomwmth.citreforged.defaults.cit.types.TypeItem;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * citreforged_lazy_stack_caches (CIT Reforged 1.0.2, MIT, client; tested build 1.0.2). Priority 1100: applied after CIT
 * Reforged's own ItemStack mixins (priority 1000), whose field initialisers and getters it wraps.
 *
 * In ItemStack's constructors, the two CITCache constructions CIT Reforged's initialisers make (CITCache.Single for the
 * item, armor and elytra types, CITCache.MultiList for the enchantment type) are skipped while the switch is on: CIT's
 * fields get null, after CIT's own argument (the bound getRealTimeCIT reference) is evaluated as before, so the type
 * containers are touched at the same moment. CIT's four getters return CIT's field when it is set (stacks built with the
 * switch off), else a cache of our own built on the first call, exactly as CIT's initialiser builds it.
 */
@Mixin(value = ItemStack.class, priority = 1100, remap = false)
public abstract class CitLazyCachesMixin {
    @Unique
    private CITCache.Single<TypeItem> bons$citItem;
    @Unique
    private CITCache.Single<TypeArmor> bons$citArmor;
    @Unique
    private CITCache.Single<TypeElytra> bons$citElytra;
    @Unique
    private CITCache.MultiList<TypeEnchantment> bons$citEnchantment;

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "<init>*", at = @At(value = "NEW", target = "dev/tomwmth/citreforged/cit/CITCache$Single"))
    private CITCache.Single bons$skipSingle(Function realtime, Operation<CITCache.Single> original) {
        return CitLazyCaches.enabled ? null : original.call(realtime);
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "<init>*", at = @At(value = "NEW", target = "dev/tomwmth/citreforged/cit/CITCache$MultiList"))
    private CITCache.MultiList bons$skipMultiList(Function realtime, Operation<CITCache.MultiList> original) {
        return CitLazyCaches.enabled ? null : original.call(realtime);
    }

    @WrapMethod(method = "citresewn$getCacheTypeItem")
    private CITCache.Single<TypeItem> bons$item(Operation<CITCache.Single<TypeItem>> original) {
        CITCache.Single<TypeItem> cit = original.call();
        if (cit != null) return cit;
        CITCache.Single<TypeItem> mine = this.bons$citItem;
        if (mine == null) {
            this.bons$citItem = mine = new CITCache.Single<>(TypeItem.CONTAINER::getRealTimeCIT);
            CitLazyCaches.createdItem++;
            CitLazyCaches.announce();
        }
        return mine;
    }

    @WrapMethod(method = "citresewn$getCacheTypeArmor")
    private CITCache.Single<TypeArmor> bons$armor(Operation<CITCache.Single<TypeArmor>> original) {
        CITCache.Single<TypeArmor> cit = original.call();
        if (cit != null) return cit;
        CITCache.Single<TypeArmor> mine = this.bons$citArmor;
        if (mine == null) {
            this.bons$citArmor = mine = new CITCache.Single<>(TypeArmor.CONTAINER::getRealTimeCIT);
            CitLazyCaches.createdArmor++;
            CitLazyCaches.announce();
        }
        return mine;
    }

    @WrapMethod(method = "citresewn$getCacheTypeElytra")
    private CITCache.Single<TypeElytra> bons$elytra(Operation<CITCache.Single<TypeElytra>> original) {
        CITCache.Single<TypeElytra> cit = original.call();
        if (cit != null) return cit;
        CITCache.Single<TypeElytra> mine = this.bons$citElytra;
        if (mine == null) {
            this.bons$citElytra = mine = new CITCache.Single<>(TypeElytra.CONTAINER::getRealTimeCIT);
            CitLazyCaches.createdElytra++;
            CitLazyCaches.announce();
        }
        return mine;
    }

    @WrapMethod(method = "citresewn$getCacheTypeEnchantment")
    private CITCache.MultiList<TypeEnchantment> bons$enchantment(Operation<CITCache.MultiList<TypeEnchantment>> original) {
        CITCache.MultiList<TypeEnchantment> cit = original.call();
        if (cit != null) return cit;
        CITCache.MultiList<TypeEnchantment> mine = this.bons$citEnchantment;
        if (mine == null) {
            this.bons$citEnchantment = mine = new CITCache.MultiList<>(TypeEnchantment.CONTAINER::getRealTimeCIT);
            CitLazyCaches.createdEnchantment++;
            CitLazyCaches.announce();
        }
        return mine;
    }
}
