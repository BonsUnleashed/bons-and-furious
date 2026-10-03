package bons.furious.patch.models;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.model.geom.ModelPart;

/**
 * State of one bone search (see BoneLookupMixin): the element the children spliterator just handed over, and whether
 * the search met a ModelPart subclass that overrides getAllParts or hasChild (then the caller falls back to the
 * original stream, because only the original evaluates such an override).
 *
 * Whether a class overrides those two methods is answered by the JVM's own method resolution (MethodHandles), not by
 * Class.getMethod, which would build reflection data for every public method. Plain ModelParts skip the check; any
 * class the public lookup cannot see, or any failure, counts as overriding (so the original path runs).
 */
public final class BoneSearch implements Consumer<ModelPart> {
    private static final MethodType ALL_PARTS = MethodType.methodType(Stream.class);
    private static final MethodType HAS_CHILD = MethodType.methodType(boolean.class, String.class);
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                return lookup.revealDirect(lookup.findVirtual(type, "getAllParts", ALL_PARTS)).getDeclaringClass() == ModelPart.class
                        && lookup.revealDirect(lookup.findVirtual(type, "hasChild", HAS_CHILD)).getDeclaringClass() == ModelPart.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    public ModelPart item;
    public boolean fallback;

    @Override
    public void accept(ModelPart part) {
        this.item = part;
    }

    /** True when this part class traverses exactly like ModelPart (it does not override getAllParts or hasChild). */
    public static boolean plainTraversal(Class<?> type) {
        return type == ModelPart.class || PLAIN.get(type);
    }
}
