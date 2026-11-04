package bons.furious.patch.celestialcore_precheck_c49;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch celestialcore_transform_lookup_precheck (Celestial Core 1.6.1, also 1.5.8; both sides, the handler
 * acts on the server). Celestial Core is LGPL-2.1; no Celestial Core code is carried (its classes are reached by name, so
 * this helper loads without them). Ours, idea from Collections Of Optimizations (celestialcore.leanItemTransformScan), idea
 * text only.
 *
 * What Celestial Core does: its ItemEntityMixin.celestial_core$tick$recipe runs at the end of every ItemEntity.tick on the
 * server. Each run builds a 9-slot container and asks RecipeManager.getRecipeFor(celestial_core:transformation, container,
 * level), a stream over every transformation recipe whose matches() is always true, so it answers the first recipe of the
 * type (or nothing when there is none). Only then does the recipe's lambda look at the block under the item: 1.6.x reads
 * level.getBlockState(item.getOnPos()) and returns unless that block is the recipe's "fluid" block; 1.5.x reads getOnPos()
 * and then getOnPos().below() and returns unless one of them is the recipe's "stat" block. Every dropped item on the server
 * pays the container, the recipe stream and the lambda on every tick, and nearly always ends there.
 *
 * What the switch does ({@link #nothingCanHappen}, asked in place of the handler's level.isClientSide() at its first
 * return): it keeps the set of every transformation recipe's block for the recipe map RecipeManager.byType returns (a new
 * map on every reload; the set is rebuilt when the map object or its size changes). With no recipe, the lookup answers
 * nothing and the handler does nothing, so it returns at once (no block is read, as before). Otherwise it makes the very
 * reads the lambda makes, in the same order (getOnPos and the block there; 1.5.x also getOnPos().below() and the block
 * there), and returns at once when none of those blocks is in the set: whatever recipe the lookup would have answered, its
 * block is in the set, so the lambda would have returned at the same point. When a block is in the set, or the map holds a
 * recipe of another class, Celestial Core's handler runs as shipped (its reads then repeat ours; a repeated read of the
 * chunk just read is a pure cache hit).
 *
 * Skipped with the lookup: RecipeManager.getRecipeFor and the hooks other mods put on it. The ones in this pack were
 * reviewed (guard lines marked audit): BCLib's sort (picks among the same recipes), Kiwi's (stonecutting only) and
 * Polymorph's (block-entity containers only) do nothing for this container. Assumption: no mod invents transformation
 * recipes inside getRecipeFor that are not in the recipe manager, and no code changes a loaded recipe's block field.
 *
 * Flag: -Dbons_and_furious.celestialcoreTransformLookupPrecheck=false runs the handler in full on every tick.
 */
public final class TransformPrecheck {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.celestialcoreTransformLookupPrecheck", "true"));
    /** Handler runs the switch ended at the precheck (for probes and the harness). */
    public static final AtomicLong SKIPPED = new AtomicLong();
    /** The recipe class of the installed build (1.6.x FluidTransformationRecipe, 1.5.x TransformationRecipe), or null. */
    private static final Class<?> RECIPE;
    /** Its block field: 1.6.x "fluid", 1.5.x "stat". */
    private static final Field BLOCK;
    /** 1.5.x: the lambda also reads the block below getOnPos(). */
    private static final boolean TWO_READS;
    /** CCRecipes.RT_TRANSFORMATION (a Registrate RegistryEntry, a Supplier of the recipe type). */
    private static final Field TYPE_ENTRY;
    private static volatile RecipeType<?> type;
    private static volatile Snapshot snapshot;
    private static volatile boolean announced;

    /** RecipeManager.byType, through the group's invoker on RecipeManager. */
    public interface ByType {
        Map<ResourceLocation, ?> bons$celestialcore_precheck_c49ByType(RecipeType<?> type);
    }

    /** The recipe blocks of one recipe map (identity compare: Block keeps Object's equals). */
    private record Snapshot(Map<?, ?> map, int size, Block[] blocks, boolean foreign) {}

    static {
        Class<?> recipe = null;
        Field block = null, entry = null;
        boolean two = false;
        ClassLoader loader = TransformPrecheck.class.getClassLoader();
        try {
            try {
                recipe = Class.forName("com.xiaoyue.celestial_core.content.recipes.FluidTransformationRecipe", false, loader);
                block = recipe.getField("fluid");
            } catch (ClassNotFoundException newer) {
                recipe = Class.forName("com.xiaoyue.celestial_core.content.recipes.TransformationRecipe", false, loader);
                block = recipe.getField("stat");
                two = true;
            }
            entry = Class.forName("com.xiaoyue.celestial_core.register.CCRecipes", false, loader).getField("RT_TRANSFORMATION");
            if (block.getType() != Block.class) throw new IllegalStateException("recipe block field is " + block.getType());
        } catch (Throwable t) {
            recipe = null;
            block = null;
            entry = null;
        }
        RECIPE = recipe;
        BLOCK = block;
        TWO_READS = two;
        TYPE_ENTRY = entry;
    }

    private TransformPrecheck() {
    }

    private static RecipeType<?> type() {
        RecipeType<?> t = type;
        if (t != null) return t;
        try {
            Object e = TYPE_ENTRY.get(null);
            Object v = e instanceof Supplier<?> s ? s.get() : null;
            if (v instanceof RecipeType<?> rt) type = t = rt;
        } catch (Throwable notYet) {
            // registry not ready: the handler runs as shipped
        }
        return t;
    }

    private static Snapshot build(Map<?, ?> map) {
        List<Block> blocks = new ArrayList<>();
        for (Object r : map.values()) {
            if (r == null || r.getClass() != RECIPE) return new Snapshot(map, map.size(), new Block[0], true);
            Block b;
            try {
                b = (Block) BLOCK.get(r);
            } catch (Throwable t) {
                return new Snapshot(map, map.size(), new Block[0], true);
            }
            if (b != null && !blocks.contains(b)) blocks.add(b);
        }
        return new Snapshot(map, map.size(), blocks.toArray(new Block[0]), false);
    }

    private static boolean in(Block[] blocks, Block b) {
        for (Block x : blocks) if (x == b) return true;
        return false;
    }

    /**
     * True when Celestial Core's transformation handler can do nothing for this item on this tick (it then returns at its
     * first return, before the container and the recipe lookup). False: the handler runs as shipped.
     */
    public static boolean nothingCanHappen(ItemEntity item) {
        if (!enabled || RECIPE == null) return false;
        Level level = item.m_9236_();
        RecipeManager recipes = level.m_7465_();
        if (!(recipes instanceof ByType byType)) return false;
        RecipeType<?> t = type();
        if (t == null) return false;
        Map<ResourceLocation, ?> map = byType.bons$celestialcore_precheck_c49ByType(t);
        if (map == null) return false;
        Snapshot s = snapshot;
        if (s == null || s.map() != map || s.size() != map.size()) snapshot = s = build(map);
        if (s.foreign()) return false;
        if (s.size() > 0) {
            // a recipe exists, so the lambda runs and reads: make its reads, in its order (a recipe without a block reads too)
            BlockPos at = item.m_20097_();
            BlockPos below = TWO_READS ? item.m_20097_().m_7495_() : null;
            if (in(s.blocks(), level.m_8055_(at).m_60734_())) return false;
            if (TWO_READS && in(s.blocks(), level.m_8055_(below).m_60734_())) return false;
        }
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: celestialcore_transform_lookup_precheck applies (dropped items skip Celestial Core's transformation recipe lookup when no recipe's block is under them)");
        }
        return true;
    }
}
