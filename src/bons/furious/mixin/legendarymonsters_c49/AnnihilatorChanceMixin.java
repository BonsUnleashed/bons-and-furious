package bons.furious.mixin.legendarymonsters_c49;

import bons.furious.patch.legendarymonsters_c49.AnnihilatorChance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_monsters_annihilator_proc_chance (Legendary Monsters 2.2.2 / 2.2.4; server; since 1.0.49; gameplay fix, off by
 * default): the one RandomSource.nextInt() call of ForgeEvents.onLivingHurt (the Annihilator Leggings shockwave roll) is
 * wrapped; the original draw happens once and AnnihilatorChance.draw maps it so the roll's own test holds 25% of the time.
 * Our own logic only.
 */
@Mixin(targets = "net.miauczel.legendary_monsters.event.ForgeEvents", remap = false)
public abstract class AnnihilatorChanceMixin {
    @WrapOperation(method = "onLivingHurt(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;m_188502_()I", remap = false), require = 1, allow = 1, remap = false)
    private static int bons$annihilatorRoll(RandomSource random, Operation<Integer> original) {
        return AnnihilatorChance.draw(random, original.call(random));
    }
}
