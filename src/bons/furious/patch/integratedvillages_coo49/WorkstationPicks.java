package bons.furious.patch.integratedvillages_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch integratedvillages_workstation_pick_local (since 1.0.49; Integrated Villages 1.3.2+1.20.1-forge;
 * server side: structure processing during world generation). Helper of WorkstationPickLocalMixin; no Integrated Villages
 * code is carried.
 *
 * What the mod does. WorkstationProcessor.processBlock (m_7382_) swaps its placeholder block for a random workstation:
 * RandomSource.create() (a freshly, uniquely seeded generator), then it appends the candidate blocks of its workstation
 * type to the processor's own list field outputBlocksString (the "generic" type adds the composter, the blocks of the
 * loaded integration mods, and then removes the first composter again when the list holds two or more), and picks
 * list.get(random.nextInt(list.size())). The field is never cleared, so the list grows by the whole candidate set on every
 * placement for the life of the server (k placements: k copies of the same candidates, the generic removal scans it each
 * time), and one processor instance is shared by every world generation thread, which add to and read the same ArrayList
 * without a lock (lost adds, IndexOutOfBounds or ArrayIndexOutOfBounds thrown out of chunk generation).
 *
 * What the switch does. The mixin wraps the RandomSource.create() call (made once per placement, before any list access;
 * the call itself still runs, so the shared seed uniquifier advances as before) and every read of the outputBlocksString
 * field in processBlock: inside one placement every read answers this thread's own scratch list, emptied at the start of
 * the placement. The mod's own code then builds its candidate list exactly as on the processor's first placement and picks
 * from it with its own random source. Results: every candidate is picked with the same chance as before (the old list was
 * k equal copies of these candidates, so a uniform pick over it is a uniform pick over one copy); the specific candidate
 * was never reproducible (the generator is seeded uniquely, not from the world seed); the shared field stays empty (no
 * growth), and no list is shared between threads (no race). An unknown workstation type prints and throws as before (its
 * list is empty in both cases).
 *
 * -Dbons_and_furious.integratedvillagesWorkstationPickLocal=false (read at each placement) runs the original.
 */
public final class WorkstationPicks {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.integratedvillagesWorkstationPickLocal", "true"));
    private static volatile boolean announced;
    private static final ThreadLocal<Placement> PLACEMENT = ThreadLocal.withInitial(Placement::new);

    /** One thread's current placement: whether the switch acts for it, and its candidate list. */
    private static final class Placement {
        final ArrayList<String> list = new ArrayList<>();
        boolean active;
    }

    private WorkstationPicks() {
    }

    /** processBlock's RandomSource.create() (once per placement, before the candidate list is touched). */
    public static RandomSource begin(Operation<RandomSource> original) {
        Placement p = PLACEMENT.get();
        p.active = enabled;
        if (p.active) {
            p.list.clear();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: integratedvillages_workstation_pick_local applies (each workstation placement builds its candidates in its own list; the processor's shared list stays empty)");
            }
        }
        return original.call();
    }

    /** processBlock's reads of the outputBlocksString field: this placement's own list while the switch acts. */
    public static ArrayList<String> candidates(Object processor, Operation<ArrayList<String>> original) {
        Placement p = PLACEMENT.get();
        return p.active ? p.list : original.call(processor);
    }

    /** The candidate list of this thread's last placement (offline proof only). */
    public static List<String> lastCandidates() {
        return List.copyOf(PLACEMENT.get().list);
    }
}
