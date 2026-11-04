package bons.furious.patch.biolith_restamp_c49;

import com.terraformersmc.biolith.impl.biome.InterfaceBiomeSource;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch biolith_swapped_source_restamp (Biolith 1.0.1-beta.1, server), a gameplay fix that is off by
 * default. Biolith is LGPL-3.0; no Biolith code is carried, its own setter is called. Ours, idea from Collections Of
 * Optimizations (biolith.restampSwappedBiomeSource), idea text only.
 *
 * What Biolith does: it learns which dimension a biome source belongs to only when a LevelStem is built (its
 * MixinDimensionOptions tags the generator's source with the stem's dimension type); every other source keeps
 * DIMENSION_TYPE_UNDEFINED, and Biolith places no biome replacement or sub-biome in a source it cannot place. Mods that
 * replace the overworld's biome source on ServerAboutToStart (MCreator's old overworld biome injector) hand the world a
 * source no LevelStem constructor ever saw, so with such a mod every Biolith placement silently stops.
 *
 * What the switch does (only when bons_and_furious.properties turns it on): at the start of MinecraftServer.createLevels
 * (after ServerAboutToStart, before any ServerLevel is built, so before Biolith's own world-start handling), every LevelStem
 * of the LEVEL_STEM registry whose generator's biome source still reports DIMENSION_TYPE_UNDEFINED is tagged with that
 * stem's dimension type through Biolith's own biolith$setDimensionType (registry order; a source shared by two stems keeps
 * the first stem's type). Sources Biolith already tagged are not touched. What the player can notice: in packs where a mod
 * swapped the biome source, Biolith's biome replacements and sub-biomes appear again in newly generated chunks; without
 * such a swap nothing changes.
 *
 * Flag: -Dbons_and_furious.biolithSwappedSourceRestamp=false leaves the sources as they are.
 */
public final class SourceRestamp {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.biolithSwappedSourceRestamp", "true"));
    private static volatile boolean announced;

    private SourceRestamp() {
    }

    /** Tags the untagged Biolith biome sources of the given stems; returns how many it tagged. */
    public static int restamp(Registry<LevelStem> stems) {
        if (!enabled || stems == null) return 0;
        int n = 0;
        for (LevelStem stem : stems) {
            BiomeSource source = stem.f_63976_().m_62218_();
            if (!(source instanceof InterfaceBiomeSource tagged)) continue;
            ResourceKey<?> now = tagged.biolith$getDimensionType();
            if (now == null || !now.m_135782_().equals(InterfaceBiomeSource.DIMENSION_TYPE_UNDEFINED.m_135782_())) continue;
            if (stem.f_63975_().m_203543_().isEmpty()) continue;     // no key to tag with (Biolith's own setter would do nothing)
            tagged.biolith$setDimensionType(stem.f_63975_());
            n++;
            // one INFO line when the switch first acts; later tags (other dimensions, later server starts) at DEBUG
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: biolith_swapped_source_restamp applies: the biome source of {} ({}) was swapped in after its LevelStem was built; tagged it as {} so Biolith places its biomes there",
                        stems.m_7981_(stem), source.getClass().getName(), stem.f_63975_().m_203543_().get().m_135782_());
            } else {
                LOGGER.debug("Bons and Furious: biolith_swapped_source_restamp: tagged the swapped-in biome source of {} ({}) as {}",
                        stems.m_7981_(stem), source.getClass().getName(), stem.f_63975_().m_203543_().get().m_135782_());
            }
        }
        return n;
    }
}