package bons.furious.patch.colorwheel;

import dev.djefrey.colorwheel.engine.ClrwlRenderingPhase;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

/**
 * Labels of colorwheel_phase_labels: for each phase and shadow flag, the string Colorwheel's setPhase builds, computed
 * with the same expression the first time it is needed (benign race: two threads may both build the same string).
 */
public final class PhaseLabels {
    private static final String[][] LABELS = new String[ClrwlRenderingPhase.values().length][2];

    private PhaseLabels() {
    }

    public static String label(ClrwlRenderingPhase phase, boolean shadow) {
        String[] row = LABELS[phase.ordinal()];
        int i = shadow ? 1 : 0;
        String s = row[i];
        if (s == null) row[i] = s = "Clrwl " + (shadow ? "Shadow " : "") + StringUtils.capitalize(phase.name().toLowerCase(Locale.ROOT).replace("_", " "));
        return s;
    }
}
