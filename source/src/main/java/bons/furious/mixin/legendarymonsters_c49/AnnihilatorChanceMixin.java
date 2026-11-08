package bons.furious.mixin.legendarymonsters_c49;

import bons.furious.patch.legendarymonsters_c49.AnnihilatorChance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_monsters_annihilator_proc_chance (Legendary Monsters 2.2.3 for Minecraft 1.21.1; server; since 1.0.49; gameplay
 * fix, off by default): the one RandomSource.nextInt() call of ForgeEvents.onLivingHurt (the Annihilator Leggings shockwave
 * roll, on NeoForge's LivingIncomingDamageEvent) is wrapped; the original draw happens once and AnnihilatorChance.draw maps
 * it so the roll's own test holds 25% of the time. Our own logic only.
 */
@Mixin(targets = "net.miauczel.legendary_monsters.event.ForgeEvents", remap = false)
public abstract class AnnihilatorChanceMixin {
    @WrapOperation(method = "onLivingHurt(Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt()I", remap = false), require = 1, allow = 1, remap = false)
    private static int bons$annihilatorRoll(RandomSource random, Operation<Integer> original) {
        return AnnihilatorChance.draw(random, original.call(random));
    }
}
