package bons.furious.patch.geckolib_lean;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * geckolib_bone_lookup_memo (GeckoLib 4.9.3 for NeoForge 1.21.1, client; since 1.0.49): the list type GeckoLib's bone tree
 * is built from.
 *
 * GeoBone's child list and the built-in model factory's top-level bone list are created as this subclass of fastutil's
 * ObjectArrayList (the class they were). Every structural mutation, through any path the list offers (List and
 * ObjectArrayList methods, Stack push/pop, sort/replaceAll/removeIf, and handing out the raw backing array through
 * elements()), first runs ObjectArrayList's own code unchanged and then advances one global bone-tree version
 * (GeckoBoneMemo.VERSION). fastutil 8.5.12's (Minecraft 1.21.1's) list iterator (ObjectArrayList$1) and sublists
 * (ObjectArrayList$SubList, AbstractObjectList$ObjectSubList and its iterators) change the list only through
 * ObjectArrayList.add(int, K) / set / remove(int) / addAll / addElements / setElements / removeElements, which are counted
 * here, so they need no wrapper and iteration costs what it cost. Elements, order, exceptions and every return value are
 * ObjectArrayList's.
 *
 * Exact variant: these lists are a subclass of ObjectArrayList (getClass() differs; elements and order identical).
 */
public final class CountingBoneList<K> extends ObjectArrayList<K> {
    private static final long serialVersionUID = 1L;

    public CountingBoneList() {
        super();
    }

    private static void changed() {
        GeckoBoneMemo.VERSION.incrementAndGet();
    }

    @Override
    public void add(int index, K k) {
        super.add(index, k);
        changed();
    }

    @Override
    public boolean add(K k) {
        boolean r = super.add(k);
        changed();
        return r;
    }

    @Override
    public boolean addAll(int index, Collection<? extends K> c) {
        try {
            return super.addAll(index, c);
        } finally {
            changed();
        }
    }

    @Override
    public boolean addAll(int index, ObjectList<? extends K> l) {
        try {
            return super.addAll(index, l);
        } finally {
            changed();
        }
    }

    @Override
    public boolean addAll(Collection<? extends K> c) {
        try {
            return super.addAll(c);
        } finally {
            changed();
        }
    }

    @Override
    public boolean addAll(ObjectList<? extends K> l) {
        try {
            return super.addAll(l);
        } finally {
            changed();
        }
    }

    @Override
    public K remove(int index) {
        K r = super.remove(index);
        changed();
        return r;
    }

    @Override
    public boolean remove(Object k) {
        boolean r = super.remove(k);
        changed();
        return r;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        try {
            return super.removeAll(c);
        } finally {
            changed();
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        try {
            return super.retainAll(c);
        } finally {
            changed();
        }
    }

    @Override
    public boolean removeIf(Predicate<? super K> filter) {
        try {
            return super.removeIf(filter);
        } finally {
            changed();
        }
    }

    @Override
    public K set(int index, K k) {
        K r = super.set(index, k);
        changed();
        return r;
    }

    @Override
    public void clear() {
        super.clear();
        changed();
    }

    @Override
    public void size(int size) {
        super.size(size);
        changed();
    }

    @Override
    public void removeElements(int from, int to) {
        super.removeElements(from, to);
        changed();
    }

    @Override
    public void addElements(int index, K[] a, int offset, int length) {
        super.addElements(index, a, offset, length);
        changed();
    }

    @Override
    public void addElements(int index, K[] a) {
        super.addElements(index, a);
        changed();
    }

    @Override
    public void setElements(int index, K[] a, int offset, int length) {
        super.setElements(index, a, offset, length);
        changed();
    }

    @Override
    public void setElements(int index, K[] a) {
        super.setElements(index, a);
        changed();
    }

    @Override
    public void setElements(K[] a) {
        super.setElements(a);
        changed();
    }

    @Override
    public void sort(Comparator<? super K> comp) {
        try {
            super.sort(comp);
        } finally {
            changed();
        }
    }

    @Override
    public void unstableSort(Comparator<? super K> comp) {
        try {
            super.unstableSort(comp);
        } finally {
            changed();
        }
    }

    @Override
    public void replaceAll(UnaryOperator<K> operator) {
        try {
            super.replaceAll(operator);
        } finally {
            changed();
        }
    }

    @Override
    public void push(K o) {
        super.push(o);
        changed();
    }

    @Override
    public K pop() {
        K r = super.pop();
        changed();
        return r;
    }

    /**
     * The raw backing array: whoever takes it can write it at any later time without the list knowing, so a list that has
     * handed it out is never trusted again (GeckoBoneMemo keeps the stock search for every model that contains it).
     */
    @Override
    public K[] elements() {
        exposed = true;
        changed();
        return super.elements();
    }

    /** True once elements() handed out the backing array. */
    public boolean exposed() {
        return exposed;
    }

    private boolean exposed;
}
