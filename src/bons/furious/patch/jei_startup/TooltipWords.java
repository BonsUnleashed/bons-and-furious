package bons.furious.patch.jei_startup;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import mezz.jei.common.util.Translator;
import net.minecraft.network.chat.Component;

/** Tooltip callbacks, component flattening and JEI's locale supplier all stay on the calling thread.
 * After that, two small scans replace a regex matcher and a temporary split array. HashSet insertion
 * order and Java split(" ")'s leading/inner empty words and discarded trailing words are preserved.
 * No tooltip cache survives a call: language, NBT and dynamic mod tooltips are always read afresh.
 */
public final class TooltipWords {
    public static volatile boolean enabled = !Boolean.getBoolean("bons_and_furious.jeiTooltipWords.off");
    public static final LongAdder CALLS = new LongAdder(), LINES = new LongAdder();
    private TooltipWords() {}

    public static Set<String> read(List<Component> components) {
        Set<String> words = new HashSet<>();
        for (Component component : components) {
            addWords(words, Translator.toLowercaseWithLocale(strip(component.getString())));
            LINES.increment();
        }
        CALLS.increment();
        return words;
    }

    public static String strip(String text) {
        if (text == null) return "";
        int first = -1;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '\u00a7' && formatCode(text.charAt(i + 1))) { first = i; break; }
        }
        if (first < 0) return text;
        StringBuilder result = new StringBuilder(text.length());
        result.append(text, 0, first);
        for (int i = first; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length() && formatCode(text.charAt(i + 1))) i++;
            else result.append(c);
        }
        return result.toString();
    }

    private static boolean formatCode(char c) {
        if (c >= 'A' && c <= 'Z') c += 'a' - 'A';
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'k' && c <= 'o' || c == 'r';
    }

    public static void addWords(Set<String> out, String line) {
        if (line.isEmpty()) { out.add(""); return; }
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') end--;
        int start = 0;
        for (int i = 0; i < end; i++) {
            if (line.charAt(i) == ' ') { out.add(line.substring(start, i)); start = i + 1; }
        }
        if (end > 0) out.add(line.substring(start, end));
    }
}
