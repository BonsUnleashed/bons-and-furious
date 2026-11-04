package bons.furious.patch.fallentraits_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.xkmc.l2hostility.content.item.traits.SealedItem;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fallentraits_root_slot_list_lazy (Fallen Traits & Relics 1.0.0, LGPL-2.1-or-later, with L2
 * Hostility 2.5.19 and Curios; server side: the curio tick returns on client levels). Ours. Idea: Collections Of
 * Optimizations (fallentraits.skipIdleRootSlotList), idea text only.
 *
 * YggdrasilRoot.curioTick starts every tick with CurioCompat.getItemAccess(wearer): a new list of six equipment-slot and one
 * curio-slot access object per equipped curio slot (Curios' capability, every slot type's equipped stacks). The list is
 * read only in the branch that starts unsealing (no YggdrasilRoot tag on the stack), where each slot is asked whether it
 * holds a SealedItem; the tag branch (unsealing in progress) never reads it.
 *
 * itemAccess() answers an empty list instead of building it when that changes nothing: (a) the stack has the YggdrasilRoot
 * tag and a Curios walk over the wearer now would only read (no pending slot-count update: CuriosPresence.quiet), or (b)
 * none of the six equipment slots holds a SealedItem and Curios' layer is certain that no equipped curio slot does
 * (CuriosPresence.wornAny == 0, which also means no pending update), so the loop would find nothing in any branch. Both
 * CuriosPresence calls go through method handles (bons.furious.patch.curios_presence.CuriosPresence, the Curios presence
 * layer); absent, off or unsure: the list is built as shipped. getItemAccess only reads (equipment slots, the inventory
 * lookup, getStacks without a pending update) and allocates, so leaving it out changes nothing.
 *
 * Flag: -Dbons_and_furious.fallentraitsRootSlotListLazy=false.
 */
public final class FtRoot {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fallentraitsRootSlotListLazy", "true"));
    private static final String OWNER = "bons.furious.patch.curios_presence.CuriosPresence";
    private static final MethodHandle QUIET = find("quiet", MethodType.methodType(boolean.class, LivingEntity.class));
    private static final MethodHandle WORN_ANY = find("wornAny", MethodType.methodType(int.class, LivingEntity.class, Predicate.class));
    private static final Predicate<Item> SEALED = item -> item instanceof SealedItem;
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static volatile boolean announced;

    private FtRoot() {
    }

    private static MethodHandle find(String name, MethodType type) {
        try {
            return MethodHandles.lookup().findStatic(Class.forName(OWNER, true, FtRoot.class.getClassLoader()), name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: fallentraits_root_slot_list_lazy cannot reach {}.{} ({}); the Yggdrasil Root builds its slot list as shipped", OWNER, name, e.toString());
            return null;
        }
    }

    private static boolean quiet(LivingEntity e) {
        try {
            return QUIET != null && (boolean) QUIET.invokeExact(e);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean noSealedCurio(LivingEntity e) {
        try {
            return WORN_ANY != null && (int) WORN_ANY.invokeExact(e, SEALED) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the slot list of this tick would not change anything (see the class comment). */
    public static boolean unused(LivingEntity wearer, ItemStack stack) {
        if (!enabled || wearer == null || stack == null) return false;
        CompoundTag tag = stack.m_41783_();
        if (tag != null && tag.m_128441_("YggdrasilRoot")) return quiet(wearer);
        for (EquipmentSlot slot : SLOTS) {
            if (wearer.m_6844_(slot).m_41720_() instanceof SealedItem) return false;
        }
        return noSealedCurio(wearer);
    }

    /** The wrapped CurioCompat.getItemAccess(wearer) of YggdrasilRoot.curioTick. */
    @SuppressWarnings({"rawtypes"})
    public static List itemAccess(LivingEntity wearer, Operation<List> original, ItemStack stack) {
        if (!unused(wearer, stack)) return original.call(wearer);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fallentraits_root_slot_list_lazy applies (the Yggdrasil Root builds its slot list only when it can use it)");
        }
        return List.of();
    }
}
