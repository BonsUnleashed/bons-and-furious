package bons.furious.patch.ambientsounds;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Bons and Furious switch ambientsounds_dimension_patterns (AmbientSounds 6.3.8, client).
 *
 * AmbientDimension.is tests the level's dimension name against each configured name pattern with String.matches, which
 * compiles the regular expression on every call. String.matches(regex) is Pattern.matches(regex, input), i.e.
 * Pattern.compile(regex).matcher(input).matches(); the compiled Pattern is now kept per pattern string (Pattern objects
 * are immutable and thread-safe) and only the matcher is made per call. A pattern that fails to compile is never kept,
 * so it throws the same PatternSyntaxException on every call, as before.
 */
public final class DimensionPatterns {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.ambientDimensionPatterns=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ambientDimensionPatterns", "true"));
    private static final ConcurrentHashMap<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    private DimensionPatterns() {
    }

    /** In place of input.matches(regex) inside AmbientDimension.is. */
    public static boolean matches(String input, String regex) {
        if (!enabled) return input.matches(regex);
        Pattern p = COMPILED.get(regex);
        if (p == null) {
            p = Pattern.compile(regex);                  // throws exactly like String.matches; failures are not kept
            if (COMPILED.size() < 4096) COMPILED.putIfAbsent(regex, p);
        }
        return p.matcher(input).matches();
    }
}
