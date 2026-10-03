package bons.furious.patch.emf_compile;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;

/**
 * Bons and Furious switch emf_variable_index (Entity Model Features 3.2.4, client). No EMF code here.
 *
 * EMF compiles every animated model variant (the Fresh Animations / Semos Animation Lib player models are 370-840
 * animation lines each) into a class with one ASMVariableHandler per compile. The handler keeps the variable names in
 * two ArrayLists (float and bool), answers each variable reference with list.contains then list.indexOf, and after
 * every line ASMParser.compileOrNull calls verifyEndOfParse, which runs floatVarList.stream().anyMatch(boolVarList::contains):
 * |float| x |bool| String.equals calls per line, 4-13 million per player variant, on the render thread at every resource
 * reload.
 *
 * One VariableIndex per handler mirrors the two lists in two name -> index HashMaps and remembers whether a name was ever
 * added to one list while the other already held it. The handler's contains / indexOf answer from the maps and the
 * per-line check returns that flag. Identical because:
 *  - the lists are append-only and only getAndAssignVarIndex changes them (its add is wrapped, so every append is
 *    mirrored; the only other reader, ASMAnimationHandler, iterates them, calls get(i) and size());
 *  - a name is appended only when contains returned false, so a list never holds duplicates and the index recorded at the
 *    append (size - 1) equals indexOf; String equals/hashCode agree and HashMap handles null like ArrayList;
 *  - an overlap can appear but never disappear (nothing is removed), so the flag equals anyMatch at every call;
 *  - the handler is created per compile and used by one thread.
 * The decision is taken per handler at construction: a handler built while the switch is off never gets an index and
 * runs the original code throughout.
 */
public final class VariableIndex {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.emfVariableIndex=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfVariableIndex", "true"));
    /** Shadow mode for rigs: every answer is also computed the original way and compared (WARN on a difference). */
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.emfVariableIndex.verify");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced;
    /** Shadow-mode counters (read by probes). */
    public static long verified;
    public static long mismatches;

    private final HashMap<Object, Integer> floats = new HashMap<>();
    private final HashMap<Object, Integer> bools = new HashMap<>();
    private boolean overlap;

    private VariableIndex() {
    }

    /** Field initializer of the mixed-in ASMVariableHandler: an index when the switch is on, otherwise null (original code). */
    public static VariableIndex create() {
        if (!enabled) return null;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: emf_variable_index indexes the variable lists of EMF's animation compiler");
        }
        return new VariableIndex();
    }

    /** list.contains(name) for the handler's bool list (bool) or float list. */
    public boolean contains(boolean bool, Object name, List<?> list) {
        boolean r = (bool ? bools : floats).containsKey(name);
        if (VERIFY) check(r == list.contains(name), "contains", name);
        return r;
    }

    /** list.indexOf(name). */
    public int indexOf(boolean bool, Object name, List<?> list) {
        Integer i = (bool ? bools : floats).get(name);
        int r = i == null ? -1 : i;
        if (VERIFY) check(r == list.indexOf(name), "indexOf", name);
        return r;
    }

    /** After list.add(name) appended name at position index. */
    public void added(boolean bool, Object name, int index) {
        HashMap<Object, Integer> mine = bool ? bools : floats;
        mine.putIfAbsent(name, index);
        if ((bool ? floats : bools).containsKey(name)) overlap = true;
    }

    /** floatVarList.stream().anyMatch(boolVarList::contains). */
    public boolean overlap(List<?> floatList, List<?> boolList) {
        if (VERIFY) check(overlap == floatList.stream().anyMatch(boolList::contains), "overlap", null);
        return overlap;
    }

    private static void check(boolean same, String what, Object name) {
        verified++;
        if (!same && mismatches++ < 20) {
            LOGGER.warn("Bons and Furious: emf_variable_index shadow check: {} differs for [{}]", what, name);
        }
    }
}
