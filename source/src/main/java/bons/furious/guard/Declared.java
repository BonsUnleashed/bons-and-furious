package bons.furious.guard;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Which class declares the method a virtual call on a class runs, asked through the JVM's own resolution of that ONE method
 * (privateLookupIn + findVirtual + revealDirect), never through a listing of the class's members.
 *
 * Why not Class.getMethod / getDeclaredMethod / getDeclaredMethods: each of them builds a reflection object for every method it
 * walks and resolves the parameter, return and exception types of all of them. One type the game cannot load makes the whole call
 * throw: NoClassDefFoundError for a class that is in no installed jar (a client class on a dedicated server, a class of a mod that
 * is not installed), or the RuntimeException of the loader's RuntimeDistCleaner ("Attempted to load class ... for invalid dist", after
 * its red ERROR line) for a class marked client-only. Mod classes with such methods are common and work as long as nothing lists
 * their members: an entity with an unmarked render helper, the lambda body left behind by a stripped client method. Bug sweep
 * 2026-11-12: two switches listed the methods of every entity class without a catch, which stops a dedicated server; 1.0.49.2 had
 * already moved two other switches to this resolution because of the ERROR lines alone.
 *
 * Resolution finds the method by name and descriptor from the class upwards, so a declaration anywhere below the base class is
 * seen, mixin-added ones included (they are part of the loaded class). It loads no class the caller does not already hold. Every
 * failure (a class or module that does not open to this mod, a missing method, anything else) answers null: "cannot tell", which
 * every caller treats as the careful answer for that class.
 */
public final class Declared {
    private Declared() {
    }

    /**
     * The class whose declaration {@code type.name(signature)} resolves to, or null when it cannot be asked. A lookup inside
     * {@code type} itself first (sees everything the class can call, protected members included), then one inside {@code base}
     * (sees base's package-private members; needs {@code type} to be accessible from there), then throughParent.
     */
    public static Class<?> by(Class<?> type, Class<?> base, String name, MethodType signature) {
        for (Class<?> anchor : new Class<?>[] {type, base}) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(anchor, MethodHandles.lookup());
                return lookup.revealDirect(lookup.findVirtual(type, name, signature)).getDeclaringClass();
            } catch (Throwable t) {
                // not this way: the next anchor, then the parent that the base can name
            }
        }
        return throughParent(type, base, name, signature);
    }

    /**
     * The one case both lookups of by() miss: a package-private method of the base (Entity.updateInWaterStateAndDoWaterCurrentPushing)
     * asked about a class that is not public, such as an anonymous subclass in a mod. A lookup inside that class may not use a
     * package-private method of another package, and the lookup inside the base may not name a class it cannot see. Such a class
     * cannot replace the method either: only a class of the method's own package can. So the answer is the one of its nearest
     * superclass that the base can name, as long as the method found there is package-private and no class skipped on the way
     * lies in the method's package. Anything else answers null.
     */
    private static Class<?> throughParent(Class<?> type, Class<?> base, String name, MethodType signature) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(base, MethodHandles.lookup());
            for (Class<?> parent = type.getSuperclass(); parent != null && base.isAssignableFrom(parent); parent = parent.getSuperclass()) {
                java.lang.invoke.MethodHandleInfo found;
                try {
                    found = lookup.revealDirect(lookup.findVirtual(parent, name, signature));
                } catch (IllegalAccessException e) {
                    continue;       // the base cannot name this one either: its parent
                }
                int access = found.getModifiers() & (java.lang.reflect.Modifier.PUBLIC | java.lang.reflect.Modifier.PROTECTED | java.lang.reflect.Modifier.PRIVATE);
                if (access != 0) return null;
                String home = found.getDeclaringClass().getPackageName();
                for (Class<?> skipped = type; skipped != parent; skipped = skipped.getSuperclass()) {
                    if (skipped.getPackageName().equals(home)) return null;
                }
                return found.getDeclaringClass();
            }
        } catch (Throwable t) {
            // cannot tell
        }
        return null;
    }

    /**
     * True when {@code type} runs {@code base}'s own {@code name(signature)}: no class below {@code base} declares it. False when
     * one does, and also when it cannot be asked (the caller then stands aside for that class).
     */
    public static boolean inherits(Class<?> type, Class<?> base, String name, MethodType signature) {
        return by(type, base, name, signature) == base;
    }

    /**
     * The class or interface that holds the method an object of exactly {@code type} runs for {@code name(signature)}, or null when
     * it cannot be asked: the JVM's resolution of that one method as an invokespecial made from inside {@code type}
     * (privateLookupIn + findSpecial + revealDirect).
     *
     * For a method that an interface gives as a default (Forge's IForgeItem, IForgeBlock) this is the one to ask. A virtual
     * lookup through a class, which by() makes, names that class itself for a default method the class only inherits (the JVM
     * links the method through the class's own method table and keeps the class with it), so by() cannot tell "inherits the
     * interface's default" from "declares its own". The direct handle of an invokespecial names the real holder: the interface
     * when no class of the chain replaces the default, else the nearest class that declares the method. There is no second
     * anchor here: a package-private method of a base class in another package cannot be asked this way (null).
     */
    public static Class<?> holder(Class<?> type, String name, MethodType signature) {
        try {
            MethodHandles.Lookup in = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            return in.revealDirect(in.findSpecial(type, name, signature, type)).getDeclaringClass();
        } catch (Throwable t) {
            return null;
        }
    }
}
