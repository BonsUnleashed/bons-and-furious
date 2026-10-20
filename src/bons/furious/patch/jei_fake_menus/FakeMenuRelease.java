package bons.furious.patch.jei_fake_menus;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import mezz.jei.library.plugins.vanilla.anvil.AnvilHelper;
import mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * Bons and Furious switches jei_anvil_menu_player_release and jei_grindstone_menu_player_release (JEI 15.71.0.224 and
 * 15.59.0.212, the two helper classes are byte-identical in both, client only). [FIX] JEI's two simulation menus no longer
 * keep a replaced player, and through it the old ClientLevel, in memory.
 *
 * JEI's vanilla plugin computes anvil and grindstone results with one fake menu each, kept in a static field:
 * AnvilHelper.ANVIL_MENU = new AnvilMenu(0, new Inventory(Minecraft.player)) and GrindstoneHelper.GRINDSTONE_MENU = new
 * GrindstoneMenu(0, new Inventory(Minecraft.player)), made at JEI's start (its anvil and grindstone recipes) and, for the
 * anvil, used again whenever an anvil recipe is shown (AnvilHelper.findLevelsCost, the level cost). JEI clears both only
 * when its runtime stops (VanillaPlugin.onRuntimeUnavailable, at logout). Each menu holds the player (the menu's player
 * field and the fake inventory's), and the player holds its level. On a dimension change or a respawn the client gets a new
 * LocalPlayer (and on a dimension change a new ClientLevel), so the menus keep the player of JEI's start and its level
 * until the player leaves the server.
 *
 * Here, when Forge reports a new LocalPlayer (ClientPlayerNetworkEvent.Clone: respawn and dimension change) or the player
 * logging out (LoggingOut), a menu whose inventory belongs to another player is released with JEI's own clearCache (the
 * same reset JEI does at logout). JEI builds it again, for the current player and with its own code, the next time it needs
 * it. Recipes JEI builds are unchanged: its start builds them before any respawn can happen, and the deferred recipe lanes
 * (jei_join_recipe_deferral) keep the menu they started with. The one difference: an anvil level cost shown after a respawn
 * comes from a menu built for the current player instead of the replaced one, as after every world join; in this pack the
 * anvil result reads the player only for creative mode (vanilla, Bons Space Program) and its level's client flag (Deeper
 * Darker), so the cost differs only when the respawn changed creative mode.
 *
 * Runtime switches -Dbons_and_furious.jeiAnvilMenuPlayerRelease=false and
 * -Dbons_and_furious.jeiGrindstoneMenuPlayerRelease=false (read at each player change): JEI keeps its menus as shipped.
 * -Dbons_and_furious.jeiAnvilMenuPlayerRelease.shadow=true (verification runs): the first anvil menu released in a JEI
 * session is kept (as JEI would keep it), and every level cost computed afterwards is computed on it too; cost and result
 * must be equal (SHADOW_CHECKS, SHADOW_MISMATCHES, the first 20 logged; expect 0 unless creative mode changed).
 */
public final class FakeMenuRelease {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean anvilEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.jeiAnvilMenuPlayerRelease", "true"));
    public static volatile boolean grindstoneEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.jeiGrindstoneMenuPlayerRelease", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.jeiAnvilMenuPlayerRelease.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Menus released (tests, rigs). */
    public static final AtomicLong RELEASED_ANVIL = new AtomicLong(), RELEASED_GRINDSTONE = new AtomicLong();
    private static volatile WeakReference<AnvilMenu> anvil = new WeakReference<>(null);
    private static volatile WeakReference<AbstractContainerMenu> grindstone = new WeakReference<>(null);
    private static volatile boolean anvilHooked, grindstoneHooked, registered, announcedAnvil, announcedGrindstone, registerFailed;
    /** SHADOW only: the anvil menu JEI would still be using (the first one released since JEI's last start). */
    private static volatile AnvilMenu shadowStock;

    private FakeMenuRelease() {
    }

    /** AnvilHelper.getFakeAnvilMenu, after JEI stored a new menu in ANVIL_MENU. */
    public static void anvilCreated(AnvilMenu menu) {
        anvil = new WeakReference<>(menu);
        anvilHooked = true;
        register();
    }

    /** GrindstoneHelper.getFakeGrindstoneMenu, after JEI stored a new menu in GRINDSTONE_MENU. */
    public static void grindstoneCreated(AbstractContainerMenu menu) {
        grindstone = new WeakReference<>(menu);
        grindstoneHooked = true;
        register();
    }

    private static synchronized void register() {
        if (registered || registerFailed) return;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ClientPlayerNetworkEvent.Clone.class, FakeMenuRelease::onClone);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ClientPlayerNetworkEvent.LoggingOut.class, FakeMenuRelease::onLoggingOut);
            registered = true;
        } catch (RuntimeException | LinkageError e) {
            registerFailed = true;
            LOGGER.warn("Bons and Furious: jei_anvil_menu_player_release / jei_grindstone_menu_player_release could not listen for player changes; "
                    + "JEI's menus stay as shipped", e);
        }
    }

    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        playerChanged(event.getNewPlayer());
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        playerChanged(null);
        shadowStock = null;     // JEI's runtime stops: it clears its menus itself and builds new ones at its next start
    }

    /**
     * A new current player (null: logged out). Every JEI simulation menu whose inventory belongs to another player is released
     * with JEI's own clearCache; JEI makes a new one for the current player when it next needs one. Called on the render thread.
     */
    public static void playerChanged(Player current) {
        if (anvilHooked && anvilEnabled) {
            AnvilMenu m = anvil.get();
            if (m != null && (current == null || ownerOf(m) != current)) {
                AnvilHelper.clearCache();
                anvil = new WeakReference<>(null);
                long n = RELEASED_ANVIL.incrementAndGet();
                if (SHADOW && shadowStock == null && current != null) shadowStock = m;
                if (!announcedAnvil) {
                    announcedAnvil = true;
                    LOGGER.info("Bons and Furious: jei_anvil_menu_player_release applies (JEI's fake anvil menu no longer keeps the previous player and "
                            + "its level; released {} so far){}", n, SHADOW ? " - shadow verification on" : "");
                }
            }
        }
        if (grindstoneHooked && grindstoneEnabled) {
            AbstractContainerMenu m = grindstone.get();
            if (m != null && (current == null || ownerOf(m) != current)) {
                GrindstoneRecipeMaker.clearCache();
                grindstone = new WeakReference<>(null);
                long n = RELEASED_GRINDSTONE.incrementAndGet();
                if (!announcedGrindstone) {
                    announcedGrindstone = true;
                    LOGGER.info("Bons and Furious: jei_grindstone_menu_player_release applies (JEI's fake grindstone menu no longer keeps the previous "
                            + "player and its level; released {} so far)", n);
                }
            }
        }
    }

    /** The player a JEI simulation menu was built for: the owner of its (fake) player inventory, or null. */
    public static Player ownerOf(AbstractContainerMenu menu) {
        for (Slot slot : menu.f_38839_) {
            Container c = slot.f_40218_;
            if (c instanceof Inventory inventory) return inventory.f_35978_;
        }
        return null;
    }

    /** AnvilHelper.findLevelsCost, SHADOW only: the same inputs on the menu JEI would still be using must give the same answer. */
    public static int shadowCost(ItemStack left, ItemStack right, int cost) {
        AnvilMenu stock = shadowStock;
        AnvilMenu current = anvil.get();
        if (stock == null || current == null || current == stock) return cost;
        ItemStack currentResult = current.m_38853_(2).m_7993_().m_41777_();
        AnvilMenu result = AnvilHelper.setAnvilMenu(stock, left, right);
        int stockCost = result == null ? -1 : result.m_39028_();
        ItemStack stockResult = result == null ? ItemStack.f_41583_ : result.m_38853_(2).m_7993_();
        SHADOW_CHECKS.incrementAndGet();
        if (stockCost != cost || !ItemStack.m_41728_(currentResult, stockResult)) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20)
                LOGGER.warn("Bons and Furious: jei_anvil_menu_player_release shadow mismatch #{}: {} + {} cost {} (result {}) on the current player's menu, "
                        + "{} (result {}) on JEI's earlier menu", n, left, right, cost, currentResult, stockCost, stockResult);
        }
        return cost;
    }
}
