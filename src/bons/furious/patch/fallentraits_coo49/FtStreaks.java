package bons.furious.patch.fallentraits_coo49;

import net.minecraft.world.entity.Entity;
import net.rtxyd.fallen.lib.runtime.forgemod.util.EntityCakyHandler;
import net.rtxyd.fallen.lib.util.IObjectCaky;

/**
 * Bons and Furious switch fallentraits_unused_streak_reset_skip (Fallen Traits & Relics 1.0.0 and Fallen Lib 1.4.6, both
 * LGPL-2.1-or-later; server side: hurt events). Ours. Idea: Collections Of Optimizations (fallentraits.skipUnusedStreakReset),
 * idea text only.
 *
 * The Rage Glove and the Hand of Creation keep their hit streak in the wearer's Fallen Lib cache (EntityCakyHandler.resolveWith,
 * keys ft.glove_stack / ft.hand_of_creation_stack, a fresh Stack when missing, reviewer 0). FTEvents.onHurt resets both for
 * every hurt living entity by asking the same keys with reviewer 1: Fallen Lib then makes the entity's cache (if it has none),
 * and puts or replaces a fresh Stack marked 1. A relic reads its streak only through its own getStack (reviewer 0), which
 * replaces any entry marked 1 with a fresh Stack again; so for an entity whose streak was never asked for, the reset only
 * builds records that nothing reads, on every first hurt of every mob.
 *
 * used() marks the entity when a relic's getStack asks for its streak (before Fallen Lib answers). reset() runs the shipped
 * reset for marked keys; for an unmarked key it leaves it out, and only the first time it does so for an entity it asks
 * Fallen Lib for one marker entry of ours with the reset's own cache type, so the entity's cache is made exactly when and how
 * the shipped reset would have made it (Fallen Lib fixes a cache's type on its first use). The relic's next getStack then
 * finds no entry and makes a fresh Stack marked 0, the same value it would have got by replacing the reset's record.
 *
 * Exact variant: the cache of an entity that never used either relic holds one marker entry instead of two fresh streak
 * records that nothing reads.
 *
 * Flag: -Dbons_and_furious.fallentraitsUnusedStreakResetSkip=false.
 */
public final class FtStreaks {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fallentraitsUnusedStreakResetSkip", "true"));
    public static final byte GLOVE = 1, HAND = 2, CACHE = 4;
    public static final String GLOVE_KEY = "ft.glove_stack", HAND_KEY = "ft.hand_of_creation_stack";
    /** Our marker entry in the entity's Fallen Lib cache (no other code asks for it). */
    public static final String MARKER_KEY = "bons_and_furious:fallentraits_streak_cache";
    private static final Object MARKER = new Object();
    private static final IObjectCaky.CakyLoader<Entity, Object> MARKER_LOADER = e -> MARKER;
    private static final IObjectCaky.CakyReviewer<Entity> MARKER_REVIEWER = e -> 0;

    private FtStreaks() {
    }

    static byte bit(String key) {
        return GLOVE_KEY.equals(key) ? GLOVE : HAND_KEY.equals(key) ? HAND : 0;
    }

    /** A relic's getStack asks for the entity's streak under key. */
    public static void used(Entity entity, String key) {
        byte b = bit(key);
        if (b != 0 && entity instanceof FtStreakDuck d) {
            byte s = d.bons$fallentraitsCoo49Streak();
            if ((s & b) == 0) d.bons$fallentraitsCoo49SetStreak((byte) (s | b));
        }
    }

    /** The redirected EntityCakyHandler.resolveWith calls of FTEvents.onHurt (their answer is dropped). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object reset(Entity entity, String key, IObjectCaky.Type type, IObjectCaky.CakyLoader loader, IObjectCaky.CakyReviewer reviewer) {
        byte b;
        if (!enabled || !(entity instanceof FtStreakDuck d) || (b = bit(key)) == 0) return EntityCakyHandler.resolveWith(entity, key, type, loader, reviewer);
        byte s = d.bons$fallentraitsCoo49Streak();
        if ((s & b) != 0) return EntityCakyHandler.resolveWith(entity, key, type, loader, reviewer);
        if ((s & CACHE) == 0) {
            EntityCakyHandler.resolveWith(entity, MARKER_KEY, type, MARKER_LOADER, MARKER_REVIEWER);
            d.bons$fallentraitsCoo49SetStreak((byte) (s | CACHE));
        }
        return null;
    }
}
