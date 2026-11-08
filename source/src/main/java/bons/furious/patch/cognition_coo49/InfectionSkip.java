package bons.furious.patch.cognition_coo49;

import net.minecraft.world.level.Level;

/**
 * Bons and Furious switch cognition_client_infection_skip (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for
 * NeoForge 1.21.1; exact variant, client; helper of InfectAdjacentClientMixin; no Cognition code carried).
 *
 * What Cognition does. Every infected bookshelf and every agar block ticks on both logical sides (AbstractAgarEntity.tick,
 * AbstractInfectedBookshelfEntity.tick); once a second (times its infectivity) it calls
 * AbstractInfectiveEntity.infectAdjacent(level, pos), which reads the six neighbours' block states, asks
 * InfectingRecipe.getInfectedBlockState for each (a copy of the infecting recipe list, a recipe input and an item stack) and,
 * when some neighbour can be infected, draws Math.random() to pick one and hands it to BibliophageItem.infectBlock, whose
 * whole body is "if (!level.isClientSide) ...". On a client level the method therefore only reads and changes nothing.
 *
 * What the switch does. infectAdjacent returns at once on a client level. Exact variant, one documented difference: the
 * client no longer draws that one Math.random() value (the JVM-wide unseeded generator; nothing reproducible depends on it).
 * Runtime flag bons_and_furious.cognitionClientInfectionSkip.
 */
public final class InfectionSkip {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cognitionClientInfectionSkip", "true"));

    private InfectionSkip() {
    }

    /** True when infectAdjacent(level, pos) may return at once (a client level). */
    public static boolean skip(Level level) {
        return enabled && level != null && level.isClientSide;
    }
}
