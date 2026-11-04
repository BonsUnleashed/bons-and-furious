package bons.furious.mixin.oddaccessories_coo49;

import bons.furious.patch.oddaccessories_coo49.OddCloakBarDuck;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * oddaccessories_cloak_action_bar_interval (server side): per player the Fiberglass Cloak indicator last sent and when, for
 * OddScans.cloakMessage. Adds no behaviour.
 */
@Mixin(value = Player.class, remap = false)
public abstract class OddCloakBarStateMixin implements OddCloakBarDuck {
    @Unique
    private byte bons$oddaccessoriesCoo49BarLastSite;
    @Unique
    private long bons$oddaccessoriesCoo49BarLastTick;

    @Override
    public byte bons$oddaccessoriesCoo49BarSite() {
        return this.bons$oddaccessoriesCoo49BarLastSite;
    }

    @Override
    public long bons$oddaccessoriesCoo49BarTick() {
        return this.bons$oddaccessoriesCoo49BarLastTick;
    }

    @Override
    public void bons$oddaccessoriesCoo49BarSent(byte site, long tick) {
        this.bons$oddaccessoriesCoo49BarLastSite = site;
        this.bons$oddaccessoriesCoo49BarLastTick = tick;
    }
}
