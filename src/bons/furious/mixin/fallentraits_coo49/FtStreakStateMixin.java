package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtStreakDuck;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * fallentraits_unused_streak_reset_skip (server side): one byte of streak marks per living entity for FtStreaks (which
 * relic streaks the entity's own getStack asked for, and whether its Fallen Lib cache was made). Adds no behaviour.
 */
@Mixin(value = LivingEntity.class, remap = false)
public abstract class FtStreakStateMixin implements FtStreakDuck {
    @Unique
    private byte bons$fallentraitsCoo49StreakMarks;

    @Override
    public byte bons$fallentraitsCoo49Streak() {
        return this.bons$fallentraitsCoo49StreakMarks;
    }

    @Override
    public void bons$fallentraitsCoo49SetStreak(byte marks) {
        this.bons$fallentraitsCoo49StreakMarks = marks;
    }
}
