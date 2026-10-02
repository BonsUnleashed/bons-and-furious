package bons.furious.mixin.c2me_compat;

import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Trigger of switch radium_experimental_tickets_spawning: C2meCompatPlugin sets Radium's experimental options when Mixin
 * asks about this class. Never applied (the plugin always answers false); it only makes Mixin call the plugin at the
 * right moment, after Radium built its options and before Radium's mixins are decided.
 */
@Mixin(value = DistanceManager.class, remap = false)
public abstract class RadiumExperimentalOptionTrigger {
}
