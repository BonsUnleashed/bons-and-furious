package bons.furious.patch.crypticfoes;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BooleanSupplier;

/**
 * Iteration of "every entity in the level" for a loop whose per-entity test starts with a condition that does not
 * depend on the entity.
 *
 * The first element is handed over unchanged, so the loop body evaluates that condition at exactly the point it always
 * did (and throws exactly as before if it throws). Before the second element, {@code restMayMatch} evaluates the same
 * condition once: when it is false, every further iteration would only skip, so the iteration ends there; when it is
 * true, every remaining element is handed over as before. Iterating the level's entities has no side effects. The
 * elements are passed through untyped, so the caller's own cast happens exactly where it always did.
 */
public final class EntityScan {
    private EntityScan() {}

    public static <T> Iterable<T> firstThenIf(Iterable<T> all, BooleanSupplier restMayMatch) {
        if (all == null) {
            return null;   // the caller's loop fails on it exactly as it did on the original null
        }
        return () -> new FirstThenIf<>(all.iterator(), restMayMatch);
    }

    private static final class FirstThenIf<T> implements Iterator<T> {
        private static final int BEFORE_FIRST = 0, AFTER_FIRST = 1, ALL = 2, STOP = 3;
        private final Iterator<T> elements;
        private final BooleanSupplier restMayMatch;
        private int state = BEFORE_FIRST;

        FirstThenIf(Iterator<T> elements, BooleanSupplier restMayMatch) {
            this.elements = elements;
            this.restMayMatch = restMayMatch;
        }

        @Override
        public boolean hasNext() {
            if (this.state == AFTER_FIRST) {
                this.state = this.restMayMatch.getAsBoolean() ? ALL : STOP;
            }
            return this.state != STOP && this.elements.hasNext();
        }

        @Override
        public T next() {
            if (this.state == STOP) {
                throw new NoSuchElementException();
            }
            T element = this.elements.next();
            if (this.state == BEFORE_FIRST) {
                this.state = AFTER_FIRST;
            }
            return element;
        }
    }
}
