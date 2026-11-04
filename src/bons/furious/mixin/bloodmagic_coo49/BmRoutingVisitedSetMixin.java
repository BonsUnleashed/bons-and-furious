package bons.furious.mixin.bloodmagic_coo49;

import bons.furious.patch.bloodmagic_coo49.BmVisited;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * bloodmagic_routing_visited_set (Blood Magic 3.3.8-50, CC BY 4.0; server; exact): @Redirect of the two "new LinkedList()" of
 * TileMasterRoutingNode.tick that seed isConnected(path, node) for every output node (the first NEW of the method) and every
 * input node (the third; the second and fourth are the filter lists, untouched). isConnected uses the list as its visited
 * set: add on entry, contains for every connection of every node it walks, nothing else. A network of 16 nodes or more (the
 * master's own node list, generalNodeList) gets a LinkedList that also keeps a hash set of its elements once a search has
 * visited 16 of them, so contains is a hash lookup instead of a walk of the whole list; a smaller network gets the plain
 * LinkedList as shipped. A redirect (not a MixinExtras wrap) so a small network pays one size test. See BmVisited. Our own
 * logic only.
 */
@Mixin(targets = "wayoftime.bloodmagic.common.tile.routing.TileMasterRoutingNode", remap = false)
public abstract class BmRoutingVisitedSetMixin {
    @Shadow(remap = false)
    private List<BlockPos> generalNodeList;

    @SuppressWarnings({"rawtypes"})
    @Redirect(method = "tick()V", at = @At(value = "NEW", target = "()Ljava/util/LinkedList;", ordinal = 0), require = 1, allow = 1, remap = false)
    private LinkedList bons$bloodmagicCoo49OutputPath() {
        return BmVisited.path(this.generalNodeList);
    }

    @SuppressWarnings({"rawtypes"})
    @Redirect(method = "tick()V", at = @At(value = "NEW", target = "()Ljava/util/LinkedList;", ordinal = 2), require = 1, allow = 1, remap = false)
    private LinkedList bons$bloodmagicCoo49InputPath() {
        return BmVisited.path(this.generalNodeList);
    }
}
