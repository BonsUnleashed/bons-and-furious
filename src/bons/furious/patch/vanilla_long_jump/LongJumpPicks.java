package bons.furious.patch.vanilla_long_jump;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.entity.ai.behavior.LongJumpToPreferredBlock;
import net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_long_jump_weighted_pick (Minecraft 1.20.1 on Forge 47.4.16; server side, including the
 * integrated server). SRG member names.
 *
 * What vanilla does. A goat (LongJumpToRandomPos) or frog (LongJumpToPreferredBlock) about to long-jump collects every
 * position in a box around it as a weighted candidate (goats: 11 x 11 x 11 - 1 = 1,330 candidates, weight = the rounded-up
 * squared distance) and then, in pickCandidate, draws candidates one at a time until one is a usable landing spot:
 * getJumpCandidate (m_213675_) is WeightedRandom.getRandomItem(level.random, jumpCandidates) followed by
 * jumpCandidates.remove(picked). getRandomItem sums every weight (getTotalWeight), draws nextInt(total) and walks the list
 * until the running sum passes the draw; remove(Object) searches the list again and shifts it. Each draw is three passes
 * over the list, and pickCandidate can draw hundreds of candidates in one tick (every spot that is not solid ground or
 * not walkable is rejected), so a goat on a steep slope can spend milliseconds in one tick on the passes alone; a frog that
 * wants its preferred blocks drains the whole list in one getJumpCandidate call.
 *
 * What the switch does. For the two vanilla classes (exact class: LongJumpToRandomPos for goats, LongJumpToPreferredBlock
 * for frogs, which calls the same method through super), getJumpCandidate keeps, next to the candidate list, a Fenwick
 * (binary indexed) tree of the candidates' weights and of their presence, in the list's order, built from the list on the
 * first draw after start() assigned it. A draw then reads the total from the tree, draws nextInt(total) from the same
 * random source once (none when the total is 0, as vanilla), finds the candidate where the running sum passes the draw by a
 * tree search, removes it from the list by index (the same element remove(Object) finds: candidates are distinct objects),
 * and updates the trees.
 *
 * Why the result is identical. Same draws (one nextInt(total) per non-empty draw, with total = the sum vanilla's
 * getTotalWeight computes), same candidate (the first in list order whose cumulative weight exceeds the draw, which is
 * what getWeightedItem returns), same list afterwards (that element removed, order kept), so pickCandidate, the frog's
 * loop and everything downstream see what vanilla shows them. The trees are only trusted while the list is the same object
 * with the size they expect and the element they point at is the candidate they recorded; otherwise this draw is answered
 * by WeightedRandom.getWeightedItem on the list with the draw already made (exactly vanilla's code path from there) and
 * the trees are rebuilt next time. Other classes extending LongJumpToRandomPos keep the original method.
 *
 * -Dbons_and_furious.longJumpWeightedPick=false switches it off at run time.
 * -Dbons_and_furious.longJumpWeightedPick.shadow=true (verification runs only): every tree pick is also computed the
 * vanilla way (getWeightedItem on a copy of the list with the same draw) and compared (SHADOW_CHECKS /
 * SHADOW_MISMATCHES, the first 20 mismatches logged).
 */
public final class LongJumpPicks {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.longJumpWeightedPick", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.longJumpWeightedPick.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced, warned;
    private static final java.util.Set<Class<?>> OTHER_CLASSES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Implemented by the mixin: the behaviour's candidate list and its picker. */
    public interface Holder {
        List<LongJumpToRandomPos.PossibleJump> bons$candidates();

        Picker bons$picker();

        void bons$picker(Picker picker);
    }

    private LongJumpPicks() {
    }

    /** LongJumpToRandomPos.getJumpCandidate (m_213675_), wrapped. */
    public static Optional<LongJumpToRandomPos.PossibleJump> pick(Object behaviour, Holder holder, ServerLevel level,
                                                                  Operation<Optional<LongJumpToRandomPos.PossibleJump>> original) {
        Class<?> c = behaviour.getClass();
        if (!enabled) {
            return original.call(level);
        }
        if (c != LongJumpToRandomPos.class && c != LongJumpToPreferredBlock.class) {
            if (OTHER_CLASSES.add(c)) {
                LOGGER.info("Bons and Furious: vanilla_long_jump_weighted_pick leaves {} (a subclass it does not know) to the original long-jump pick", c.getName());
            }
            return original.call(level);
        }
        List<LongJumpToRandomPos.PossibleJump> list = holder.bons$candidates();
        if (!(list instanceof ArrayList)) {
            return original.call(level);
        }
        Picker p = holder.bons$picker();
        if (p == null || p.list != list || p.alive != list.size()) {
            p = Picker.build(list);
            holder.bons$picker(p);
            if (p == null) return original.call(level);       // weights above int range: vanilla's code (and its exception)
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_long_jump_weighted_pick applies (goats and frogs pick long-jump targets without three passes over the candidates per pick){}",
                        SHADOW ? " - shadow verification on" : "");
            }
        }
        if (p.total == 0) {
            return Optional.empty();                           // getRandomItem: total 0 -> empty, no draw
        }
        int draw = level.f_46441_.m_188503_(p.total);
        int pos = p.find(draw);
        int index = p.aliveBefore(pos);
        LongJumpToRandomPos.PossibleJump item = p.items[pos];
        if (index >= list.size() || list.get(index) != item) {
            // the list is not what the trees describe: vanilla's selection with the draw already made
            if (!warned) {
                warned = true;
                LOGGER.warn("Bons and Furious: vanilla_long_jump_weighted_pick found a long-jump candidate list changed by other code; that pick used vanilla's selection (the lists are re-read from now on)");
            }
            holder.bons$picker(null);
            Optional<LongJumpToRandomPos.PossibleJump> o = WeightedRandom.m_146314_(list, draw);
            o.ifPresent(list::remove);
            return o;
        }
        if (SHADOW) shadow(list, draw, item);
        list.remove(index);
        p.removeAt(pos);
        return Optional.of(item);
    }

    private static void shadow(List<LongJumpToRandomPos.PossibleJump> list, int draw, LongJumpToRandomPos.PossibleJump item) {
        SHADOW_CHECKS.incrementAndGet();
        int total = WeightedRandom.m_146312_(list);
        Optional<LongJumpToRandomPos.PossibleJump> vanilla = WeightedRandom.m_146314_(new ArrayList<>(list), draw);
        if (vanilla.isEmpty() || vanilla.get() != item || draw >= total) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: long jump pick shadow mismatch #{}: draw {} of {} picked {} where vanilla picks {}", m, draw, total, item.m_147693_(),
                    vanilla.map(LongJumpToRandomPos.PossibleJump::m_147693_).orElse(null));
        }
    }

    /** Fenwick trees over the candidate list as it was when built (positions never move; removed ones weigh 0). */
    public static final class Picker {
        final List<LongJumpToRandomPos.PossibleJump> list;
        final LongJumpToRandomPos.PossibleJump[] items;
        final int[] weights;
        final int[] weightTree;     // 1-based Fenwick tree of weights
        final int[] countTree;      // 1-based Fenwick tree of presence (1 / 0)
        final int n, top;
        int total, alive;

        private Picker(List<LongJumpToRandomPos.PossibleJump> list, LongJumpToRandomPos.PossibleJump[] items, int[] weights, int total) {
            this.list = list;
            this.items = items;
            this.weights = weights;
            this.n = items.length;
            this.weightTree = new int[this.n + 1];
            this.countTree = new int[this.n + 1];
            for (int i = 0; i < this.n; i++) {
                int k = i + 1;
                this.weightTree[k] += weights[i];
                this.countTree[k] += 1;
                int parent = k + (k & -k);
                if (parent <= this.n) {
                    this.weightTree[parent] += this.weightTree[k];
                    this.countTree[parent] += this.countTree[k];
                }
            }
            this.top = this.n == 0 ? 0 : Integer.highestOneBit(this.n);
            this.total = total;
            this.alive = this.n;
        }

        static Picker build(List<LongJumpToRandomPos.PossibleJump> list) {
            LongJumpToRandomPos.PossibleJump[] items = list.toArray(new LongJumpToRandomPos.PossibleJump[0]);
            int[] weights = new int[items.length];
            long total = 0;
            for (int i = 0; i < items.length; i++) {
                weights[i] = items[i].m_142631_().m_146281_();
                total += weights[i];
                if (weights[i] < 0) return null;
            }
            if (total > Integer.MAX_VALUE) return null;
            return new Picker(list, items, weights, (int) total);
        }

        /** The 0-based position of the first candidate whose cumulative weight exceeds the draw. */
        int find(int draw) {
            int pos = 0, rest = draw;
            for (int step = this.top; step > 0; step >>= 1) {
                int next = pos + step;
                if (next <= this.n && this.weightTree[next] <= rest) {
                    pos = next;
                    rest -= this.weightTree[next];
                }
            }
            return pos;
        }

        /** How many candidates still in the list come before position pos. */
        int aliveBefore(int pos) {
            int sum = 0;
            for (int k = pos; k > 0; k -= k & -k) sum += this.countTree[k];
            return sum;
        }

        void removeAt(int pos) {
            int w = this.weights[pos];
            this.weights[pos] = 0;
            for (int k = pos + 1; k <= this.n; k += k & -k) {
                this.weightTree[k] -= w;
                this.countTree[k] -= 1;
            }
            this.total -= w;
            this.alive--;
        }
    }
}
