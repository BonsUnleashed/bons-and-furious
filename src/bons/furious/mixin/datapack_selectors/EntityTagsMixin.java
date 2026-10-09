package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.TagIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashSet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_selector_tag_index (Minecraft 1.20.1 on Forge 47.4.16, both sides): Entity's constructor initialises its final
 * tags field (f_19841_) from Sets.newHashSet(); the call is wrapped and, when it returned a plain empty HashSet, a
 * TrackedTags (a HashSet built the same way that reports its changes to the level's tag lists) is stored instead
 * (TagIndex.trackedSet). Any other result (another mod's set) is kept unchanged and its entity is always visited by tag
 * scans. require = 0: without it every entity is simply untracked. No Minecraft code.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class EntityTagsMixin {
    @WrapOperation(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"))
    private HashSet<?> bons$trackedTags(Operation<HashSet<?>> original) {
        return TagIndex.trackedSet(this, original.call());
    }
}
