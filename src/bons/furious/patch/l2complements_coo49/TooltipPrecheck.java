package bons.furious.patch.l2complements_coo49;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2complements_tooltip_line_precheck (L2 Complements 2.6.1, client). L2 Complements is All Rights
 * Reserved: none of its code is carried. Ours, idea from Collections Of Optimizations (l2complements.skipUnrelatedTooltips),
 * idea text only.
 *
 * What L2 Complements does: ClientEventHandler.modifyItemTooltip runs (lowest priority) for every tooltip built for an
 * enchanted item or an enchanted book (every hovered frame, every JEI panel line): it reads the stack's whole enchantment
 * map, the Alt key state, wraps every tooltip line in an Either and makes a literal or empty component per line, and only
 * then looks for its own lines: a line whose text is a translation key starting "enchantment.l2complements." and ending
 * ".desc" (directly, or as the only sibling of a literal line). Only such a line, if its id names one of L2 Complements'
 * custom-description enchantments, is replaced; when none is replaced the tooltip is left exactly as it was.
 *
 * What the switch does (TooltipLinePrecheckMixin, a @Redirect of each of the two calls of the handler's first test): when a
 * call answers true (enchanted, or an enchanted book), the handler's own line test, without the registry lookup (so it
 * marks a superset of the lines the handler could replace), runs over the same lines; when no line passes it, nothing in
 * the handler could change the tooltip, so the test answers false and the handler returns at once. The work left out is
 * reads (the enchantment map: EnchantmentHelper.getEnchantments, the key state) and throwaway objects. Tooltips of other
 * items return as shipped. When a line passes, the handler runs on as shipped. Keyed on the tooltip lines, so desc lines
 * added for enchantments the stack does not carry (other tooltip mods) are handled as before.
 *
 * Flag: -Dbons_and_furious.l2complementsTooltipLinePrecheck=false runs the handler for every tooltip.
 */
public final class TooltipPrecheck {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2complementsTooltipLinePrecheck", "true"));
    private static final String PREFIX = "enchantment.l2complements.";
    private static final String SUFFIX = ".desc";
    /** Handler runs ended at the precheck (for probes and the harness). */
    public static long skipped;
    private static volatile boolean announced;

    private TooltipPrecheck() {
    }

    /** False when the switch is on and the shipped handler cannot change this tooltip (its first test then answers false). */
    public static boolean mayChange(ItemTooltipEvent event) {
        return !enabled || !nothingToChange(event);
    }

    /** True when the shipped handler (past its first test) cannot change this tooltip: no line can be one it replaces. */
    public static boolean nothingToChange(ItemTooltipEvent event) {
        List<Component> lines = event.getToolTip();
        for (int i = 0, n = lines.size(); i < n; i++) {
            Component line = lines.get(i);
            ComponentContents contents = line.m_214077_();
            if (contents instanceof LiteralContents) {
                List<Component> siblings = line.m_7360_();
                if (siblings.size() != 1) continue;
                contents = siblings.get(0).m_214077_();
            }
            if (contents instanceof TranslatableContents tr && candidate(tr.m_237508_())) return false;
        }
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2complements_tooltip_line_precheck applies (L2 Complements reads an enchanted item's tooltip only when it has its own description lines)");
        }
        return true;
    }

    /** The handler's own key test (starts with PREFIX, ends with SUFFIX: at least 30 characters, as the two share their
     *  dot), with character 12 of PREFIX ('l') checked first so other mods' keys are turned away at once. */
    static boolean candidate(String key) {
        return key.length() >= 30 && key.charAt(12) == 'l' && key.startsWith(PREFIX) && key.endsWith(SUFFIX);
    }
}
