package bons.furious.patch.number_prechecks;

/**
 * Bons and Furious, group number_prechecks (since 1.0.45): the pre-check shared by emf_token_number_precheck,
 * molang_number_precheck, azurelib_molang_number_precheck and textanimator_parse_prechecks.
 *
 * mustThrow(s) answers true only for strings that Float.parseFloat and Double.parseDouble reject with a
 * NumberFormatException. Both methods accept the same strings (the grammar of Double.valueOf: optional sign, then NaN,
 * Infinity, a decimal literal with optional exponent and [fFdD] suffix, or a hexadecimal literal with a mandatory binary
 * exponent; ASCII characters only; characters up to U+0020 ignored at both ends). This was read from the scanner of each
 * runtime the pack uses: JDK 21.0.12.1 (FloatingDecimal.readJavaFormatString after String.trim) and JDK 25.0.4.1 (the
 * rewritten scanner: skipWhitespaces over chars up to U+0020 at both ends, ASCII-only digit and letter tests). Every
 * accepted string meets both conditions below, so a string that fails either one is rejected:
 *
 *  1. after removing the characters up to U+0020 at both ends and one optional '+' or '-', a character follows and it is
 *     an ASCII digit, '.', 'N' (NaN) or 'I' (Infinity);
 *  2. every character of the trimmed string is one of  0-9 . + - a-f A-F x X p P N I n i t y  (digits, point, signs,
 *     exponent e/E, hex prefix x/X, hex digits, binary exponent p/P, suffix f/F/d/D, and the letters of NaN and Infinity).
 *
 * A string that passes both conditions is NOT known to parse: callers hand it to the real parser, which decides. null is
 * never passed here (the parsers throw NullPointerException for it, which the callers leave to them).
 *
 * The proof (1.0.45): every token of the pack's EMF models and GeckoLib/AzureLib animations, plus a fuzz corpus
 * of 10^6 strings (Unicode digits, every whitespace class, hex floats, NaN/Infinity spellings, suffixes, signs, random
 * ASCII), run through mustThrow and the real Float.parseFloat / Double.parseDouble on JDK 21 and JDK 25: whenever
 * mustThrow is true, both parsers threw (tools/mixedproof/NumberPrechecksGrammarProof.java).
 */
public final class FloatGrammar {
    /** Bit c (c < 64) of LOW, bit c - 64 (64 <= c < 128) of HIGH: the characters of condition 2. */
    private static final long LOW, HIGH;

    static {
        long low = 0, high = 0;
        for (char c : "0123456789.+-abcdefABCDEFxXpPNIinty".toCharArray()) {
            if (c < 64) low |= 1L << c;
            else high |= 1L << (c - 64);
        }
        LOW = low;
        HIGH = high;
    }

    private FloatGrammar() {
    }

    /** True only when Float.parseFloat(s) and Double.parseDouble(s) both throw NumberFormatException. s must not be null. */
    public static boolean mustThrow(String s) {
        return mustThrow(s, 0, s.length());
    }

    /** mustThrow for the substring s[from, to) without creating it. */
    public static boolean mustThrow(String s, int from, int to) {
        int end = to, start = from;
        while (start < end && s.charAt(start) <= ' ') start++;
        while (end > start && s.charAt(end - 1) <= ' ') end--;
        if (start == end) return true;                                    // empty after trimming: "empty String"
        int i = start;
        char c = s.charAt(i);
        if (c == '+' || c == '-') {
            if (++i == end) return true;                                  // a sign alone
            c = s.charAt(i);
        }
        if ((c < '0' || c > '9') && c != '.' && c != 'N' && c != 'I') return true;   // condition 1
        for (int k = start; k < end; k++) {                               // condition 2
            char ch = s.charAt(k);
            if (ch < 64 ? (LOW >>> ch & 1L) == 0 : ch >= 128 || (HIGH >>> (ch - 64) & 1L) == 0) return true;
        }
        return false;
    }
}
