package bons.furious.patch.jei_startup;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import mezz.jei.common.util.Translator;
import net.minecraft.network.chat.FormattedText;

/** Tooltip callbacks, component flattening and JEI's locale supplier all stay on the calling thread.
 * After that, two small scans replace a regex matcher per line (ChatFormatting.stripFormatting) and a regex split
 * (JEI 19.51's ListElementInfo.addSplitStrings: trim, then split on runs of \s, empty pieces skipped).
 * No tooltip cache survives a call: language, data components and dynamic mod tooltips are always read afresh.
 *
 * Minecraft 1.21.1 port: JEI 19.51 splits words differently from the JEI 15 this switch was written for (15 kept
 * split(" ")'s leading and inner empty words; 19.51 trims and splits on whitespace runs), so addWords follows 19.51.
 * The switch's guards pin both of JEI's methods and the pattern made in ListElementInfo's static initializer.
 */
public final class TooltipWords {
    public static volatile boolean enabled = !Boolean.getBoolean("bons_and_furious.jeiTooltipWords.off");
    public static final LongAdder CALLS = new LongAdder(), LINES = new LongAdder();
    private TooltipWords() {}

    // 1.0.34: each line is read as FormattedText, the type JEI's getStrings casts it to (checkcast FormattedText, then
    // FormattedText.getString); a Component cast refused a plain FormattedText line that JEI itself accepts
    public static Set<String> read(List<? extends FormattedText> components) {
        Set<String> words = new HashSet<>();
        for (FormattedText component : components) {
            addWords(words, Translator.toLowercaseWithLocale(strip(component.getString())));
            LINES.increment();
        }
        CALLS.increment();
        return words;
    }

    /** ChatFormatting.stripFormatting: every "§" followed by a format code (0-9, a-f, k-o, r, either case) removed. */
    public static String strip(String text) {
        if (text == null) return "";
        int first = -1;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '§' && formatCode(text.charAt(i + 1))) { first = i; break; }
        }
        if (first < 0) return text;
        StringBuilder result = new StringBuilder(text.length());
        result.append(text, 0, first);
        for (int i = first; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length() && formatCode(text.charAt(i + 1))) i++;
            else result.append(c);
        }
        return result.toString();
    }

    private static boolean formatCode(char c) {
        if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'k' && c <= 'o' || c == 'r';
    }

    /**
     * JEI 19.51's addSplitStrings: String.trim() (every char up to U+0020 at both ends), nothing for an empty result,
     * otherwise every maximal run of characters that are not regex \s (space, tab, line feed, vertical tab, form feed,
     * carriage return). After trim the text neither starts nor ends with such a character, so Pattern.split yields no
     * empty piece either.
     */
    public static void addWords(Set<String> out, String line) {
        String s = line.trim();
        if (s.isEmpty()) return;
        int start = -1;
        for (int i = 0; i < s.length(); i++) {
            if (space(s.charAt(i))) {
                if (start >= 0) { out.add(s.substring(start, i)); start = -1; }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) out.add(s.substring(start));
    }

    private static boolean space(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }
}
