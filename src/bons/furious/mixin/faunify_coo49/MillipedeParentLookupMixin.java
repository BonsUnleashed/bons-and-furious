package bons.furious.mixin.faunify_coo49;

import bons.furious.patch.faunify_coo49.ParentLookup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * faunify_millipede_parent_uuid_lookup (Faunify 1.3.3, client; the class is not in Faunify 1.1.3: string target + @Pseudo,
 * and the guard leaves the switch ABSENT there): the client branch's Level.getEntities box search in
 * MillipedeSegmentEntity.getParentSegment is answered from the level's UUID lookup when ParentLookup can; otherwise the
 * search runs as before. The UUID is the segment's own synced parent UUID, read again at the call (getParentSegment read
 * it just before; nothing in between can change it). See ParentLookup.
 */
@Pseudo
@Mixin(targets = "com.pepper.faunify.entity.MillipedeSegmentEntity", remap = false)
public abstract class MillipedeParentLookupMixin {
    @Shadow
    @Final
    private static EntityDataAccessor<Optional<UUID>> PARENT_UUID;

    @WrapOperation(method = "getParentSegment", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6249_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<Entity> bons$parentByUuid(Level level, Entity except, AABB box, Predicate<? super Entity> predicate, Operation<List<Entity>> original) {
        UUID parent = ((Entity) (Object) this).m_20088_().m_135370_(PARENT_UUID).orElse(null);
        List<Entity> found = ParentLookup.find(level, box, parent);
        if (found == null) return original.call(level, except, box, predicate);
        if (ParentLookup.SHADOW) ParentLookup.shadow(found, original.call(level, except, box, predicate), parent);
        return found;
    }
}
