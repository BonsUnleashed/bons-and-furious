package bons.furious.mixin.artifacts;

import artifacts.equipment.EquipmentHelper;
import artifacts.neoforge.event.ArtifactHooksNeoForge;
import artifacts.registry.ModDataComponents;
import artifacts.registry.ModTags;
import bons.furious.patch.artifacts.ArtifactsTickOrder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * artifacts_living_tick_order, kitty slippers half (Artifacts; 1.21.1 tested build: Artifacts 13.2.5 for NeoForge 1.21.1,
 * MIT; both sides).
 *
 * ArtifactHooksNeoForge.onLivingUpdate (NeoForge EntityTickEvent.Post: every living entity, every tick, client and
 * server) calls onKittySlippersLivingUpdate. The body below is Artifacts', with the creeper tag test moved before
 * EquipmentHelper.hasAbilityActive(CREEPER_REPELLENT, lastHurtByMob, true), the scan of the attacker's armor and Curios
 * slots (see ArtifactsTickOrder for why the result is identical). With the runtime switch off the original order runs.
 *
 * Ported to 1.21.1: the 1.20.1 target was ArtifactEventsForge.onKittySlippersLivingUpdate(LivingTickEvent) with
 * KITTY_SLIPPERS.isEquippedBy(lastHurtByMob); Artifacts 13.2.5 checks lastHurtByMob != null first (kept first) and
 * scans for the CREEPER_REPELLENT data-component ability instead.
 */
@Mixin(value = ArtifactHooksNeoForge.class, remap = false)
public abstract class ArtifactHooksNeoForgeTickOrderMixin {
    /**
     * @author BonsUnleashed
     * @reason Test the creeper type before the kitty slippers equipment scan (artifacts_living_tick_order).
     */
    @Overwrite
    private static void onKittySlippersLivingUpdate(LivingEntity entity) {
        boolean clear;
        if (!ArtifactsTickOrder.reorder()) {
            clear = entity.getLastHurtByMob() != null
                    && EquipmentHelper.hasAbilityActive(ModDataComponents.CREEPER_REPELLENT.get(), entity.getLastHurtByMob(), true)
                    && entity.getType().is(ModTags.CREEPERS);
        } else {
            clear = entity.getLastHurtByMob() != null
                    && entity.getType().is(ModTags.CREEPERS)
                    && EquipmentHelper.hasAbilityActive(ModDataComponents.CREEPER_REPELLENT.get(), entity.getLastHurtByMob(), true);
            if (ArtifactsTickOrder.SHADOW) {
                clear = ArtifactsTickOrder.shadow("kitty slippers", clear, entity.getLastHurtByMob() != null
                        && EquipmentHelper.hasAbilityActive(ModDataComponents.CREEPER_REPELLENT.get(), entity.getLastHurtByMob(), true)
                        && entity.getType().is(ModTags.CREEPERS));
            }
        }
        if (clear) {
            entity.setLastHurtByMob(null);
        }
    }
}
