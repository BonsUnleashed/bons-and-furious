package bons.furious.mixin.enigmaticdelicacy_coo49;

import bons.furious.patch.enigmaticdelicacy_coo49.SlicingPeek;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticdelicacy_unset_persistent_data_skip (Enigmatic Delicacy 1.0.1, both sides; All Rights Reserved: no code of the
 * mod is carried). EnigmaticDelightEventHandler.onTick reads getPersistentData().getInt("Slicing") for every living entity
 * on every tick and acts only on a positive value. MixinExtras @WrapOperation on that getPersistentData() call
 * (SlicingPeek.readOnly: the entity's compound, or one shared empty compound when it has none, without creating one) and on
 * the getInt call (0 at once for the shared empty compound).
 */
@Mixin(targets = "auviotre.enigmatic.delicacy.handlers.EnigmaticDelightEventHandler", remap = false)
public abstract class EdSlicingPeekMixin {
    @WrapOperation(method = "onTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getPersistentData()Lnet/minecraft/nbt/CompoundTag;"),
            require = 1, allow = 1)
    private CompoundTag bons$enigmaticdelicacyCoo49Data(LivingEntity entity, Operation<CompoundTag> original) {
        return SlicingPeek.readOnly(entity, original);
    }

    @WrapOperation(method = "onTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;m_128451_(Ljava/lang/String;)I"),
            require = 1, allow = 1)
    private int bons$enigmaticdelicacyCoo49Slicing(CompoundTag tag, String key, Operation<Integer> original) {
        return SlicingPeek.isShared(tag) ? 0 : original.call(tag, key);
    }
}
