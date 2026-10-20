package bons.furious.patch.l2hostility;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * Bons and Furious switch l2hostility_curio_check_lean (L2 Hostility 2.5.19 with Curios API 5.14.1+1.20.1, both sides; our
 * pack does not install L2 Hostility, the switch is for other packs, proven offline on the real classes).
 *
 * <p>L2 Hostility answers "does this entity wear item X in a curio slot" (its traits, curses and rings ask it on many
 * events) in CurioCompat.hasItemImpl: it resolves the entity's curios capability, calls the handler's findFirstCurio(X) and
 * drops the answer, then walks every slot type; for each type with slots whose id is not "curio" it builds
 * ItemTags.create(new ResourceLocation("curios", id)) to ask whether X may go there, and compares that type's stacks.
 *
 * <p>With the switch on:
 * <ul>
 * <li>findFirstCurio is not called when nothing it does can be observed: the handler is Curios' own CurioInventoryWrapper,
 *   every slot type in it is Curios' own CurioStacksHandler with no pending slot-count update and Curios' own
 *   DynamicStackHandler. That call then only reads (the slot map, each type's stacks, their sizes and stacks, the item
 *   comparison, the render flags when found) and builds an answer that L2 Hostility drops. A pending update matters
 *   because CurioStacksHandler.getStacks first runs update(), which resizes the type, posts SlotModifiersUpdatedEvent and
 *   resets an open curios menu; then, and for any other implementation, the call runs as before.</li>
 * <li>the slot tag key comes from curios_tag_predicate_keys' CuriosTagKeys.slotTag(id) (called through a method handle;
 *   that group is not changed): for an id with a vanilla path it is the kept canonical key, otherwise the original
 *   expression is evaluated there (so an invalid id throws the same exception). The ResourceLocation handed to
 *   ItemTags.create is that key's own location object, and ItemTags.create of exactly that object answers the key: the
 *   canonical instance the interner itself would return (it is kept reachable, so the interner keeps answering with it).
 *   Any other ResourceLocation goes to ItemTags.create as before.</li>
 * </ul>
 * Answers, exceptions, the remaining calls (capability resolves, getIdentifier, the holder test, stack reads) and their
 * order are unchanged.
 *
 * <p>-Dbons_and_furious.l2hostilityCurioCheckLean=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.l2hostilityCurioCheckLean.shadow=true makes every original call and compares: the skipped
 * findFirstCurio is still made (SHADOW_CHECKS counts it; a pending update found afterwards counts a mismatch), and every slot
 * key is compared with the original expression's key (equal and the same instance, else a mismatch, WARN for the first 20).
 */
public final class CurioCheckLean {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2hostilityCurioCheckLean", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.l2hostilityCurioCheckLean.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** findFirstCurio calls skipped and slot keys answered from CuriosTagKeys (statistics; plain, racy). */
    public static long findSkipped, keysReused;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle SLOT_TAG, CURIOS, UPDATE, STACK_HANDLER;
    private static final Class<?> WRAPPER, STACKS, DYNAMIC;
    private static final boolean READY;
    private static volatile TagKey<Item> last;
    private static volatile boolean announced;

    static {
        MethodHandle slotTag = null, curios = null, update = null, stackHandler = null;
        Class<?> wrapper = null, stacks = null, dynamic = null;
        boolean ready = false;
        try {
            Class<?> keys = Class.forName("bons.furious.patch.curios_tooltip.CuriosTagKeys", true, CurioCheckLean.class.getClassLoader());
            Method m = keys.getDeclaredMethod("slotTag", String.class);
            m.setAccessible(true);
            slotTag = MethodHandles.lookup().unreflect(m).asType(MethodType.methodType(TagKey.class, String.class));
            ClassLoader curiosLoader = ICuriosItemHandler.class.getClassLoader();
            wrapper = Class.forName("top.theillusivec4.curios.common.capability.CurioInventoryCapability$CurioInventoryWrapper", false, curiosLoader);
            stacks = Class.forName("top.theillusivec4.curios.common.inventory.CurioStacksHandler", false, curiosLoader);
            dynamic = Class.forName("top.theillusivec4.curios.common.inventory.DynamicStackHandler", false, curiosLoader);
            curios = getter(wrapper, "curios");
            update = getter(stacks, "update");
            stackHandler = getter(stacks, "stackHandler");
            ready = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: l2hostility_curio_check_lean is inactive: Curios or curios_tag_predicate_keys' helper is not the supported layout ({})", t.toString());
        }
        SLOT_TAG = slotTag;
        CURIOS = curios;
        UPDATE = update;
        STACK_HANDLER = stackHandler;
        WRAPPER = wrapper;
        STACKS = stacks;
        DYNAMIC = dynamic;
        READY = ready;
    }

    private CurioCheckLean() {
    }

    private static MethodHandle getter(Class<?> owner, String name) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        MethodHandle h = MethodHandles.lookup().unreflectGetter(f);
        return h.asType(MethodType.methodType(f.getType() == boolean.class ? boolean.class : Object.class, Object.class));
    }

    /**
     * True when findFirstCurio on this handler only reads: Curios' own wrapper, every slot type Curios' own stacks handler
     * without a pending update, with Curios' own dynamic stack handler.
     */
    static boolean onlyReads(ICuriosItemHandler handler) throws Throwable {
        if (handler == null || handler.getClass() != WRAPPER) return false;
        Map<?, ?> types = (Map<?, ?>) (Object) CURIOS.invokeExact((Object) handler);
        if (types == null) return false;
        for (Object type : types.values()) {
            if (type == null || type.getClass() != STACKS || (boolean) UPDATE.invokeExact(type)) return false;
            Object stacks = (Object) STACK_HANDLER.invokeExact(type);
            if (stacks == null || stacks.getClass() != DYNAMIC) return false;
        }
        return true;
    }

    /** In place of hasItemImpl's handler.findFirstCurio(item), whose answer L2 Hostility drops. */
    public static Optional<SlotResult> findFirst(ICuriosItemHandler handler, Item item) {
        if (enabled && READY) {
            boolean skip;
            try {
                skip = onlyReads(handler);
            } catch (Throwable t) {
                skip = false;
            }
            if (skip) {
                if (SHADOW) {
                    Optional<SlotResult> r = handler.findFirstCurio(item);
                    SHADOW_CHECKS.incrementAndGet();
                    boolean still;
                    try {
                        still = onlyReads(handler);
                    } catch (Throwable t) {
                        still = false;
                    }
                    if (!still) mismatch("findFirstCurio changed the handler (a pending update appeared)");
                    return r;
                }
                findSkipped++;
                announce();
                return Optional.empty();
            }
        }
        return handler.findFirstCurio(item);
    }

    /** In place of hasItemImpl's new ResourceLocation("curios", id): the kept key's own location for the slot id. */
    public static ResourceLocation location(String namespace, String path) {
        if (enabled && READY && "curios".equals(namespace)) {
            TagKey<Item> key;
            try {
                key = (TagKey<Item>) SLOT_TAG.invokeExact(path);
            } catch (RuntimeException | Error e) {
                throw e;                                   // the original expression's own exception (invalid id)
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
            if (SHADOW) {
                ResourceLocation original = new ResourceLocation(namespace, path);
                TagKey<Item> theirs = ItemTags.create(original);
                SHADOW_CHECKS.incrementAndGet();
                if (theirs != key) mismatch("slot '" + path + "': original key " + theirs + ", kept " + key + (theirs.equals(key) ? " (equal, not the same instance)" : ""));
                return original;
            }
            last = key;
            return key.f_203868_();                       // TagKey.location() (record accessor, SRG name)
        }
        return new ResourceLocation(namespace, path);
    }

    /** In place of hasItemImpl's ItemTags.create(location): the key whose own location this is, else ItemTags.create. */
    public static TagKey<Item> tag(ResourceLocation location) {
        TagKey<Item> key = last;
        if (key != null && key.f_203868_() == location) {
            keysReused++;
            return key;
        }
        return ItemTags.create(location);
    }

    private static void mismatch(String what) {
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: l2hostility_curio_check_lean SHADOW MISMATCH {}: {}", n, what);
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2hostility_curio_check_lean: L2 Hostility's curio checks skip the unused findFirstCurio walk and reuse Curios slot tag keys");
        }
    }
}
