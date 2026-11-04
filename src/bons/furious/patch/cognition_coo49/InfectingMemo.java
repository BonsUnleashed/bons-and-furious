package bons.furious.patch.cognition_coo49;

import com.cyanogen.experienceobelisk.recipe.InfectingRecipe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch cognition_infecting_recipe_memo (Cognition / experienceobelisk 2.4.12, both sides; exact;
 * helper of InfectingRecipeMemoMixin; no Cognition code carried).
 *
 * What Cognition does. InfectingRecipe.getInfectedBlockState(level, state) (6 calls per infected bookshelf or agar block per
 * spread attempt, once a second, both sides, and once per bibliophage use) builds state.getBlock().asItem()'s default stack,
 * walks level.getRecipeManager().getAllRecipesFor(infecting type) (a copy of the list) testing each recipe's ingredient
 * against that stack in a new one-slot container, and answers the first match's result block's default state when the
 * result is a block item, else null. The answer depends only on the Block (the stack comes from the block alone), the
 * recipe list and Ingredient.test.
 *
 * What the switch does. Per RecipeManager it keeps Block -> answer (null kept too) while the recipe generation token of the
 * vanilla_cooking_recipe_item_index layer (RecipeGenerationToken, reached by name) stays current: the same recipe table
 * object, no tag rebind since (TagsUpdatedEvent), Forge's ingredient invalidation counter unchanged. The memo is kept only
 * while every recipe of the infecting type is a plain InfectingRecipe (checked once per token: another class could match
 * differently); otherwise, or when the token is unavailable, every call runs as shipped. A memo hit returns the object the
 * original computed (a block's default state is one fixed object). Exact. Runtime flag
 * bons_and_furious.cognitionInfectingRecipeMemo; SHADOW MODE -Dbons_and_furious.cognitionInfectingRecipeMemo.shadow=true runs
 * the original on every hit and compares (identity); SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20, the original's
 * answer is returned.
 */
public final class InfectingMemo {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cognitionInfectingRecipeMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cognitionInfectingRecipeMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): answers from the memo, original calls made. */
    public static long hits, misses;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String TOKEN = "bons.furious.patch.vanilla_cooking_recipe_index.RecipeGenerationToken";
    private static final MethodHandle TOKEN_OF, STILL_CURRENT;
    private static final Object NONE = new Object();
    /** Per RecipeManager (weak: a closed world's manager goes with it); guarded by itself. */
    private static final Map<RecipeManager, Memo> MEMOS = new WeakHashMap<>();
    private static volatile boolean announced;

    static {
        MethodHandle tok = null, cur = null;
        try {
            Class<?> c = Class.forName(TOKEN, false, InfectingMemo.class.getClassLoader());
            MethodHandles.Lookup l = MethodHandles.lookup();
            tok = l.findStatic(c, "tokenOf", MethodType.methodType(Object.class, RecipeManager.class));
            cur = l.findStatic(c, "stillCurrent", MethodType.methodType(boolean.class, RecipeManager.class, Object.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: cognition_infecting_recipe_memo cannot reach the recipe generation token ({}); infecting recipe lookups run as shipped", t.toString());
        }
        TOKEN_OF = tok;
        STILL_CURRENT = cur;
    }

    private static final class Memo {
        final Object token;
        final boolean plain;
        final Map<Block, Object> answers = new IdentityHashMap<>();

        Memo(Object token, boolean plain) {
            this.token = token;
            this.plain = plain;
        }
    }

    private InfectingMemo() {
    }

    private static Object tokenOf(RecipeManager m) {
        try {
            return TOKEN_OF == null ? null : (Object) TOKEN_OF.invokeExact(m);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean current(RecipeManager m, Object token) {
        try {
            return STILL_CURRENT != null && token != null && (boolean) STILL_CURRENT.invokeExact(m, token);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Every recipe of the infecting type is a plain InfectingRecipe (whose test and result are the shipped ones). */
    private static boolean plain(RecipeManager m) {
        try {
            for (Object r : m.m_44013_(InfectingRecipe.Type.INSTANCE)) if (r == null || r.getClass() != InfectingRecipe.class) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** InfectingRecipe.getInfectedBlockState(level, state), wrapped. */
    public static BlockState infected(Level level, BlockState state, Operation<BlockState> original) {
        if (!enabled || level == null || state == null || TOKEN_OF == null) return original.call(level, state);
        RecipeManager manager = level.m_7465_();
        if (manager == null) return original.call(level, state);
        Block block = state.m_60734_();
        Memo memo;
        Object kept;
        synchronized (MEMOS) {
            memo = MEMOS.get(manager);
            if (memo == null || !current(manager, memo.token)) {
                Object token = tokenOf(manager);   // taken before anything the memo answers from is read
                if (token == null) {
                    MEMOS.remove(manager);
                    memo = null;
                } else {
                    memo = new Memo(token, plain(manager));
                    MEMOS.put(manager, memo);
                }
            }
            kept = memo != null && memo.plain ? memo.answers.get(block) : null;
        }
        if (memo == null || !memo.plain) return original.call(level, state);
        if (kept != null) {
            BlockState answer = kept == NONE ? null : (BlockState) kept;
            if (SHADOW) {
                BlockState asked = original.call(level, state);
                SHADOW_CHECKS.incrementAndGet();
                if (asked != answer && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: cognition_infecting_recipe_memo SHADOW MISMATCH for {}: kept {}, asked {}", block, answer, asked);
                return asked;
            }
            hits++;
            return answer;
        }
        BlockState result = original.call(level, state);
        misses++;
        synchronized (MEMOS) {
            if (MEMOS.get(manager) == memo && current(manager, memo.token)) memo.answers.put(block, result == null ? NONE : result);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cognition_infecting_recipe_memo: infecting recipe answers are kept per block until the recipes or tags change");
        }
        return result;
    }
}
