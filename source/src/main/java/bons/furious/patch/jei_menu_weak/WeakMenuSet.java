package bons.furious.patch.jei_menu_weak;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix jei_unsupported_menu_weak_set (JEI 15.59.0.212, Forge 1.20.1; client only). JEI is MIT; no JEI code
 * is carried. Idea: Collections Of Optimizations (jei.weakUnsupportedContainers), idea text only.
 *
 * JEI's RecipeTransferManager remembers in a plain HashSet every menu whose getType() throws (it asks once per menu,
 * the first time a recipe view looks for a transfer handler for it). The player's own InventoryMenu has no MenuType, so it
 * always lands there the first time a recipe is opened from the inventory, and nothing ever removes it. A menu holds its
 * player and the player its level: every respawn and dimension change builds a new LocalPlayer (and a new InventoryMenu), so
 * each looked-up inventory menu keeps one old LocalPlayer and its old ClientLevel (chunks, entities) alive until JEI's
 * runtime is rebuilt.
 *
 * The fix routes the set's two calls (contains, add; the field's only accesses, both in getMenuType) to this set, one per
 * RecipeTransferManager like the original:
 *  - menus whose class keeps Object's equals and hashCode (every installed menu class, InventoryMenu included) are held by
 *    a WeakHashMap: equality there is identity, as in the HashSet, and an entry disappears only when nothing else can reach
 *    the menu - and then getMenuType can never be asked about it again, because its caller holds the menu. So every answer
 *    is the HashSet's; only retention changes;
 *  - menus whose class overrides equals or hashCode are kept in a strong HashSet exactly as before (a later, equal menu must
 *    still find them).
 * The original HashSet field stays in place and is still consulted by contains (it stays empty while the switch is on, so
 * flipping the runtime flag during play never loses an entry).
 *
 * Runtime flag: -Dbons_and_furious.jeiUnsupportedMenuWeakSet=false sends both calls to JEI's own HashSet.
 * Shadow mode: -Dbons_and_furious.jeiUnsupportedMenuWeakSet.shadow=true also keeps a strong copy of every added menu and
 * compares each contains answer with it (SHADOW_CHECKS / SHADOW_MISMATCHES; WARN for the first 20 mismatches). The shadow
 * copy keeps the menus alive, so it is for checking exactness, not for play.
 */
public final class WeakMenuSet {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.jeiUnsupportedMenuWeakSet", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.jeiUnsupportedMenuWeakSet.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Whether a class keeps Object's equals and hashCode (identity equality). */
    private static final ClassValue<Boolean> IDENTITY_EQUALITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("equals", Object.class).getDeclaringClass() == Object.class
                        && type.getMethod("hashCode").getDeclaringClass() == Object.class;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        }
    };
    private static volatile boolean announced;

    private final WeakHashMap<Object, Boolean> weak = new WeakHashMap<>();
    private Set<Object> strong;        // menus of classes with their own equals/hashCode (kept as stock does)
    private Set<Object> shadowCopy;    // shadow mode only: every menu ever added, strongly

    /** The answer of JEI's HashSet.contains for this menu. */
    public boolean contains(Object menu) {
        boolean answer = weak.containsKey(menu) || (strong != null && strong.contains(menu));
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            boolean stock = shadowCopy != null && shadowCopy.contains(menu);
            if (stock != answer && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: jei_unsupported_menu_weak_set shadow mismatch for {}: stock {}, ours {}", menu.getClass().getName(), stock, answer);
        }
        return answer;
    }

    /** JEI's HashSet.add for this menu (its result is not used by JEI). */
    public boolean add(Object menu) {
        if (SHADOW) {
            if (shadowCopy == null) shadowCopy = new HashSet<>();
            shadowCopy.add(menu);
        }
        announce();
        if (IDENTITY_EQUALITY.get(menu.getClass())) return weak.put(menu, Boolean.TRUE) == null;
        if (strong == null) strong = new HashSet<>();
        return strong.add(menu);
    }

    /** For the harness: menus currently held (weakly or strongly). */
    public int size() {
        return weak.size() + (strong == null ? 0 : strong.size());
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: jei_unsupported_menu_weak_set applies (JEI's set of menus without a menu type no longer keeps old players and worlds alive)");
        }
    }
}
