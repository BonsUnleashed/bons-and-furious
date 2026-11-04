package bons.furious.patch.mysticalagriculture_coo49;

import com.blakebr0.mysticalagriculture.api.crop.Crop;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious 1.0.49 switch mysticalagriculture_crop_name_key_memo (Mystical Agriculture 7.0.24, both sides; kind
 * opt, default on; helper of CropNameKeyMixin; no Mystical Agriculture code carried).
 *
 * What Mystical Agriculture does. Crop.getDisplayName() answers the crop's display name when one was set, else
 * Component.translatable(String.format("crop.%s.%s", getModId(), getName())): a String.format on every name look-up of a
 * seed, essence or crop block (hovered items every frame, recipe viewers indexing every seed and essence).
 *
 * What the switch does. At that String.format call (the format is the constant "crop.%s.%s", its arguments getModId()
 * and getName() as the mod computes them), a crop of a class that keeps Crop's own getId / getName / getModId (both read
 * the final id, so the arguments never change) answers the key it made the first time; a crop of another class gets the
 * key made again on every call. The key is made by format() below: the same characters as String.format. Every call
 * still returns a NEW translatable component (callers may change it), and a crop with a display name never reaches the
 * call. Runtime flag bons_and_furious.mysticalagricultureCropNameKeyMemo.
 */
public final class CropNameKeys {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mysticalagricultureCropNameKeyMemo", "true"));
    /** Kept by a crop of a class that overrides one of the id getters (compared by identity: no key is this object). */
    public static final String NOT_PLAIN = new String("(a crop class with its own id getters)");
    /** Crop.getDisplayName's key format (the constant in its bytecode; the guard row pins the method). */
    static final String FORMAT = "crop.%s.%s";
    private static volatile boolean announced;

    /** Whether a Crop class keeps Crop's own getId, getName and getModId (the key is then a function of the final id). */
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getId").getDeclaringClass() == Crop.class && type.getMethod("getName").getDeclaringClass() == Crop.class
                        && type.getMethod("getModId").getDeclaringClass() == Crop.class;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
        }
    };

    private CropNameKeys() {
    }

    public static boolean plain(Class<?> type) {
        return PLAIN.get(type);
    }

    /**
     * String.format(format, args) for the key. For exactly the format "crop.%s.%s" and two arguments that are Strings or
     * null, the same characters by concatenation: a %s without flags, width or precision appends String.valueOf of its
     * argument (a String is never Formattable, null gives "null", and no locale applies to a lower-case s). Anything else is
     * passed to String.format itself.
     */
    public static String format(String format, Object[] args) {
        if (args != null && args.length == 2 && FORMAT.equals(format)) {
            Object a = args[0], b = args[1];
            if ((a == null || a instanceof String) && (b == null || b instanceof String)) return "crop." + a + "." + b;
        }
        return String.format(format, args);
    }

    /** A key a crop keeps from now on (logs the switch's one INFO line the first time). */
    public static String kept(String key) {
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: mysticalagriculture_crop_name_key_memo applies (a crop's name key is formatted once)");
        }
        return key;
    }
}
