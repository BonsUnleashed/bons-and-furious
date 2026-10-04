package bons.furious.patch.geckolib;

import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.animation.keyframe.BoneAnimationQueue;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Bons and Furious switch geckolib_bone_queue_reuse (GeckoLib; 1.21.1 tested build: GeckoLib 4.9.3 for NeoForge
 * 1.21.1; both sides, the work happens where animations are processed, on the client).
 *
 * AnimationController.process calls createInitialQueues for every playing controller of every animated object every
 * rendered frame. It clears the controller's bone-queue map and puts one new BoneAnimationQueue record per registered
 * bone, and every record holds nine new AnimationPointQueue lists: ten objects per bone per controller per frame (the
 * review-8 JFR on 1.20.1 has them at 0.41% of the render thread's allocation while GeckoLib mobs are in view).
 *
 * {@link #rebuild} performs the same map operations in the same order - clear(), then one put(name, record) per bone in
 * the order of the bone collection - so the map's keys, their table layout and its iteration order are exactly what
 * GeckoLib produces. Only the records differ in identity: a record is taken over from the map's previous contents when
 * it belongs to the same bone object (record.bone() == bone), after its nine queues are emptied, and is created new
 * otherwise. A taken-over record is then equal (record equality) to the new one GeckoLib would have made: the same bone
 * and nine empty queues. Points that remained queued from the previous frame (only possible with an animation that names
 * a bone twice) are dropped, as GeckoLib drops them with the old record. Each bone's name is read once, as GeckoLib
 * reads it once. GeckoLib's only readers of the records are the same controller's process / processCurrentAnimation and
 * AnimationProcessor.tickAnimation, which polls every queue in the same frame; none keeps a record past the frame.
 *
 * Ported to 1.21.1: GeckoLib 4.9.x merged its core package, so AnimationController moved to
 * software.bernie.geckolib.animation, BoneAnimationQueue to software.bernie.geckolib.animation.keyframe, and the bones
 * are GeoBone (software.bernie.geckolib.cache.object) instead of CoreGeoBone; createInitialQueues, the record (bone +
 * nine AnimationPointQueue lists, a LinkedList subclass) and its use in process and tickAnimation are unchanged.
 *
 * -Dbons_and_furious.boneQueueReuse=false restores GeckoLib's own body at runtime.
 */
public final class BoneQueueReuse {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** geckolib_bone_queue_reuse: -Dbons_and_furious.boneQueueReuse=false runs GeckoLib's own createInitialQueues. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.boneQueueReuse", "true"));
    private static volatile boolean announced;

    private BoneQueueReuse() {}

    /** Per-controller scratch (one per AnimationController, never shared: a controller is processed by one thread). */
    public static final class Scratch {
        GeoBone[] bones = new GeoBone[16];
        String[] names = new String[16];
        BoneAnimationQueue[] queues = new BoneAnimationQueue[16];

        void grow() {
            int n = this.bones.length * 2;
            this.bones = java.util.Arrays.copyOf(this.bones, n);
            this.names = java.util.Arrays.copyOf(this.names, n);
            this.queues = java.util.Arrays.copyOf(this.queues, n);
        }
    }

    /**
     * createInitialQueues with the records of the same bones taken over. The map receives clear() and then
     * put(bone.getName(), record) for every bone in iteration order, as in GeckoLib.
     */
    public static void rebuild(Map<String, BoneAnimationQueue> queues, Collection<GeoBone> bones, Scratch s) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: geckolib_bone_queue_reuse applies (animation controllers keep their bone queues between frames)");
        }
        int n = 0;
        for (GeoBone bone : bones) {
            if (n == s.bones.length) s.grow();
            String name = bone.getName();
            BoneAnimationQueue old = queues.get(name);
            s.bones[n] = bone;
            s.names[n] = name;
            s.queues[n] = old != null && old.bone() == bone ? old : null;
            n++;
        }
        queues.clear();
        for (int i = 0; i < n; i++) {
            GeoBone bone = s.bones[i];
            BoneAnimationQueue q = s.queues[i];
            if (q == null) {
                q = new BoneAnimationQueue(bone);
            } else {
                drain(q);
            }
            queues.put(s.names[i], q);
            s.bones[i] = null;
            s.names[i] = null;
            s.queues[i] = null;
        }
    }

    private static void drain(BoneAnimationQueue q) {
        if (!q.rotationXQueue().isEmpty()) q.rotationXQueue().clear();
        if (!q.rotationYQueue().isEmpty()) q.rotationYQueue().clear();
        if (!q.rotationZQueue().isEmpty()) q.rotationZQueue().clear();
        if (!q.positionXQueue().isEmpty()) q.positionXQueue().clear();
        if (!q.positionYQueue().isEmpty()) q.positionYQueue().clear();
        if (!q.positionZQueue().isEmpty()) q.positionZQueue().clear();
        if (!q.scaleXQueue().isEmpty()) q.scaleXQueue().clear();
        if (!q.scaleYQueue().isEmpty()) q.scaleYQueue().clear();
        if (!q.scaleZQueue().isEmpty()) q.scaleZQueue().clear();
    }
}
