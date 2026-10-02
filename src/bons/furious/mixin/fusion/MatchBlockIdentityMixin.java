package bons.furious.mixin.fusion;

import bons.furious.patch.fusion.ConnectionLookups;
import com.supermartijn642.fusion.texture.types.connecting.predicates.MatchBlockConnectionPredicate;
import java.util.Collection;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fusion_connection_lookups, part 2 (Fusion 1.3.14+a, client).
 *
 * MatchBlockConnectionPredicate (the "match_block" connection rule of Fusion's connected textures, xali's overlays use
 * it on every terrain face) keeps its blocks in a Set.copyOf set and asks {@code blocks.contains(other.getBlock())} for
 * each of a face's eight neighbours. At the end of the (only) constructor an identity view of the same blocks is built
 * once (ConnectionLookups.identityView: the blocks as an array, null for sets over 16 blocks or unless equality is
 * identity for every block involved), and the contains call in shouldConnect is answered by scanning it with ==. The
 * view holds the same blocks, so membership is the same; with the runtime flag off, without a view or for a null
 * argument the original Set.contains runs. Fusion is All Rights
 * Reserved: one constructor tail injection and one redirected call, none of its code carried.
 */
@Mixin(value = MatchBlockConnectionPredicate.class, remap = false)
public abstract class MatchBlockIdentityMixin {
    @Shadow
    @Final
    private Set<Block> blocks;

    @Unique
    private Block[] bons$identity;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$identityView(Collection<Block> source, CallbackInfo ci) {
        this.bons$identity = ConnectionLookups.identityView(this.blocks);
    }

    @Redirect(method = "shouldConnect", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean bons$contains(Set<?> set, Object block) {
        return ConnectionLookups.contains(set, this.bons$identity, block);
    }
}
