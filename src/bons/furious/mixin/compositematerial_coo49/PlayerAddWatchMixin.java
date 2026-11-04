package bons.furious.mixin.compositematerial_coo49;

import bons.furious.patch.compositematerial_coo49.HealScanGate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * compositematerial_heal_scan_player_gate (both sides; server levels): in Forge's
 * PersistentEntitySectionManager.addEntityWithoutEvent (the one way into a server level's entity storage), the one
 * startTracking call: every entity's pass tells HealScanGate the watch is in place; for a player the call is bracketed
 * by HealScanGate.trackingStart / trackingEnd (the player is in the storage before the level lists it at the end of its
 * tracking start; the gate steps aside meanwhile, and for good when the player is not listed afterwards or the call
 * throws). Every other entity: the call as shipped. A Redirect, no wrapper object on any entity's add. When another mod
 * redirects this call too, Mixin keeps one redirect, the one of higher priority: ours has priority 500, so another mod's
 * at the default 1000 (or above) wins and Mixin skips ours with a "@Redirect conflict" warning; require = 0 and
 * expect = 0, so the game starts, our handler never runs, and the gate stands aside (HealScanGate.watching was never
 * called).
 */
@Mixin(value = PersistentEntitySectionManager.class, remap = false, priority = 500)
public abstract class PlayerAddWatchMixin {
    @Shadow
    abstract void m_157575_(EntityAccess entity);

    @Redirect(method = "addEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;m_157575_(Lnet/minecraft/world/level/entity/EntityAccess;)V"),
            require = 0, expect = 0, allow = 1)
    private void bons$compositematerial_coo49Track(PersistentEntitySectionManager self, EntityAccess entity) {
        HealScanGate.watching();
        if (!(entity instanceof Player p)) {
            m_157575_(entity);
            return;
        }
        HealScanGate.trackingStart();
        boolean returned = false;
        try {
            m_157575_(entity);
            returned = true;
        } finally {
            HealScanGate.trackingEnd(p, returned);
        }
    }
}
