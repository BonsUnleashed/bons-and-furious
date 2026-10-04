package bons.furious.patch.immediatelyfast;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.ArrayDeque;
import net.minecraft.client.renderer.RenderType;
import net.raphimc.immediatelyfast.feature.core.BatchableBufferSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch immediatelyfast_idle_layer_skip (ImmediatelyFast 1.5.5+1.20.4, Forge; client).
 *
 * ImmediatelyFast replaces every immediate MultiBufferSource with its BatchableBufferSource. What that class allocated
 * per flush in the review-8 recording (2.5-4.2% of the render thread's sampled allocation, 0.3-1.35% of its time):
 *  - endBatch() ended every fixed layer (29 in vanilla's RenderBuffers, plus any a mod adds) through endBatch(layer),
 *    whose getBufferBuilder wrapped the fixed builder in Collections.singleton(...) and iterated it: a SingletonSet and
 *    its iterator per fixed layer per flush, nearly all of them for layers that were never drawn into
 *    (73 + 67 MB in 90 s while flying);
 *  - close(), which endBatch() calls when nothing is active, iterated the empty active-layer set (an iterator per call);
 *  - every non-consolidating layer got a new ReferenceLinkedOpenHashSet (and its two arrays) per batch;
 *  - every new BatchableBufferSource (Immersive Engineering and others make one per render stage per frame through
 *    MultiBufferSource.immediate) built its two linked hash collections at their default capacity of 16.
 *
 * What the switch does instead, and why each result is ImmediatelyFast's own:
 *  - endBatch() leaves out endBatch(layer) for a fixed layer only when that call could do nothing but toggle the Iris
 *    flag: its builder is not building (RenderType.end returns at once), the layer is neither active nor holding fallback
 *    buffers (remove() would not find it), no fallback batch is pending, the layer's class does not override
 *    RenderType.end, and the source is a plain BatchableBufferSource (a subclass may override endBatch(layer)). The Iris
 *    flag ends where ImmediatelyFast leaves it (set back to true), and RenderSystem.getVertexSorting() is still read once,
 *    keeping its render-thread assertion.
 *  - endBatch(layer) ends the fixed builder directly instead of through a one-element set; fallback sets are iterated
 *    as before.
 *  - close() skips the loop over an empty active set (iterating an empty set does nothing).
 *  - getOrCreateBufferBuilder answers with one lookup per map (get != null; neither map holds null values).
 *  - A fallback set removed after its layer was drawn, or dropped by close(), is emptied and kept for the next
 *    computeIfAbsent of the same source. Linked sets iterate in insertion order whatever their history, and nothing
 *    outside the source holds one after its removal.
 *  - New sources build their fallback map and active set with room for 4 entries instead of 16; linked collections
 *    iterate in insertion order whatever their capacity and grow when needed.
 *
 * -Dbons_and_furious.idleLayerSkip=false restores ImmediatelyFast's own bodies at runtime.
 */
public final class IdleLayers {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** immediatelyfast_idle_layer_skip: -Dbons_and_furious.idleLayerSkip=false runs ImmediatelyFast's own code. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.idleLayerSkip", "true"));
    /** Initial room of a new source's fallback map and active set (ImmediatelyFast's default: 16). */
    public static final int INITIAL_LAYERS = 4;
    private static final int POOLED_SETS = 16;
    private static volatile boolean announced;

    /** True when the render type ends its batch through RenderType's own end (which does nothing for an idle builder). */
    private static final ClassValue<Boolean> PLAIN_END = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("m_276775_", BufferBuilder.class, VertexSorting.class).getDeclaringClass() == RenderType.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private IdleLayers() {}

    /** The fast paths apply to ImmediatelyFast's own class only (a subclass may override any of the methods involved). */
    public static boolean plain(Object source) {
        if (!enabled || source.getClass() != BatchableBufferSource.class) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immediatelyfast_idle_layer_skip applies (ImmediatelyFast flushes skip idle fixed layers and reuse their bookkeeping)");
        }
        return true;
    }

    public static boolean plainEnd(RenderType layer) {
        return PLAIN_END.get(layer.getClass());
    }

    /** Per-source pool of emptied fallback sets (created on first use; sources made per frame rarely need one). */
    public static final class SetPool {
        private final ArrayDeque<ReferenceSet<BufferBuilder>> free = new ArrayDeque<>();

        public ReferenceSet<BufferBuilder> take() {
            ReferenceSet<BufferBuilder> s = this.free.pollLast();
            return s != null ? s : new ReferenceLinkedOpenHashSet<>();
        }

        public void give(ReferenceSet<BufferBuilder> s) {
            if (s instanceof ReferenceLinkedOpenHashSet && this.free.size() < POOLED_SETS) {
                s.clear();
                this.free.addLast(s);
            }
        }
    }
}
