package bons.furious.patch.fancymenu_identity_c49;

import de.keksuccino.fancymenu.customization.ScreenCustomization;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fancymenu_screen_identity_memo (FancyMenu 3.9.14; client side). Ours; none of FancyMenu's code
 * is carried (FancyMenu's licence allows no copying), only injectors with this logic.
 *
 * What FancyMenu does. While a screen is open, FancyMenu asks several times a frame which screen it is
 * (ScreenCustomizationLayerHandler.getActiveLayer, isCustomizationEnabledForScreen from its widget hooks, the overlay):
 * (1) ScreenCustomization.isScreenBlacklisted(String) runs its blacklist rules over the class name (twenty startsWith /
 * contains / equals tests by default); (2) UniversalScreenIdentifierRegistry.getUniversalIdentifierFor(String) asks
 * containsKey, then walks every registered universal identifier comparing its screen class; (3)
 * ScreenIdentifierHandler.isValidIdentifier calls Class.forName(identifier, false, its loader) for a class name.
 *
 * What the switch does, and why each answer is the same.
 * (1) FancyMenu's own rules (their class's nest host is ScreenCustomization: the twenty rules addDefaultScreenBlacklistRules
 * adds, pure tests of the name, one of them also of "is OptiFine's installer class present", which does not change during
 * a session) are asked once per class name: the position of the first own rule that blacklists it is kept. A rule of
 * another mod is still called on every query, in list order, exactly when the original loop would reach it: every
 * foreign rule before that position, until one answers true (none kept: every foreign rule). The answer is the loop's:
 * true at the first rule that says true. A null rule in the list, or a list whose size changed since its rules were
 * sorted, and the original loop runs. addScreenBlacklistRule (the list is private; getScreenBlacklistRules hands out a
 * copy) drops every kept position and sorts the rules again.
 * (2) An index from screen class to the first universal identifier registered for it (in the map's iteration order,
 * which put keeps for a replaced key) answers the walk; containsKey still asks the live map. register, the map's only
 * writer, drops the index; the next 8 lookups walk as before (so registering between lookups costs nothing extra) and
 * the 9th builds the index from the map.
 * (3) A class name whose Class.forName already returned a class returns that same Class again (a loaded class stays
 * loaded while its loader lives, and forName with initialize=false returns the loader's loaded class). Missing classes and
 * errors are never kept: forName runs again, as before.
 *
 * -Dbons_and_furious.fancymenuScreenIdentityMemo=false switches it off at run time.
 */
public final class ScreenIdentityMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fancymenuScreenIdentityMemo", "true"));
    static final int CAP = 4096;
    private static volatile boolean announced;

    private ScreenIdentityMemo() {}

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fancymenu_screen_identity_memo applies (FancyMenu's screen identification answers are kept while they cannot change)");
        }
    }

    // ---------------------------------------------------------------- (1) blacklist
    /** class name -> position of the first of FancyMenu's own rules that blacklists it (-1: none of them). */
    private static final ConcurrentHashMap<String, Integer> FIRST_OWN = new ConcurrentHashMap<>();

    /** The rule list as last sorted: own[i] = rule i is one of FancyMenu's own; usable = no null rule. */
    private record Rules(int size, boolean[] own, boolean foreign, boolean usable) {}

    /** null = sort the rules at the next query. */
    private static volatile Rules rules;

    /** The loop's answer for this class name, or null when the original loop must run. */
    public static Boolean blacklisted(String screenClassPath, List<ScreenCustomization.ScreenBlacklistRule> list) {
        if (!enabled || screenClassPath == null) return null;
        Rules r = rules;
        if (r == null || r.size != list.size()) {
            FIRST_OWN.clear();
            rules = r = sort(list);
        }
        if (!r.usable) return null;
        Integer kept = FIRST_OWN.get(screenClassPath);
        int first;
        if (kept != null) {
            first = kept;
        } else {
            first = -1;
            for (int i = 0; i < r.size; i++) {
                if (r.own[i] && list.get(i).isScreenBlacklisted(screenClassPath)) {
                    first = i;
                    break;
                }
            }
            if (FIRST_OWN.size() < CAP) {
                FIRST_OWN.put(screenClassPath, first);
                announce();
            }
        }
        if (r.foreign) {
            for (int i = 0, end = first < 0 ? r.size : first; i < end; i++) {
                if (!r.own[i] && list.get(i).isScreenBlacklisted(screenClassPath)) return Boolean.TRUE;
            }
        }
        return first >= 0;
    }

    private static Rules sort(List<ScreenCustomization.ScreenBlacklistRule> list) {
        int n = list.size();
        boolean[] own = new boolean[n];
        boolean foreign = false;
        for (int i = 0; i < n; i++) {
            Object rule = list.get(i);
            if (rule == null) return new Rules(n, own, true, false);
            own[i] = rule.getClass().getNestHost() == ScreenCustomization.class;
            foreign |= !own[i];
        }
        return new Rules(n, own, foreign, true);
    }

    /** At the end of addScreenBlacklistRule. */
    public static void rulesChanged() {
        rules = null;
        FIRST_OWN.clear();
    }

    // ---------------------------------------------------------------- (2) universal identifiers
    /** universalFor's answer "run the original method" (compared by identity). */
    public static final String ORIGINAL = new String("run the original method");
    /** Lookups after a change that walk as before; the next one builds the index. */
    static final int WALKS_BEFORE_INDEX = 8;
    /** screen class -> first universal identifier registered for it; null = not built since the last change. */
    private static volatile Map<String, String> reverse;
    private static volatile int walksSinceChange;
    /** Counters for checks: lookups left to the original walk, index builds. */
    public static long universalWalks, universalBuilds;

    /** In place of getUniversalIdentifierFor(String)'s walk: the live containsKey, then the index (or ORIGINAL). */
    public static String universalFor(String screenClassPath, Map<String, String> universal) {
        Map<String, String> index = reverse;
        if (index == null) {
            if (walksSinceChange < WALKS_BEFORE_INDEX) {
                walksSinceChange++;
                universalWalks++;
                return ORIGINAL;
            }
            HashMap<String, String> built = new HashMap<>();
            for (Map.Entry<String, String> e : universal.entrySet()) built.putIfAbsent(e.getValue(), e.getKey());
            reverse = index = built;
            universalBuilds++;
            announce();
        }
        if (universal.containsKey(screenClassPath)) return screenClassPath;
        return index.get(screenClassPath);
    }

    /** At the end of register (the map's only writer). */
    public static void universalChanged() {
        reverse = null;
        walksSinceChange = 0;
    }

    // ---------------------------------------------------------------- (3) classes found
    private static final ConcurrentHashMap<String, Class<?>> FOUND = new ConcurrentHashMap<>();
    private static volatile ClassLoader foundLoader;

    /** In place of isValidIdentifier's Class.forName(name, initialize, loader). */
    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        if (!enabled || name == null || initialize) return Class.forName(name, initialize, loader);
        if (loader == foundLoader) {
            Class<?> kept = FOUND.get(name);
            if (kept != null) return kept;
        }
        Class<?> found = Class.forName(name, false, loader);
        if (foundLoader == null) foundLoader = loader;
        if (loader == foundLoader && FOUND.size() < CAP) FOUND.putIfAbsent(name, found);
        return found;
    }
}
