package bons.furious.mixin.enigmaticlegacy_coo49;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * enigmaticlegacy_cursed_ring_anger_candidates_exact (server side): a mixin of priority 999999 on Forge's ForgeHooks that
 * changes nothing; it adds one unused marker method (so the merged class shows the look ran) and gives the group's plugin
 * (ElPlugin.postApply) a look at ForgeHooks after the mixins of other mods were merged, to record whether
 * getEntityVisibilityMultiplier is still the plain Forge body.
 */
@Mixin(targets = "net.minecraftforge.common.ForgeHooks", priority = 999999, remap = false)
public abstract class ElVisibilityHooksLookMixin {
    @Unique
    private static boolean bons$enigmaticlegacyCoo49HooksLook() {
        return true;
    }
}
