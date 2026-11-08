package bons.furious.mixin.cosmeticarmor_coo49;

import bons.furious.patch.cosmeticarmor_coo49.RestoreQueues;
import java.util.Deque;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * cosmeticarmor_restore_queue_field (Cosmetic Armor Reworked 1.21.1-v1, client), part 1 of 2: a field on Player that holds
 * Cosmetic Armor's restore queue for that player (created on first use by RestoreQueues, read and written only by Cosmetic
 * Armor's render handlers on the render thread). Nothing else of Player changes.
 */
@Mixin(value = Player.class, remap = false)
public abstract class PlayerRestoreQueueMixin implements RestoreQueues.Holder {
    @Unique
    private Deque<Runnable> bons$cosmeticarmor_coo49Deque;

    @Override
    public Deque<Runnable> bons$cosmeticarmor_coo49Queue() {
        return this.bons$cosmeticarmor_coo49Deque;
    }

    @Override
    public void bons$cosmeticarmor_coo49SetQueue(Deque<Runnable> queue) {
        this.bons$cosmeticarmor_coo49Deque = queue;
    }
}
