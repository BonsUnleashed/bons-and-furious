package bons.furious.patch.copycats_migration_c49;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch copycats_migration_fast_path (Copycats+ 3.0.9+mc.1.21.1, All Rights Reserved; both sides). Ours;
 * no Copycats+ code is carried (injectors with our own checks).
 *
 * What Copycats+ does. Its LevelChunkMixin passes every block entity a chunk sets (chunk load, placement) through
 * MigrationManager.migrateBlockEntity, which first evaluates migrationDisabled() (CCConfigs.safeGetter around a config
 * read), then reads the chunk's block state at the block entity's position, looks the block entity type's id up in the
 * registry, resolves the block's builtInRegistryHolder().key(), builds the id's string and compares it case-insensitively
 * with "create:copycat", and only then checks the block's namespace. migrateStructure (StructureTemplateMixin,
 * ContraptionMixin) does the same config read for every template block before two instanceof checks.
 *
 * What the switch does. migrateBlockEntity returns the block entity at once when its type is not the type the registry
 * holds under create:copycat (resolved once, by id, and checked back with getKey); migrateStructure returns the info at
 * once when its block is neither a MultiStateCopycatBlock nor a CCCopycatBlock. Null arguments and an unresolved type
 * keep Copycats+' own path.
 *
 * Why the result is identical. Registry ids are lower case, so the case-insensitive compare is true exactly for the id
 * create:copycat, and getKey(type) is that id exactly for the one registered type object under it: for every other type
 * (or an unregistered one, getKey null) Copycats+ returns the same block entity, whatever migrationDisabled() answers; the
 * skipped work (config read, block state read, registry lookup, holder key, string building) only reads. In
 * migrateStructure both conversions need one of the two block classes, so every other block returns the same info object.
 *
 * -Dbons_and_furious.copycatsMigrationFastPath=false switches it off at run time.
 */
public final class MigrationFastPath {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.copycatsMigrationFastPath", "true"));
    private static final ResourceLocation COPYCAT_ID = ResourceLocation.fromNamespaceAndPath("create", "copycat");
    private static volatile BlockEntityType<?> copycatType;
    private static volatile boolean announced;

    private MigrationFastPath() {}

    /** True when {@code type} is certainly not the create:copycat type (then migrateBlockEntity returns its argument). */
    public static boolean notCopycat(BlockEntityType<?> type) {
        BlockEntityType<?> c = copycatType;
        if (c == null) {
            c = resolve();
            if (c == null) return false;
        }
        return type != c;
    }

    private static BlockEntityType<?> resolve() {
        BlockEntityType<?> t = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(COPYCAT_ID);
        if (t == null || !COPYCAT_ID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(t))) return null;
        copycatType = t;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: copycats_migration_fast_path applies (Copycats+ migration checks skip block entities and template blocks that cannot need migrating)");
        }
        return t;
    }
}
