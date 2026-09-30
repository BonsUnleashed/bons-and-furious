package bons.furious.patch.oculus;

import java.util.Locale;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import org.apache.commons.lang3.StringUtils;

/**
 * The GL debug-group label Oculus gives each world rendering phase, built by the same expression Oculus uses in
 * IrisRenderingPipeline.setPhase, once per phase instead of on every phase change.
 */
public final class PhaseLabels {
    private static volatile String[] labels;

    private PhaseLabels() {}

    public static String of(WorldRenderingPhase phase) {
        String[] table = labels;
        if (table == null) {
            WorldRenderingPhase[] phases = WorldRenderingPhase.values();
            table = new String[phases.length];
            for (WorldRenderingPhase p : phases) {
                table[p.ordinal()] = StringUtils.capitalize(p.name().toLowerCase(Locale.ROOT).replace("_", " "));
            }
            labels = table;
        }
        return table[phase.ordinal()];
    }
}
