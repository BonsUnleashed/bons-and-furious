package bons.furious.patch.compositematerial_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch compositematerial_heal_scan_player_gate (Composite Material 1.3.0, mod id composite_material;
 * both sides, server levels only). Composite Material's code is MIT; none of it is carried (the gate repeats its one-line
 * blocking test). Ours, idea from Collections Of Optimizations (compositematerial.healPlayersOnly), idea text only.
 *
 * What Composite Material does: HealEvents.onLivingHeal (every LivingHealEvent: any living entity's heal, the regeneration
 * ticks of mobs and players included) searches the box of 16 blocks around the healed entity for every living entity
 * (level.getEntitiesOfClass(LivingEntity.class, box)) and acts only on the players among them that hold the Dungeon Shield
 * in a hand and are blocking with it (isUsingItem, the use item the shield), and only when the healed entity is not itself
 * such a player: that player is healed by the amount, a sound plays and the heal is cancelled.
 *
 * What the switch does ({@link #decide}, a Redirect of that one search): on a server level whose player list holds no
 * player whose use item is the Dungeon Shield (a blocker's use item is the shield, so nobody there can block with it), or
 * when the healed entity is itself a player blocking with it, the loop can act on nobody, so the search is not made and the
 * loop gets an empty list; otherwise the search is made for Player.class ({@link #PLAYERS}: the same players in the same
 * order, the loop's other elements only ever read the shield's registry object). Why that is the same:
 *  - a server level's player list holds every player in its entity storage. A player enters the storage only through
 *    PersistentEntitySectionManager.addEntityWithoutEvent, and every player is always ticking, so the add tracks it at once
 *    and the level lists a ServerPlayer at the end of that tracking start; it leaves the list only after it has left the
 *    storage (removal), never on a chunk or section change. While a player's tracking start runs the switch steps aside
 *    ({@link #trackingStart}, a Redirect of that one call: the player is in the storage, not yet listed, and other mods'
 *    tracking handlers run there; before it only vanilla bookkeeping runs: setLevelCallback, the empty onCreated); when
 *    the added player is not on its level's list afterwards (a player class the level does not list, another mod's veto)
 *    or the tracking start throws, it steps aside for the rest of the session. The gate also stands aside until that
 *    redirect has run once ({@link #watching}): when another mod redirects Forge's entity-tracking call (ours has
 *    priority 500, below the default), Mixin skips ours with a warning (require 0, so the game starts), our handler
 *    never runs, and the gate never answers;
 *  - its test is the handler's own (the hand stacks, isUsingItem, the use item, ItemStack.is): reads only, also with the
 *    other mods' mixins on them (Better Combat, BadOptimizations, BCLib, ModernFix); the loop decides every element with
 *    the same reads before it acts, and the skipped search only reads (the profiler's "getEntities" counter aside);
 *  - a level of another class (client levels, subclasses), a player's tracking start in progress and a stepped-aside
 *    session take the players-only search (it needs no player list); an unregistered shield (the loop's
 *    RegistryObject.get throws on the first element) and the switch flag off take the shipped search.
 *
 * Flag: -Dbons_and_furious.compositematerialHealScanPlayerGate=false makes every search as shipped.
 */
public final class HealScanGate {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.compositematerialHealScanPlayerGate", "true"));
    /** Players being added to an entity storage right now (in the storage, not yet on the level's player list). */
    private static final AtomicInteger ADDING = new AtomicInteger();
    /** Our startTracking redirect has run (set on the first entity add it sees). Until then, and for good when another
     *  mod's redirect of that call displaced ours, the gate stands aside: without the add watch it cannot see a player's
     *  arrival window. */
    private static volatile boolean watched;
    /** Why the gate stepped aside for the rest of the session (null while it is active). */
    private static volatile String steppedAside;
    /** Searches skipped and searches made (plain counters for probes and the harness, not exact under threads). */
    public static long skipped, searched;
    private static volatile boolean announced;

    private HealScanGate() {
    }

    /** The handler's search as shipped (getEntitiesOfClass(LivingEntity.class, box)). */
    public static final int SHIPPED = 0;
    /** The same search for Player.class: the handler's loop acts on players only, and a section's per-class list is the
     *  players of its LivingEntity list in the same order (vanilla ClassInstanceMultiMap and Radium's find both filter
     *  the one insertion-ordered list), so the loop sees the same players in the same order. */
    public static final int PLAYERS = 1;
    /** No search: an empty list (the loop could act on no element). */
    public static final int NONE = 2;

    /** For HealEvents.onLivingHeal's search around {@code healed} on {@code level}: what it becomes. */
    public static int decide(Level level, LivingEntity healed, RegistryObject<Item> shield) {
        if (!enabled || shield == null || !shield.isPresent()) {   // unregistered: the loop's get() throws on any element
            searched++;
            return SHIPPED;
        }
        if (!watched || steppedAside != null || ADDING.get() != 0 || level == null || level.getClass() != ServerLevel.class) {
            searched++;   // the player list cannot answer for this level now; the players-only search needs no list
            return PLAYERS;
        }
        Item item = shield.get();
        if (!(healed instanceof Player self && self.m_21211_().m_150930_(item) && blocking(self, item))) {
            for (Player p : level.m_6907_()) {
                if (p.m_21211_().m_150930_(item)) {
                    searched++;
                    return PLAYERS;
                }
            }
        }
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: compositematerial_heal_scan_player_gate applies (Composite Material's heal handler searches for blockers only while a player uses the Dungeon Shield, and then for players only)");
        }
        return NONE;
    }

    /** The empty list the loop gets instead of the search. */
    @SuppressWarnings("rawtypes")
    public static List none() {
        return Collections.emptyList();
    }

    /** HealEvents' test, in its order: the shield in the main or the off hand, using an item, the use item the shield. */
    static boolean blocking(Player p, Item shield) {
        return (p.m_6844_(EquipmentSlot.MAINHAND).m_150930_(shield) || p.m_6844_(EquipmentSlot.OFFHAND).m_150930_(shield))
                && p.m_6117_() && p.m_21211_().m_150930_(shield);
    }

    /** From our startTracking redirect, for every entity: the add watch is in place (one volatile write, then reads). */
    public static void watching() {
        if (!watched) watched = true;
    }

    /** Before a player's tracking start inside PersistentEntitySectionManager.addEntityWithoutEvent (the player is in the
     *  storage, the level lists it at the end of its onTrackingStart). */
    public static void trackingStart() {
        ADDING.incrementAndGet();
    }

    /** After that tracking start ({@code returned}: it did not throw): the player must be on its level's list now. */
    public static void trackingEnd(Player p, boolean returned) {
        ADDING.decrementAndGet();
        if (!returned) stepAside("adding a player of class " + p.getClass().getName() + " to a level's entities failed");
        else if (!(p.m_9236_() instanceof ServerLevel level && level.m_6907_().contains(p)))
            stepAside("a player of class " + p.getClass().getName() + " entered a level's entities without joining its player list");
    }

    private static void stepAside(String why) {
        if (steppedAside != null) return;
        steppedAside = why;
        LOGGER.info("Bons and Furious: compositematerial_heal_scan_player_gate steps aside for this session ({}); Composite Material's heal handler searches as shipped", why);
    }
}
