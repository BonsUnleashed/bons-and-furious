package bons.furious.mixin.embeddium_search;

import bons.furious.patch.embeddium_search.SearchReplay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.render.viewport.frustum.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * embeddium_search_replay (Embeddium 0.3.31+mc1.20.1; client only; with Oculus 1.8.0 the shadow pass too, through
 * oculus_shadow_search_replay).
 *
 * OcclusionCuller.findVisible is Embeddium's breadth-first section search, run by RenderSectionManager.createTerrainRenderList
 * for the main view and (Oculus) for the shadow pass on every frame. This @WrapMethod keeps, per culler, the last search
 * of each kind (frustum class + occlusion flag) as the sequence of sections it reported to its visitor with the answers,
 * plus a key of everything the search reads (SearchReplay). When a new search's inputs are identical bit for bit (same
 * key, same section-graph and build-info epochs, same start and camera section, same occlusion flag), the real search
 * would report exactly that sequence, so bons$apply stamps each section's lastVisibleFrame with the new frame and hands
 * the same (section, answer) pairs to the same visitor, which builds the same render lists and rebuild queues from the
 * sections' live state. Anything else runs the real search unchanged.
 *
 * Performance rules (they decide only whether a search is keyed or recorded, never what a search returns): a search is
 * recorded when its inputs equal the previous keyed search's of its kind (a still view; the next identical search then
 * replays), so a moving view is never recorded; after 16 keyed searches in a row whose inputs changed, only one search in
 * four is keyed until a search replays or repeats (a moving view then pays a quarter of the keying).
 *
 * Why not separate per-pass render lists (the survey's first idea): with Oculus, tickVisibleRenders and
 * scheduleTranslucencyUpdates read the main view's region lists during the shadow pass, after the shadow search refilled
 * them; keeping the passes apart would change which sprites are marked active and which sections get resort tasks.
 * Replaying the search's visitor calls leaves that shared storage, and everything else, exactly as shipped.
 * Why no re-testing of the recorded sections when only the frustum moved: measured, re-running every recorded section's
 * visibility test costs as much as the search itself (both are bound by touching each section once).
 */
@Mixin(value = OcclusionCuller.class, remap = false)
public abstract class OcclusionCullerReplayMixin {
    @Shadow
    @Final
    private Level world;

    @Shadow
    private RenderSection getRenderSection(int x, int y, int z) {
        throw new AssertionError("shadowed");
    }

    @Unique
    private final SearchReplay.Recording bons$slotA = new SearchReplay.Recording();
    @Unique
    private final SearchReplay.Recording bons$slotB = new SearchReplay.Recording();
    @Unique
    private final SearchReplay.Recording bons$check = new SearchReplay.Recording();
    @Unique
    private final SearchReplay.Recorder bons$recorder = new SearchReplay.Recorder();
    @Unique
    private final SearchReplay.Key bons$key = new SearchReplay.Key();
    @Unique
    private int bons$lastFrame;
    @Unique
    private boolean bons$sawFrame;
    @Unique
    private long bons$uses;

    @WrapMethod(method = "findVisible")
    private void bons$findVisible(OcclusionCuller.Visitor visitor, Viewport viewport, float searchDistance, boolean useOcclusionCulling, int frame,
                                  Operation<Void> original) {
        // one call site for every search that runs unchanged (switch off, not replayable, moving view)
        if (!this.bons$handled(visitor, viewport, searchDistance, useOcclusionCulling, frame, original)) {
            original.call(visitor, viewport, searchDistance, useOcclusionCulling, frame);
        }
    }

    /** Replays or records the search when it may and returns true; false: the caller runs it unchanged. */
    @Unique
    private boolean bons$handled(OcclusionCuller.Visitor visitor, Viewport viewport, float searchDistance, boolean useOcclusionCulling, int frame,
                                 Operation<Void> original) {
        // the search's "already visited" test compares stamps with the frame: only frames newer than any seen are replayable
        boolean fresh = !this.bons$sawFrame || frame - this.bons$lastFrame > 0;
        if (fresh) {
            this.bons$sawFrame = true;
            this.bons$lastFrame = frame;
        }
        if (!SearchReplay.enabled || !fresh || visitor == null || visitor.getClass() != VisibleChunkCollector.class) return false;
        Frustum frustum = ((ViewportAccessMixin) (Object) viewport).bons$frustum();
        SearchReplay.Plan plan = frustum == null ? null : SearchReplay.frustumPlan(frustum.getClass());
        SectionPos origin = viewport.getChunkCoord();
        int ox = origin.m_123341_(), oy = origin.m_123342_(), oz = origin.m_123343_();
        // the start section of a search that begins inside the world (initWithinWorld); every other start runs unchanged
        RenderSection start = plan != null && oy >= this.world.m_151560_() && oy < this.world.m_151561_() ? this.getRenderSection(ox, oy, oz) : null;
        if (start == null) return false;
        SearchReplay.SEARCHES.incrementAndGet();
        SearchReplay.Recording slot = this.bons$slot(frustum.getClass(), useOcclusionCulling);
        int graph = SearchReplay.graphEpoch, info = useOcclusionCulling ? SearchReplay.infoEpoch : 0;
        if (!slot.valid()) {
            // 1.0.34: the classes are checked before the first recording (was: after it), so the recording visitor never
            // runs in a search that carries untested mixins; not ready: this and every later search run unchanged
            if (!SearchReplay.coreReady()) return false;
            // first search of this kind: record it, then read its key (only after a real search ran: the distance filter's
            // holder is initialised by the search's first distance test, exactly as without the switch; until then no key)
            this.bons$record(slot, visitor, viewport, searchDistance, useOcclusionCulling, frame, original, start, ox, oy, oz, graph, info);
            if (!slot.valid() || !SearchReplay.key(this.bons$key, frustum, plan, viewport.getTransform(), searchDistance)) {
                slot.invalidate();
                return true;
            }
            slot.setKey(this.bons$key);
            return true;
        }
        // a view that keeps changing is keyed in one search out of four (performance rule; unkeyed searches run unchanged)
        if (slot.skipCheck()) return false;
        // an input that cannot be keyed right now (e.g. betterfpsdist's debug mode): run unchanged
        if (!SearchReplay.coreReady() || !SearchReplay.key(this.bons$key, frustum, plan, viewport.getTransform(), searchDistance)) return false;
        if (slot.matches(start, ox, oy, oz, useOcclusionCulling, graph, info, this.bons$key)) {
            slot.still();
            if (SearchReplay.SHADOW) {
                // rig self-check: the real search runs as shipped; its visit sequence must equal the one the replay would issue
                SearchReplay.SHADOW_CHECKS.incrementAndGet();
                this.bons$record(this.bons$check, visitor, viewport, searchDistance, useOcclusionCulling, frame, original, start, ox, oy, oz, graph, info);
                if (!this.bons$check.valid() || !slot.sameSequence(this.bons$check)) {
                    SearchReplay.mismatch("frame " + frame + ", frustum " + frustum.getClass().getSimpleName() + ", occlusion " + useOcclusionCulling
                            + ": recorded " + slot.size() + " sections, the real search visited " + this.bons$check.size());
                }
                slot.copyFrom(this.bons$check);
                return true;
            }
            bons$apply(slot, (VisibleChunkCollector) visitor, frame);
            SearchReplay.REPLAYS.incrementAndGet();
            return true;
        }
        // the inputs differ from the previous keyed search of this kind (a moving view): run it unchanged, unrecorded (a
        // performance rule: recording costs, and the kept recording stays exact for its own key)
        if (!slot.repeats(start, ox, oy, oz, graph, info, this.bons$key)) {
            slot.changed();
            return false;
        }
        // the same inputs twice in a row (a still view): record this search, so the next identical one replays
        slot.still();
        this.bons$record(slot, visitor, viewport, searchDistance, useOcclusionCulling, frame, original, start, ox, oy, oz, graph, info);
        return true;
    }

    /** The recording kept for this kind of search (two kinds: Oculus's shadow pass and the main view). */
    @Unique
    private SearchReplay.Recording bons$slot(Class<?> frustumClass, boolean occlusion) {
        SearchReplay.Recording a = this.bons$slotA, b = this.bons$slotB, r;
        if (a.holds(frustumClass, occlusion)) r = a;
        else if (b.holds(frustumClass, occlusion)) r = b;
        else {
            r = a.lastUse() <= b.lastUse() ? a : b;
            r.assign(frustumClass, occlusion);
        }
        r.touch(++this.bons$uses);
        return r;
    }

    /** What the search does to the outside world: each visited section's stamp and one visitor call per section, in order. */
    @Unique
    private static void bons$apply(SearchReplay.Recording r, VisibleChunkCollector visitor, int frame) {
        RenderSection[] sections = r.sections();
        boolean[] visible = r.visible();
        int n = r.size();
        for (int i = 0; i < n; i++) {
            RenderSection section = sections[i];
            section.setLastVisibleFrame(frame);
            visitor.visit(section, visible[i]);
        }
        SearchReplay.REPLAYED_SECTIONS.addAndGet(n);
    }

    /** The real search with a recording visitor in front of the real one (same calls, same order); keeps the current key. */
    @Unique
    private void bons$record(SearchReplay.Recording r, OcclusionCuller.Visitor visitor, Viewport viewport, float searchDistance, boolean occlusion,
                             int frame, Operation<Void> original, RenderSection start, int ox, int oy, int oz, int graph, int info) {
        SearchReplay.Recorder recorder = this.bons$recorder.begin(visitor, r);
        boolean ok = false;
        try {
            original.call(recorder, viewport, searchDistance, occlusion, frame);
            ok = true;
        } finally {
            recorder.end();
            r.finish(ok && SearchReplay.graphEpoch == graph && (!occlusion || SearchReplay.infoEpoch == info), start, ox, oy, oz, occlusion, graph, info,
                    this.bons$key);
        }
        SearchReplay.announce();
    }
}
