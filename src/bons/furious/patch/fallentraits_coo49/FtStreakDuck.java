package bons.furious.patch.fallentraits_coo49;

/**
 * fallentraits_unused_streak_reset_skip: what FtStreakStateMixin adds to LivingEntity: one byte of streak marks
 * (FtStreaks.GLOVE / HAND: the entity's own Rage Glove / Hand of Creation streak was asked for; FtStreaks.CACHE: its Fallen
 * Lib cache was made the way the shipped reset makes it).
 */
public interface FtStreakDuck {
    byte bons$fallentraitsCoo49Streak();

    void bons$fallentraitsCoo49SetStreak(byte marks);
}
