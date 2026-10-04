package bons.furious.patch.dungeonsdelight_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix dungeonsdelight_yam_single_add (Dungeons Delight 1.2.10, AZURUNE licence; server). No Dungeons
 * Delight code is carried.
 *
 * When a Monster Yam summons rotten zombies, its tick calls level.addFreshEntity(zombie) and then, on Hard difficulty,
 * level.addFreshEntity(zombie) again for the SAME zombie. Once the first call has added it, the second can never succeed,
 * but on Forge 47.4.16 it still does work before failing: PersistentEntitySectionManager.addEntity posts a second
 * EntityJoinLevelEvent for the zombie (every join listener of every mod runs again for an entity already in the world)
 * and the add hooks of other mods run again (in this pack Cupboard's, Radium's and Valkyrien Skies'), and only then the
 * duplicate UUID check refuses it with a warning ("UUID of added entity already exists"); if a listener removed the zombie
 * in between, the call refuses it with "Tried to add entity ... marked as removed" instead. Either way it returns false.
 *
 * The fix skips the second call exactly when the first call returned true for the same zombie, and answers false, which
 * is what the skipped call returns (the tick discards the value anyway). When the first add was refused (a cancelled
 * join event, for example), the second call runs as before, so the zombie keeps its second chance. Below Hard difficulty
 * there is no second call, and nothing changes.
 *
 * Runtime flag: -Dbons_and_furious.dungeonsDelightYamSingleAdd=false keeps the double add.
 */
public final class YamSingleAdd {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dungeonsDelightYamSingleAdd", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Second adds skipped (for probes and the harness). */
    public static volatile long skipped;

    private YamSingleAdd() {
    }

    /** The first addFreshEntity(zombie) of the summon loop: runs as before and remembers a successful add. */
    public static boolean first(Level level, Entity entity, Operation<Boolean> original, LocalRef<Entity> added) {
        boolean ok = original.call(level, entity);
        added.set(ok ? entity : null);
        return ok;
    }

    /** The second addFreshEntity(zombie) (Hard only): skipped when the first one added this zombie. */
    public static boolean second(Level level, Entity entity, Operation<Boolean> original, LocalRef<Entity> added) {
        if (enabled && entity != null && added.get() == entity) {
            added.set(null);
            skipped++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: dungeonsdelight_yam_single_add: a Monster Yam's rotten zombie is added once on Hard instead of twice (the second add only re-ran join events and logged a duplicate UUID)");
            }
            return false;
        }
        return original.call(level, entity);
    }
}
