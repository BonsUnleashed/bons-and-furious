package bons.furious.patch.emf_render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious group emf_render (tested build: Entity Model Features 3.3.9 for NeoForge 1.21.1 on Minecraft 1.21.1 /
 * NeoForge 21.1.252, client only; first written for EMF 3.2.4 on Minecraft 1.20.1 / Forge 47.4.16). Helper of the two
 * switches of the group that exist on 1.21.1:
 *
 *  - emf_variable_default_unboxed (bons.furious.mixin.emf_render.EmfVariableDefaultMixin): EMFMath.getEntityVariable
 *    without boxing its default value.
 *  - emf_anger_map_prune (EmfAngerMapMixin): Fix: the anger-time map forgets a mob whose anger ended instead of keeping a
 *    0 entry for every neutral mob ever drawn.
 *
 * Ported to 1.21.1: the third 1.0.36 switch of the group, emf_arm_walk_gate, is retired here: EMF 3.3.9 no longer walks a
 * custom part's subtree on every render (EMFModelPartCustom has no render override and no processArmItemOverrides; hand
 * and other attachment positioners are built once per model variant in EMFModelPartRoot.addAndSetVariantOfJem and looked
 * up by getPositionerForAttachment), so its runtime flag, pose-stack accessors and SHADOW probe are not carried.
 */
public final class EmfRender {
    /** emf_variable_default_unboxed; -Dbons_and_furious.emfVariableDefaultUnboxed=false also turns it off. */
    public static volatile boolean variableDefaults = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfVariableDefaultUnboxed", "true"));
    /** emf_anger_map_prune; -Dbons_and_furious.emfAngerMapPrune=false also turns it off. */
    public static volatile boolean angerMapPrune = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfAngerMapPrune", "true"));

    /** The value getOrDefault returns for an absent variable (never stored in any map: it is private to this class). */
    public static final Object ABSENT = new Object();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announcedVariables, announcedAnger;

    private EmfRender() {
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
}
