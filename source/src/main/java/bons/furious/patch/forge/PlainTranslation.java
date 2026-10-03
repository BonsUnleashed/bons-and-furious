package bons.furious.patch.forge;

/** Runtime switch of forge_plain_translation_format (the config switch acts when classes are transformed). */
public final class PlainTranslation {
    /** -Dbons_and_furious.plainTranslation=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.plainTranslation", "true"));

    private PlainTranslation() {
    }

    /** True when MessageFormat would copy the template unchanged: no format element, no quoting. */
    public static boolean plain(String format) {
        return enabled && format != null && format.indexOf('{') < 0 && format.indexOf('}') < 0 && format.indexOf('\'') < 0;
    }
}
