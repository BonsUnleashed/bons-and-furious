package bons.furious.mixin.skarriermobs_coo49;

import bons.furious.patch.skarriermobs_coo49.SkarrierScans;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * skarriermobs_resisteel_set_lean (Skarrier Mobs 1.1.2 and 1.0.6, both sides; trade-off, off by default): a cancellable
 * @Inject before the first LivingEntity.getItemBySlot call of ResisteelArmorEquippedEffectProcedure.execute(Event, Entity),
 * which is where the handler's body starts for a living entity (in 1.1.2 after its every-10th-tick test). The body is left
 * out when SkarrierScans.resisteelUnused says it would only write five zero Resisteel keys and remove two absent effects
 * (no Resisteel piece worn, neither effect active, every Resisteel key absent or a plain 0.0). What differs: those entities
 * keep no zero Resisteel keys (or no ForgeData compound at all) in their saved data, and no MobEffectEvent.Remove is posted
 * for the two effects they do not have; every reader of the keys reads 0.0 either way.
 */
@Mixin(targets = "net.mcreator.skarriermobs.procedures.ResisteelArmorEquippedEffectProcedure", remap = false)
public abstract class ResisteelSetLeanMixin {
    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_6844_(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", ordinal = 0),
            cancellable = true, require = 1, allow = 1)
    private static void bons$skarriermobsCoo49ResisteelSkip(Event event, Entity entity, CallbackInfo ci) {
        if (SkarrierScans.resisteelUnused(entity)) ci.cancel();
    }
}
