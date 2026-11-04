package bons.furious.patch.tact_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch tact_compendium_unlock_single_save (TACT 1.3.0 / 1.5.3 with Alex's Caves 2.0.2, server).
 *
 * With TACT's unlockAllCompendiumInfo on, CompendiumUnlock.playerLoggedIn walks Alex's Caves' biomes and, for each one,
 * unlocks the next page of the cave book six times, calling CaveBookProgress.saveCaveBookProgress after every step: 36
 * times per login. Each save writes the whole book into the player's Citadel tag and sends that tag to every online
 * player. The book is one object kept in memory for the whole loop and nothing in the loop reads the saved tag, so only
 * the last of those 36 writes and broadcasts is the one that stays.
 *
 * Exact variant: inside playerLoggedIn the saves are held back; when the method ends (also by an exception) one save runs
 * with the same book and the same player, so the player's Citadel tag and the last tag every client receives are the
 * same as before; the 35 superseded intermediate writes and broadcasts are not done. With the option off (TACT's default)
 * the method saves nothing and nothing changes. Runtime flag -Dbons_and_furious.tactCompendiumUnlockSingleSave=false.
 * TACT is LGPL-3.0, Alex's Caves All Rights Reserved: no code of either is carried (the hooks call their own methods).
 */
public final class CompendiumSaves {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.tactCompendiumUnlockSingleSave", "true"));
    /** Saves Alex's Caves actually ran through these hooks, and saves held back (diagnostics; the harness reads them). */
    public static final AtomicLong SAVES = new AtomicLong();
    public static final AtomicLong HELD = new AtomicLong();
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();
    private static volatile boolean announced;

    private static final class Pending {
        Object progress, player;
        Operation<Void> save;
        int held;
    }

    private CompendiumSaves() {
    }

    /** Around playerLoggedIn: collect the saves of this call, then run the last one once. */
    public static void around(Object event, Operation<Void> original) {
        if (!enabled) {
            original.call(event);
            return;
        }
        Pending outer = PENDING.get();
        Pending mine = new Pending();
        PENDING.set(mine);
        try {
            original.call(event);
        } finally {
            if (outer == null) PENDING.remove();
            else PENDING.set(outer);
            if (mine.save != null) {
                SAVES.incrementAndGet();
                if (mine.held > 1 && !announced) announce(mine.held);
                mine.save.call(mine.progress, mine.player);
            }
        }
    }

    /** At each saveCaveBookProgress call inside playerLoggedIn: hold it (the switch on), or save now. */
    public static void save(Object progress, Object player, Operation<Void> save) {
        Pending p = enabled ? PENDING.get() : null;
        if (p == null) {
            SAVES.incrementAndGet();
            save.call(progress, player);
            return;
        }
        HELD.incrementAndGet();
        p.progress = progress;
        p.player = player;
        p.save = save;
        p.held++;
    }

    private static synchronized void announce(int held) {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: tact_compendium_unlock_single_save: TACT's compendium unlock at login saves the cave book once ({} saves were asked for)", held);
    }
}
