package bons.furious.mixin.domesticationinnovation_coo49;

import bons.furious.patch.domesticationinnovation_coo49.TameTypes;
import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * domesticationinnovation_tame_type_memo (Domestication Innovation 1.7.1, both sides): the `instanceof ModifedToBeTameable`
 * test in TameableUtils.isTamed, couldBeTamed, getOwnerOf and getOwnerUUIDOf (one each) is answered from a per-class cache
 * (TameTypes): the same boolean for every object, without the interface scan a failing test costs. Everything else in
 * the four methods runs as shipped. COO 5.8 hooks isTamed (domesticationinnovation.MixinDITameableUtils, option
 * domesticationinnovation.skipImpossibleTamedCheck); a WrapOperation on one test composes with other injections.
 */
@Mixin(value = TameableUtils.class, remap = false)
public abstract class TameableUtilsTameTypeMixin {
    // the class constant ModifedToBeTameable also appears in CHECKCASTs: isTamed has four casts (the Axolotl, Fox, Rabbit
    // and Frog branches) before its one instanceof (ordinal 4); the other three methods start with their instanceof
    @WrapOperation(method = "isTamed", constant = @Constant(classValue = ModifedToBeTameable.class, ordinal = 4), require = 1, allow = 1)
    private static boolean bons$tameDuckTestIsTamed(Object entity, Operation<Boolean> original) {
        return TameTypes.isDuck(entity, original);
    }

    @WrapOperation(method = {"couldBeTamed", "getOwnerOf", "getOwnerUUIDOf"}, constant = @Constant(classValue = ModifedToBeTameable.class, ordinal = 0),
            require = 3, allow = 3)
    private static boolean bons$tameDuckTest(Object entity, Operation<Boolean> original) {
        return TameTypes.isDuck(entity, original);
    }
}
