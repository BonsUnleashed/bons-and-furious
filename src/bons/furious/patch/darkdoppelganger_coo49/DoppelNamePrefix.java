package bons.furious.patch.darkdoppelganger_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch darkdoppelganger_boss_bar_prefix_check (Dark Doppelganger 9.9.0, All Rights Reserved; client).
 * Ours; no Dark Doppelganger code is carried. Idea: Collections Of Optimizations (darkdoppelganger.leanBossBarNameCheck),
 * idea text only; recomputing the answer every frame (no per-component memo) is ours.
 *
 * Dark Doppelganger listens to Forge's boss bar customize event, which fires once per boss bar per frame for every boss bar
 * on screen (the dragon's, the wither's, raids', every mod's), and asks name.getString().startsWith("Dark Doppelganger"):
 * getString walks the whole name, resolving translations, and builds a new string every frame.
 *
 * What changes: for a name that is a plain vanilla component tree (MutableComponent objects whose contents are literal
 * text, a translation (arguments: such components, strings, boxed numbers, booleans, characters or null), a key binding
 * name or the empty contents), the same visit getString makes is run with a consumer that compares the characters with
 * "Dark Doppelganger" as they come and stops at the first difference or after the 17th equal character; the call then
 * answers "Dark Doppelganger" (the name starts with it) or "" (it does not), the only thing the caller asks. Why identical:
 * getString is the concatenation of exactly the pieces this visit sees, in the same order (the same visit methods), so
 * "starts with the prefix" is decided by the same characters; the pieces after the stop are not visited, which for these
 * contents only leaves lazy caches (a translation's decomposition, a key binding's name) to be filled at their next use.
 * Anything else (another Component class, other contents): the original call. Language changes are seen at once (nothing
 * is kept between calls). Flag: -Dbons_and_furious.darkdoppelgangerBossBarPrefixCheck=false.
 */
public final class DoppelNamePrefix {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.darkdoppelgangerBossBarPrefixCheck", "true"));
    /** The prefix Dark Doppelganger tests for (the caller's own constant, same content). */
    public static final String PREFIX = "Dark Doppelganger";
    private static final Optional<Boolean> MATCH = Optional.of(Boolean.TRUE), DIFFERS = Optional.of(Boolean.FALSE);
    private static volatile boolean announced;

    private DoppelNamePrefix() {
    }

    /** The string the caller gets: PREFIX when the name starts with it, "" when not; the original call for other trees. */
    public static String answer(Component name, Operation<String> original) {
        if (!enabled || name == null || !plain(name, 0)) return original.call(name);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: darkdoppelganger_boss_bar_prefix_check applies (boss bar names are compared as they are visited)");
        }
        return startsWithPrefix(name) ? PREFIX : "";
    }

    /** The prefix test by an early-stopping visit (public for the harness). */
    public static boolean startsWithPrefix(Component name) {
        Optional<Boolean> r = name.m_5651_(new Prefix());
        return r.isPresent() && r.get();
    }

    private static final class Prefix implements FormattedText.ContentConsumer<Boolean> {
        private int pos;

        @Override
        public Optional<Boolean> m_130809_(String piece) {
            for (int i = 0, n = piece.length(); i < n; i++) {
                if (piece.charAt(i) != PREFIX.charAt(this.pos)) return DIFFERS;
                if (++this.pos == PREFIX.length()) return MATCH;
            }
            return Optional.empty();
        }
    }

    /** A tree whose visit is vanilla's for vanilla contents (see the class comment). */
    public static boolean plain(Component c, int depth) {
        if (depth > 64 || c == null || c.getClass() != MutableComponent.class) return false;
        ComponentContents k = c.m_214077_();
        if (k.getClass() == TranslatableContents.class) {
            for (Object a : ((TranslatableContents) k).m_237523_()) {
                if (a == null) continue;
                if (a instanceof Component ac) {
                    if (!plain(ac, depth + 1)) return false;
                    continue;
                }
                Class<?> ac = a.getClass();
                if (ac != String.class && ac != Integer.class && ac != Long.class && ac != Short.class && ac != Byte.class
                        && ac != Double.class && ac != Float.class && ac != Boolean.class && ac != Character.class) return false;
            }
        } else if (k != ComponentContents.f_237124_ && k.getClass() != LiteralContents.class && k.getClass() != KeybindContents.class) {
            return false;
        }
        List<Component> siblings = c.m_7360_();
        for (int i = 0, n = siblings.size(); i < n; i++) if (!plain(siblings.get(i), depth + 1)) return false;
        return true;
    }
}
