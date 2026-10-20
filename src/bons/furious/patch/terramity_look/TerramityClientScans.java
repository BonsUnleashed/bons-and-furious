package bons.furious.patch.terramity_look;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * Bons and Furious switch terramity_client_scan_skip (since 1.0.45; Terramity 0.9.8; client side - the code also runs on
 * the server, where it changes nothing). SRG names for Minecraft members.
 *
 * What Terramity does. Four accessory tick procedures run for their wearers on both sides:
 *  - ConductiteScouterHelmetTickEventProcedure (every 10th tick) and VoidGlassesWhileBaubleIsEquippedTickProcedure (every
 *    8th tick) write an NBT "timer" on the worn stack every tick, then on the firing tick run 64 steps, each three identical
 *    level.clip calls (up to 64 blocks from the eyes) and an entity query with a distance sort; the only effects of the
 *    steps (a health message to the wearer, Glowing on the entity looked at) are guarded by !level().isClientSide();
 *  - MalwareWhileBaubleIsEquippedTickProcedure and PhantomAmuletEquippedTickProcedure query every entity within 15 blocks,
 *    sort them, and give each living one an effect, again only when !level().isClientSide().
 *
 * What the switch does. On a client level the 64-step loop of the first two runs zero times (the loop bound reads 0;
 * the timer writes before and after it are unchanged), and the entity query of the other two returns a new empty
 * ArrayList, so their loops do nothing. Identical: on the client every skipped step only reads - clip and the entity
 * query read the client level (its chunk source answers from its chunk array; the three other mods that hook its getChunk
 * in this pack, SecurityCraft, Ars Nouveau and Valkyrien Skies, only look chunks up in their own storages), the sort and
 * the distance tests are pure, and every effect is behind the client-side test. On a server level nothing changes.
 *
 * -Dbons_and_furious.terramityClientScanSkip=false runs the procedures as shipped. Terramity is All Rights Reserved: the
 * mixins hold only our own logic.
 */
public final class TerramityClientScans {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.terramityClientScanSkip", "true"));
    private static volatile boolean announced;

    private TerramityClientScans() {
    }

    /** The 64-step look scan's loop bound: 0 on a client level (the wearer's level is the client one too), as shipped otherwise. */
    public static int lookSteps(int steps, LevelAccessor world, Entity entity) {
        if (enabled && world.m_5776_() && entity != null && entity.m_9236_().m_5776_()) {
            announce();
            return 0;
        }
        return steps;
    }

    /** The aura procedures' entity query: a new empty list on a client level, the query otherwise. */
    public static List<Entity> auraTargets(LevelAccessor world, Class<Entity> type, AABB box, Predicate<? super Entity> test, Operation<List<Entity>> original) {
        if (enabled && world.m_5776_()) {
            announce();
            return new ArrayList<>();
        }
        return original.call(world, type, box, test);
    }

    private static void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: terramity_client_scan_skip applies (Terramity's look and aura accessory scans skip their client-side work, which has no effect there)");
    }
}
