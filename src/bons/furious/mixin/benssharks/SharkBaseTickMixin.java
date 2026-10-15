package bons.furious.mixin.benssharks;

import bons.furious.patch.benssharks.Dispatch;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): head of baseTick of every Ben's living entity that declares it:
 * immediately after the point in LivingEntity.tick where Forge posts LivingTickEvent (LivingEntity.tick -> onLivingTick ->
 * Entity.tick -> baseTick). Same entities, same side, same moment in the tick as the listeners it replaces. Helper:
 * {@link Dispatch}.
 */
@Mixin(targets = {
        "net.mcreator.sharks.entity.AngelsharkEntity", "net.mcreator.sharks.entity.AxodileEntity",
        "net.mcreator.sharks.entity.BarracudaEntity", "net.mcreator.sharks.entity.BaskingSharkEntity",
        "net.mcreator.sharks.entity.BlacktipReefSharkEntity", "net.mcreator.sharks.entity.BlueSharkEntity",
        "net.mcreator.sharks.entity.BonnetheadSharkEntity", "net.mcreator.sharks.entity.BullSharkEntity",
        "net.mcreator.sharks.entity.CookiecutterSharkEntity", "net.mcreator.sharks.entity.GoblinSharkEntity",
        "net.mcreator.sharks.entity.GreenlandSharkEntity", "net.mcreator.sharks.entity.KrillEntity",
        "net.mcreator.sharks.entity.LandSharkEntity", "net.mcreator.sharks.entity.LemonSharkEntity",
        "net.mcreator.sharks.entity.LeopardSharkEntity", "net.mcreator.sharks.entity.MakoSharkEntity",
        "net.mcreator.sharks.entity.MegalodonEntity", "net.mcreator.sharks.entity.MegamouthSharkEntity",
        "net.mcreator.sharks.entity.NurseSharkEntity", "net.mcreator.sharks.entity.PilotFishEntity",
        "net.mcreator.sharks.entity.RemoraEntity", "net.mcreator.sharks.entity.SardineEntity",
        "net.mcreator.sharks.entity.SawsharkEntity", "net.mcreator.sharks.entity.SeaLionEntity",
        "net.mcreator.sharks.entity.SharkMinionEntity", "net.mcreator.sharks.entity.ShoalEntity",
        "net.mcreator.sharks.entity.ShrakEntity", "net.mcreator.sharks.entity.ThalassogerEntity",
        "net.mcreator.sharks.entity.ThresherSharkEntity", "net.mcreator.sharks.entity.TigerSharkEntity",
        "net.mcreator.sharks.entity.WhaleSharkEntity", "net.mcreator.sharks.entity.WhitetipSharkEntity"}, remap = false)
public abstract class SharkBaseTickMixin {
    @Inject(method = "m_6075_()V", at = @At("HEAD"), remap = false)
    private void bons$sharkTick(CallbackInfo ci) {
        Dispatch.sharkTick((LivingEntity) (Object) this);
    }
}
