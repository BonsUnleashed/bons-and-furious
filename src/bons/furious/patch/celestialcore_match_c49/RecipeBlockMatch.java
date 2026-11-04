package bons.furious.patch.celestialcore_match_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch celestialcore_transformation_recipe_match (Celestial Core 1.6.1, also 1.5.8; both sides, the
 * handler acts on the server; kind change, a gameplay fix that is off by default). Celestial Core is LGPL-2.1; none of its
 * code is carried. Ours, idea from Collections Of Optimizations (celestialcore.matchStatBlockRecipe), idea text only.
 *
 * What Celestial Core does: its ItemEntityMixin.celestial_core$tick$recipe (merged into ItemEntity, run at the end of
 * every server tick of every dropped item) asks RecipeManager.getRecipeFor(celestial_core:transformation, container,
 * level). The transformation recipe's matches() always answers true, so the lookup hands back the FIRST recipe of the
 * type, and only then does the handler's lambda look at the block under the item (1.6.x: the recipe's "fluid" block at
 * getOnPos(); 1.5.x: the recipe's "stat" block at getOnPos() or the block below it) and give up when it is not that
 * recipe's block. So in a pack with two or more transformation recipes (a datapack's or KubeJS' next to Celestial Core's
 * own) only the first one can ever fire.
 *
 * What the switch changes (only for that one lookup): while the handler's getRecipeFor runs ({@link #lookup}, a
 * WrapOperation on that call), a transformation recipe whose block is not under the item answers false from matches()
 * ({@link #blockUnder}, a ModifyReturnValue on the recipe class's matches). So the lookup, through whatever selection the
 * recipe manager and other mods' hooks on getRecipeFor make among matching recipes, hands back the first recipe whose
 * block the item is lying on, and the handler goes on exactly as before with that recipe (inputs, output, the block it
 * leaves behind). Recipes of other classes under the same type keep their own matches(). Every other caller of matches()
 * (recipe viewers, other lookups, other threads) is unchanged: outside the handler's lookup on the asking thread the scope
 * is inactive. The block reads are the ones the lambda makes, in its order, made once per lookup when the first
 * transformation recipe is tested.
 *
 * The scope: one shared object claimed (compare-and-set) by the thread whose lookup runs, the server thread in practice,
 * or a per-thread object when another thread holds the shared one (counted, so an idle matches() reads two volatile
 * fields and returns). A lookup nested inside another on the same thread shares the outer one's scope.
 *
 * Flag: -Dbons_and_furious.celestialcoreTransformationRecipeMatch=false runs the lookup as shipped.
 */
public final class RecipeBlockMatch {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.celestialcoreTransformationRecipeMatch", "true"));
    /** Lookups made with the scope active (for probes and the harness; a plain counter, not exact under threads). */
    public static long lookups;
    private static final Scope SHARED = new Scope();
    private static final AtomicReference<Thread> OWNER = new AtomicReference<>();
    /** Lookups running on per-thread scopes (while another thread holds the shared one). */
    private static final AtomicInteger OTHERS = new AtomicInteger();
    private static final ThreadLocal<Scope> OTHER = ThreadLocal.withInitial(Scope::new);
    private static volatile boolean announced;

    private static final class Scope {
        boolean active;
        ItemEntity item;
        Level level;
        BlockState on, below;
    }

    private RecipeBlockMatch() {
    }

    /** The handler's getRecipeFor call (wrapped): the lookup runs with this item as the scope of the recipes' matches(). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Optional lookup(ItemEntity item, RecipeManager manager, RecipeType type, Container container, Level level, Operation<Optional> original) {
        if (!enabled) return original.call(manager, type, container, level);
        Thread me = Thread.currentThread();
        Thread owner = OWNER.get();
        if (owner == me) return original.call(manager, type, container, level);   // nested: the outer scope stays
        boolean shared = owner == null && OWNER.compareAndSet(null, me);
        Scope s = shared ? SHARED : OTHER.get();
        if (!shared && s.active) return original.call(manager, type, container, level);   // nested: the outer scope stays
        s.active = true;
        s.item = item;
        s.level = level;
        s.on = null;
        s.below = null;
        if (!shared) OTHERS.incrementAndGet();
        lookups++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: celestialcore_transformation_recipe_match applies (a dropped item's transformation lookup picks the first recipe whose block it lies on)");
        }
        try {
            return original.call(manager, type, container, level);
        } finally {
            s.active = false;
            s.item = null;
            s.level = null;
            s.on = null;
            s.below = null;
            if (shared) OWNER.set(null);
            else OTHERS.decrementAndGet();
        }
    }

    /**
     * Called from a transformation recipe's matches() (which answered true): false only inside the handler's lookup on this
     * thread, when the recipe's block is not the block the handler's lambda would test (1.6.x: the block at getOnPos();
     * 1.5.x: the block at getOnPos(), then the one below it). Outside that lookup: true (as shipped).
     */
    public static boolean blockUnder(Block block, boolean alsoBelow) {
        Scope s;
        if (OWNER.get() == Thread.currentThread()) {
            s = SHARED;
        } else {
            if (OTHERS.get() == 0) return true;
            s = OTHER.get();
            if (!s.active) return true;
        }
        if (s.on == null) s.on = s.level.m_8055_(s.item.m_20097_());
        if (s.on.m_60713_(block)) return true;
        if (alsoBelow) {
            if (s.below == null) s.below = s.level.m_8055_(s.item.m_20097_().m_7495_());
            if (s.below.m_60713_(block)) return true;
        }
        return false;
    }
}
