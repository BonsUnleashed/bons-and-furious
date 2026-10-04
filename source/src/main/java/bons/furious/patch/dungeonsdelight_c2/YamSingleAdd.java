package bons.furious.patch.dungeonsdelight_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix dungeonsdelight_yam_single_add (Dungeons Delight, AZURUNE licence; 1.21.1 tested build: Dungeon's
 * Delight 1.5.1 for NeoForge 1.21.1, neoforge-dungeonsdelight-1.21.1-1.5.1.jar; server). No Dungeons Delight code is
 * carried.
 *
 * When a Monster Yam summons its zombies, its tick calls level.addFreshEntity(zombie) and then, on Hard difficulty,
 * level.addFreshEntity(zombie) again for the SAME zombie. Once the first call has added it, the second can never succeed,
 * but it still does work before failing: PersistentEntitySectionManager.addEntity posts a second EntityJoinLevelEvent for
 * the zombie (every join listener of every mod runs again for an entity already in the world) and the add hooks of other
 * mods run again, and only then addEntityWithoutEvent's duplicate UUID check refuses it with a warning ("UUID of added
 * entity already exists"); if a listener removed the zombie in between, ServerLevel.addEntity refuses it with "Tried to
 * add entity ... marked as removed" instead. Either way it returns false.
 *
 * The fix skips the second call exactly when the first call returned true for the same zombie, and answers false, which
 * is what the skipped call returns (the tick discards the value anyway). When the first add was refused (a cancelled
 * join event, for example), the second call runs as before, so the zombie keeps its second chance. Below Hard difficulty
 * there is no second call, and nothing changes.
 *
 * Ported to 1.21.1: Dungeon's Delight 1.5.1 still adds each summoned zombie (now a ZombifiedDryadEntity, three per
 * summon) twice on Hard (javap: exactly two Level.addFreshEntity calls in MonsterYamEntity.tick). NeoForge 21.1 keeps the
 * order the argument rests on: ServerLevel.addFreshEntity -> addEntity (removed check) -> PersistentEntitySectionManager
 * .addNewEntity -> addEntity (posts EntityJoinLevelEvent first) -> addEntityWithoutEvent -> addEntityUuid.
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
                LOGGER.info("Bons and Furious: dungeonsdelight_yam_single_add: a Monster Yam's zombie is added once on Hard instead of twice (the second add only re-ran join events and logged a duplicate UUID)");
            }
            return false;
        }
        return original.call(level, entity);
    }
}
