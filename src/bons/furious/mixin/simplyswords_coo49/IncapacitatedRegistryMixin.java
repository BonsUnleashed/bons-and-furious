package bons.furious.mixin.simplyswords_coo49;

import bons.furious.patch.simplyswords_coo49.IncapacitatedSnapshot;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.sweenus.simplyswords.api.IncapacitatingStatusEffectRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * simplyswords_incapacitated_lockfree (Simply Swords 1.70.2, both sides).
 *
 * isIncapacitated is replaced by a method WITHOUT the synchronized flag whose body is our own lock-free check
 * (IncapacitatedSnapshot); register republishes the snapshot at its RETURN, still inside its monitor. An injection
 * cannot do this: a static synchronized method enters the class monitor before its first instruction, and a
 * MixinExtras @WrapMethod keeps the flag on the outer method. No Simply Swords code is carried (the body only calls our
 * helper). Exact for every input; see IncapacitatedSnapshot for the proof. COO 5.8 also hooks this method
 * (simplyswords.MixinIncapacitatingStatusEffectRegistry, option simplyswords.fastIncapacitatedCheck): the compat table
 * makes this switch step aside while that option is on.
 */
@Mixin(value = IncapacitatingStatusEffectRegistry.class, remap = false)
public abstract class IncapacitatedRegistryMixin {
    @Shadow
    @Final
    private static Set<ResourceLocation> EFFECTS;

    /**
     * @author Bons and Furious (simplyswords_incapacitated_lockfree)
     * @reason the same answer without the class monitor: a snapshot of the registered ids published by register
     */
    @Overwrite
    public static boolean isIncapacitated(LivingEntity entity) {
        return IncapacitatedSnapshot.isIncapacitated(entity, EFFECTS);
    }

    @Inject(method = "register", at = @At("RETURN"))
    private static void bons$republishIncapacitating(ResourceLocation effectId, CallbackInfo ci) {
        IncapacitatedSnapshot.publish(EFFECTS);
    }
}
