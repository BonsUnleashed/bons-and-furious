package bons.furious.patch.emf_render;

import bons.furious.mixin.emf_render.EmfModelPartChildrenAccessor;
import bons.furious.mixin.emf_render.EmfPartAttachmentsAccessor;
import bons.furious.mixin.emf_render.EmfPoseCacheAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.geom.ModelPart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import traben.entity_model_features.models.parts.EMFModelPartCustom;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.models.parts.EMFModelPartVanilla;

/**
 * Bons and Furious group emf_render (Entity Model Features 3.2.4 on Minecraft 1.20.1 / Forge 47.4.16, client only; with
 * Embeddium 0.3.31's PoseStack). Helper of the three switches:
 *
 *  - emf_arm_walk_gate (bons.furious.mixin.emf_render.EmfArmWalkGateMixin): every EMFModelPartCustom render first calls
 *    processArmItemOverrides, which for a part without hand attachments walks its whole subtree (push, translateAndRotate,
 *    every child, pop) to find the parts that have some. walkIsInert proves, for that call, that the walk cannot have any
 *    effect besides balanced push/pop pairs, and the call is then skipped.
 *  - emf_variable_default_unboxed (EmfVariableDefaultMixin): getEntityVariable without boxing its default value.
 *  - emf_anger_map_prune (EmfAngerMapMixin): Fix: the anger-time map forgets a mob whose anger ended instead of keeping a
 *    0 entry for every neutral mob ever drawn.
 */
public final class EmfRender {
    /** emf_arm_walk_gate; -Dbons_and_furious.emfArmWalkGate=false also turns it off. */
    public static volatile boolean armWalkGate = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfArmWalkGate", "true"));
    /** emf_variable_default_unboxed; -Dbons_and_furious.emfVariableDefaultUnboxed=false also turns it off. */
    public static volatile boolean variableDefaults = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfVariableDefaultUnboxed", "true"));
    /** emf_anger_map_prune; -Dbons_and_furious.emfAngerMapPrune=false also turns it off. */
    public static volatile boolean angerMapPrune = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfAngerMapPrune", "true"));

    /**
     * Rig probe for emf_arm_walk_gate: -Dbons_and_furious.emfArmWalkGate.shadow=true runs every walk the gate would skip and
     * checks that it changed nothing: the same PoseStack depth, the same top Pose object with the same matrices, the same
     * poses below it. SHADOW_CHECKS counts the walks, SHADOW_MISMATCHES walks that changed something (WARN for 20).
     */
    public static final boolean SHADOW_WALK = Boolean.getBoolean("bons_and_furious.emfArmWalkGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    /** The value getOrDefault returns for an absent variable (never stored in any map: it is private to this class). */
    public static final Object ABSENT = new Object();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announcedWalk, announcedVariables, announcedAnger;

    private EmfRender() {
    }

    /**
     * True when processArmItemOverrides(stack) on SELF can change nothing but push and pop the stack in pairs, for this call:
     *  - the stack is a plain PoseStack (no subclass overrides push/pop) and Embeddium's pose cache is off on it (with the
     *    cache on, a push/pop pair recycles a cached Pose object, which the walk would also do);
     *  - SELF is a plain EMFModelPartCustom without attachments (it would walk, not place hand items);
     *  - every part below it is one of EMF's own part classes (none overrides the walk or translateAndRotate), every custom
     *    one without attachments, and every children map is present (the walk would throw on a null map, a null child or a
     *    part of another class, after pushes it never pops).
     * Then the walk's only effects are pushPose (a new Pose copying the top), translateAndRotate on that new top, the same
     * for each descendant, and the matching popPose calls: the stack ends exactly as it began.
     */
    public static boolean walkIsInert(EMFModelPartCustom self, PoseStack stack) {
        if (stack == null || stack.getClass() != PoseStack.class || ((EmfPoseCacheAccessor) (Object) stack).bons$poseCacheDepth() != 0) return false;
        if (self.getClass() != EMFModelPartCustom.class || ((EmfPartAttachmentsAccessor) (Object) self).bons$attachments() != null) return false;
        return subtreeInert(self);
    }

    static boolean subtreeInert(ModelPart part) {
        Map<String, ModelPart> children = ((EmfModelPartChildrenAccessor) (Object) part).bons$emfChildren();
        if (children == null) return false;
        for (ModelPart c : children.values()) {
            if (c == null) return false;
            Class<?> k = c.getClass();
            if (k == EMFModelPartCustom.class) {
                if (((EmfPartAttachmentsAccessor) (Object) c).bons$attachments() != null) return false;
            } else if (k != EMFModelPartVanilla.class && k != EMFModelPartRoot.class) {
                return false;
            }
            if (!subtreeInert(c)) return false;
        }
        return true;
    }

    public static void announceWalk() {
        if (announcedWalk) return;
        announcedWalk = true;
        LOGGER.info("Bons and Furious: emf_arm_walk_gate: EMF custom parts skip the hand-attachment walk where nothing below them has one");
    }

    public static void announceVariables() {
        if (announcedVariables) return;
        announcedVariables = true;
        LOGGER.info("Bons and Furious: emf_variable_default_unboxed: EMF entity variables are read without boxing their default");
    }

    public static void announceAnger() {
        if (announcedAnger) return;
        announcedAnger = true;
        LOGGER.info("Bons and Furious: emf_anger_map_prune: EMF forgets mobs whose anger ended (no 0 entry kept per mob)");
    }

    /** SHADOW_WALK: run the walk the gate skips and check the stack is as before (identity and matrix values). */
    public static void shadowWalk(EMFModelPartCustom self, PoseStack stack) {
        Deque<PoseStack.Pose> before = new java.util.ArrayDeque<>();
        Deque<PoseStack.Pose> live = ((EmfPoseCacheAccessor) (Object) stack).bons$poseDeque();
        before.addAll(live);
        PoseStack.Pose top = stack.m_85850_();
        org.joml.Matrix4f m4 = new org.joml.Matrix4f(top.m_252922_());
        org.joml.Matrix3f m3 = new org.joml.Matrix3f(top.m_252943_());
        self.processArmItemOverrides(stack);
        SHADOW_CHECKS.incrementAndGet();
        boolean same = live.size() == before.size() && stack.m_85850_() == top && top.m_252922_().equals(m4) && top.m_252943_().equals(m3);
        if (same) {
            java.util.Iterator<PoseStack.Pose> a = live.iterator(), b = before.iterator();
            while (a.hasNext()) if (a.next() != b.next()) { same = false; break; }
        }
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: emf_arm_walk_gate SHADOW: the walk of {} changed the pose stack (depth {} -> {})", self, before.size(), live.size());
    }
}
