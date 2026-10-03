package bons.furious.mixin.butterflies;

import com.bokmcdok.butterflies.butterfly_data.LandingRules;
import java.util.Set;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * butterflies_landing_rule_lookup (Butterflies 7.10.9).
 *
 * A resting butterfly asks isValidLandingBlock for every block it considers, and the method walked both rule sets with
 * iterators each time. Now the block rule is one set lookup of the state's block, and the tag rules are copied once
 * into an array (the sets never change after the constructor) and checked without allocating. The answer is the same:
 * true when the block is listed or the state has one of the listed tags.
 */
@Mixin(value = LandingRules.class, remap = false)
public abstract class LandingRulesMixin {
    @Shadow
    @Final
    private Set<Block> landingBlocks;

    @Shadow
    @Final
    private Set<TagKey<Block>> landingBlockTags;

    /** The landing tags as an array, filled on first use. Racing threads write equal arrays, so volatile is enough. */
    @Unique
    private volatile TagKey<Block>[] bons$tags;

    /**
     * @author BonsUnleashed
     * @reason One set lookup plus a cached tag array instead of two iterator walks per landing check.
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public boolean isValidLandingBlock(BlockState state) {
        if (this.landingBlocks.contains(state.getBlock())) {
            return true;
        }
        TagKey<Block>[] tags = this.bons$tags;
        if (tags == null) {
            tags = this.landingBlockTags.toArray(new TagKey[0]);
            this.bons$tags = tags;
        }
        for (TagKey<Block> tag : tags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
