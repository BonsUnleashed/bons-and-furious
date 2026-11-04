package bons.furious.patch.forge_listener_guard;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * forge_listener_prologue_guard: instance-of tests by class name, without loading any class.
 *
 * `x instanceof T` in a listener resolves T through the listener's class loader. In the game every mod, Minecraft and
 * Forge class sits in one module layer with one class loader per layer, and a package belongs to exactly one module, so
 * a binary name denotes one class: T is a supertype of x's class exactly when T's name is the name of x's class, of one
 * of its superclasses or of an interface it implements (directly or through a superclass or superinterface). The test
 * therefore walks the supertypes of the (loaded) class of x and compares names; T itself is never loaded. A missing T
 * (an absent optional mod) would make the listener's own instanceof throw NoClassDefFoundError on every call; such a
 * listener never completes the early-return path, so its clause is never armed (GuardedDispatch) and it keeps being
 * called.
 *
 * The per-table profile: every distinct name set of a table's INST atoms gets one bit; ClassValue memoises, per subject
 * class, the bits of the sets that class is an instance of (one lookup per post).
 */
final class Profiles {
    private Profiles() {}

    /** All supertype names (classes and interfaces, transitively) of c, c included. */
    static Set<String> supertypeNames(Class<?> c) {
        Set<String> out = new HashSet<>();
        ArrayDeque<Class<?>> todo = new ArrayDeque<>();
        todo.add(c);
        while (!todo.isEmpty()) {
            Class<?> x = todo.pop();
            if (!out.add(x.getName())) continue;
            if (x.getSuperclass() != null) todo.add(x.getSuperclass());
            for (Class<?> i : x.getInterfaces()) todo.add(i);
        }
        return out;
    }

    static boolean isSubtype(Class<?> c, String dottedName) {
        return supertypeNames(c).contains(dottedName);
    }

    /** A ClassValue giving, per class, the bit mask of the name sets it is an instance of. */
    static ClassValue<long[]> profile(String[][] sets) {
        String[][] copy = sets.clone();
        return new ClassValue<>() {
            @Override
            protected long[] computeValue(Class<?> type) {
                Set<String> names = supertypeNames(type);
                long bits = 0;
                for (int i = 0; i < copy.length; i++) {
                    for (String n : copy[i]) {
                        if (names.contains(n)) {
                            bits |= 1L << i;
                            break;
                        }
                    }
                }
                return new long[] {bits};
            }
        };
    }
}
