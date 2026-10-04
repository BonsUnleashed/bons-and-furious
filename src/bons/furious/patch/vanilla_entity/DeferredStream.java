package bons.furious.patch.vanilla_entity;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * vanilla_entity_block_scans (Minecraft 1.20.1, both sides): a stream that has not been built yet.
 *
 * The two call sites hand their stream straight to one match operation (anyMatch in Entity.isInWall, noneMatch in
 * Entity.move). A subclass answers the three match operations with a plain loop over the same elements in the same
 * order, which is what the vanilla stream does element by element, without the pipeline objects. Every other operation
 * builds the vanilla stream through the wrapped original call and delegates to it, so a mod that does something else
 * with the stream gets exactly the stream it would have had. After a loop-answered match the stream counts as
 * consumed, as a vanilla stream does: any further use except close() throws the same IllegalStateException.
 */
public abstract class DeferredStream<T> implements Stream<T> {
    private static final int NEW = 0, DELEGATED = 1, CONSUMED = 2;
    private int state = NEW;
    private Stream<T> real;

    /** Builds the stream the call site would have had (the wrapped original call). */
    protected abstract Stream<T> build();

    /** True when some element matches; called at most once, in the NEW state. */
    protected abstract boolean anyLoop(Predicate<? super T> predicate);

    /** False when some element does not match; called at most once, in the NEW state. */
    protected abstract boolean allLoop(Predicate<? super T> predicate);

    private Stream<T> real() {
        if (this.state == CONSUMED) {
            throw new IllegalStateException("stream has already been operated upon or closed");
        }
        if (this.state == NEW) {
            this.real = this.build();
            this.state = DELEGATED;
        }
        return this.real;
    }

    private boolean fresh() {
        if (this.state == NEW) {
            this.state = CONSUMED;
            return true;
        }
        return false;
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return this.fresh() ? this.anyLoop(predicate) : this.real().anyMatch(predicate);
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return this.fresh() ? !this.anyLoop(predicate) : this.real().noneMatch(predicate);
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return this.fresh() ? this.allLoop(predicate) : this.real().allMatch(predicate);
    }

    @Override
    public boolean isParallel() {
        return this.state == DELEGATED && this.real.isParallel();
    }

    @Override
    public Stream<T> sequential() {
        return this.state == DELEGATED ? this.real.sequential() : this;
    }

    @Override
    public void close() {
        if (this.state == DELEGATED) {
            this.real.close();
        } else {
            this.state = CONSUMED;     // a vanilla stream closed before use is consumed too; it has no close handlers
        }
    }

    // Everything else is the vanilla stream's own behaviour.
    @Override public Stream<T> filter(Predicate<? super T> p) { return this.real().filter(p); }
    @Override public <R> Stream<R> map(Function<? super T, ? extends R> f) { return this.real().map(f); }
    @Override public IntStream mapToInt(ToIntFunction<? super T> f) { return this.real().mapToInt(f); }
    @Override public LongStream mapToLong(ToLongFunction<? super T> f) { return this.real().mapToLong(f); }
    @Override public DoubleStream mapToDouble(ToDoubleFunction<? super T> f) { return this.real().mapToDouble(f); }
    @Override public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> f) { return this.real().flatMap(f); }
    @Override public IntStream flatMapToInt(Function<? super T, ? extends IntStream> f) { return this.real().flatMapToInt(f); }
    @Override public LongStream flatMapToLong(Function<? super T, ? extends LongStream> f) { return this.real().flatMapToLong(f); }
    @Override public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> f) { return this.real().flatMapToDouble(f); }
    @Override public <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> m) { return this.real().mapMulti(m); }
    @Override public Stream<T> distinct() { return this.real().distinct(); }
    @Override public Stream<T> sorted() { return this.real().sorted(); }
    @Override public Stream<T> sorted(Comparator<? super T> c) { return this.real().sorted(c); }
    @Override public Stream<T> peek(Consumer<? super T> a) { return this.real().peek(a); }
    @Override public Stream<T> limit(long n) { return this.real().limit(n); }
    @Override public Stream<T> skip(long n) { return this.real().skip(n); }
    @Override public Stream<T> takeWhile(Predicate<? super T> p) { return this.real().takeWhile(p); }
    @Override public Stream<T> dropWhile(Predicate<? super T> p) { return this.real().dropWhile(p); }
    @Override public void forEach(Consumer<? super T> a) { this.real().forEach(a); }
    @Override public void forEachOrdered(Consumer<? super T> a) { this.real().forEachOrdered(a); }
    @Override public Object[] toArray() { return this.real().toArray(); }
    @Override public <A> A[] toArray(IntFunction<A[]> g) { return this.real().toArray(g); }
    @Override public T reduce(T identity, BinaryOperator<T> op) { return this.real().reduce(identity, op); }
    @Override public Optional<T> reduce(BinaryOperator<T> op) { return this.real().reduce(op); }
    @Override public <U> U reduce(U identity, BiFunction<U, ? super T, U> acc, BinaryOperator<U> comb) { return this.real().reduce(identity, acc, comb); }
    @Override public <R> R collect(Supplier<R> s, BiConsumer<R, ? super T> acc, BiConsumer<R, R> comb) { return this.real().collect(s, acc, comb); }
    @Override public <R, A> R collect(Collector<? super T, A, R> c) { return this.real().collect(c); }
    @Override public java.util.List<T> toList() { return this.real().toList(); }
    @Override public Optional<T> min(Comparator<? super T> c) { return this.real().min(c); }
    @Override public Optional<T> max(Comparator<? super T> c) { return this.real().max(c); }
    @Override public long count() { return this.real().count(); }
    @Override public Optional<T> findFirst() { return this.real().findFirst(); }
    @Override public Optional<T> findAny() { return this.real().findAny(); }
    @Override public Iterator<T> iterator() { return this.real().iterator(); }
    @Override public Spliterator<T> spliterator() { return this.real().spliterator(); }
    @Override public Stream<T> parallel() { return this.real().parallel(); }
    @Override public Stream<T> unordered() { return this.real().unordered(); }
    @Override public Stream<T> onClose(Runnable h) { return this.real().onClose(h); }
}
