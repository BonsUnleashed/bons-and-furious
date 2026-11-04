package bons.furious.patch.moreartifacts_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * Bons and Furious switch moreartifacts_equip_state_single_pass (More Artifacts 1.5.5, All Rights Reserved; both sides).
 * Ours; no More Artifacts code is carried. Idea: Collections Of Optimizations (moreartifacts.fastEquipState), idea text only.
 *
 * <p>DamageCalculator.getCurrentEquipState (asked by DamageEvents.onTick for every player, both tick phases, both sides)
 * walks its list of 49 artifacts and asks CurioHandler.isCurioEquipped(player, artifact) for each: a Curios inventory
 * resolve, an Optional, a capturing lambda and a findFirstCurio walk over every equipped curio slot, per artifact; then it
 * checks the three shields in the hands. The answer of isCurioEquipped is "some non-empty equipped stack's item is the
 * artifact".
 *
 * <p>When the shared Curios layer says the player's Curios walk would only read (Curios' own inventory classes, no pending
 * slot update: CuriosPresence.quiet), nothing that 49 walks do can change anything, and they all see the same stacks. Then
 * the equipped items are read once, and the method's own artifact loop is handed an iterator over only the artifacts that
 * are worn, in the list's order: it asks isCurioEquipped for those (true, as before) and puts them in the same order;
 * the artifacts it is not handed are exactly those whose answer is false, which the original only skips. The shield
 * loop, the map and every put are the method's own. Otherwise (a pending update, another inventory implementation, the
 * Curios layer off) the loop gets the list's own iterator: the shipped behaviour.
 *
 * <p>The wrapper sits on the value of the list's iterator() call (MixinExtras @ModifyExpressionValue, the method's Player
 * argument captured by value): on the single pass it reads the list's own iterator to keep the worn artifacts in the
 * list's order (iterating a list reads only), otherwise it hands that iterator back untouched.
 * Runtime flag: -Dbons_and_furious.moreartifactsEquipStateSinglePass=false.
 */
public final class MaEquipState {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.moreartifactsEquipStateSinglePass", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** CuriosPresence.quiet(ICuriosItemHandler): a walk over this handler now applies no pending update and only reads. */
    private static final MethodHandle QUIET = find("bons.furious.patch.curios_presence.CuriosPresence", "quiet",
            MethodType.methodType(boolean.class, ICuriosItemHandler.class));
    /** Curios' own inventory class (the only one the layer can call read-only). */
    private static final Class<?> WRAPPER = CurioInventoryCapability.CurioInventoryWrapper.class;
    private static volatile boolean announced;
    /** Statistics (plain, racy): equip states answered from one read of the slots. */
    public static long singlePass;

    private MaEquipState() {
    }

    private static MethodHandle find(String owner, String name, MethodType type) {
        try {
            return MethodHandles.lookup().findStatic(Class.forName(owner, false, MaEquipState.class.getClassLoader()), name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: moreartifacts_equip_state_single_pass cannot reach {}.{} ({}); the stock code runs", owner, name, e.toString());
            return null;
        }
    }

    static boolean quiet(ICuriosItemHandler handler) {
        if (QUIET == null) return false;
        try {
            return (boolean) QUIET.invokeExact(handler);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The value of the artifact list's iterator() in getCurrentEquipState (see the class comment). One inventory lookup:
     * none -> no artifact can be worn (every isCurioEquipped answers false and walks nothing) -> an empty iterator;
     * another inventory implementation, a pending slot update or the layer off -> the list's own iterator at once.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator items(Iterator artifacts, Player player) {
        if (!enabled || player == null || artifacts == null) return artifacts;
        LazyOptional<ICuriosItemHandler> inventory = CuriosApi.getCuriosInventory(player);
        Item[] worn;
        if (!inventory.isPresent()) worn = NONE;
        else {
            ICuriosItemHandler handler = inventory.orElse(null);
            if (handler == null || handler.getClass() != WRAPPER || !quiet(handler)) return artifacts;
            worn = worn(handler);
        }
        ArrayList<Object> kept = new ArrayList<>(Math.min(worn.length, 64));
        if (worn.length > 0) {
            while (artifacts.hasNext()) {
                Object o = artifacts.next();
                for (Item w : worn) {
                    if (w == o) {
                        kept.add(o);
                        break;
                    }
                }
            }
        }
        singlePass++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: moreartifacts_equip_state_single_pass applies (More Artifacts reads a player's curio slots once per equip check)");
        }
        return kept.iterator();
    }

    private static final Item[] NONE = new Item[0];

    /** The items of every non-empty equipped (not cosmetic) curio stack of a read-only handler, read once (duplicates
     *  kept; order irrelevant). */
    static Item[] worn(ICuriosItemHandler handler) {
        Item[] out = NONE;
        int n = 0;
        for (Map.Entry<String, ICurioStacksHandler> e : handler.getCurios().entrySet()) {
            IDynamicStackHandler stacks = e.getValue().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack s = stacks.getStackInSlot(i);
                if (s.m_41619_()) continue;
                if (n == out.length) out = java.util.Arrays.copyOf(out, Math.max(8, n * 2));
                out[n++] = s.m_41720_();
            }
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }
}
