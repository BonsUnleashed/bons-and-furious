package bons.furious.patch.cosmeticarmor_coo49;

import com.google.common.cache.ForwardingLoadingCache;
import com.google.common.cache.LoadingCache;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cosmeticarmor_restore_queue_field (Cosmetic Armor Reworked 1.21.1-v1, client). Cosmetic Armor
 * Reworked is under the Minecraft Mod Public License; none of its code is carried. Ours, idea from Collections Of
 * Optimizations (cosmeticarmor.perPlayerRestoreQueue), idea text only.
 *
 * What Cosmetic Armor does: its PlayerRenderHandler keeps one restore queue (an ArrayDeque of Runnables that put the real
 * armor back after a render) per player in a Guava LoadingCache with weak keys, and asks that cache for the queue in each
 * of its seven render handlers (pre and post player render, the cancelled pre render, first-person hand and arm and their
 * cancelled variants): 2 to 4 weak-key cache lookups per rendered player per frame, plus the hand and the arm. Every key is
 * a Player (the render event's player or Minecraft.player).
 *
 * What the switch does: when the render handler builds its cache (in its constructor, once), it gets this cache wrapped
 * ({@link #wrap}): getUnchecked(player) answers the queue kept in a field the switch adds to every Player
 * ({@link Holder}), created on the first ask exactly as the cache's loader creates it (a new ArrayDeque); any other key,
 * a null key and every other cache method go to Cosmetic Armor's own cache. The result is the same: one queue per player
 * object for that object's lifetime (the weak-key cache compares keys by identity, and the field lives and dies with the
 * player), created on first use; the queue's contents and their order are Cosmetic Armor's.
 *
 * Flag: -Dbons_and_furious.cosmeticarmorRestoreQueueField=false (read when the render handler is created) keeps
 * Cosmetic Armor's cache as it is.
 */
public final class RestoreQueues {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cosmeticarmorRestoreQueueField", "true"));
    private static volatile boolean announced;

    /** Added to Player by PlayerRestoreQueueMixin. */
    public interface Holder {
        Deque<Runnable> bons$cosmeticarmor_coo49Queue();

        void bons$cosmeticarmor_coo49SetQueue(Deque<Runnable> queue);
    }

    private RestoreQueues() {
    }

    /** Cosmetic Armor's freshly built restore-queue cache, wrapped when the switch is on (else returned as it is). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static LoadingCache wrap(LoadingCache original) {
        if (!enabled || original == null) return original;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cosmeticarmor_restore_queue_field applies (Cosmetic Armor's per-player restore queues kept on the player)");
        }
        return new FieldQueues(original);
    }

    /** The wrapper: player keys answered from the field, everything else by Cosmetic Armor's cache. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class FieldQueues extends ForwardingLoadingCache.SimpleForwardingLoadingCache {
        FieldQueues(LoadingCache delegate) {
            super(delegate);
        }

        @Override
        public Object getUnchecked(Object key) {
            if (key instanceof Holder h) {
                Deque<Runnable> q = h.bons$cosmeticarmor_coo49Queue();
                if (q == null) {
                    q = new ArrayDeque<>();
                    h.bons$cosmeticarmor_coo49SetQueue(q);
                }
                return q;
            }
            return super.getUnchecked(key);
        }
    }
}
