package bons.furious.mixin.crittersandcompanions_c2;

import bons.furious.patch.crittersandcompanions_c2.EntityClassCounts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * vanilla_entity_class_count_layer (Minecraft 1.20.1, both sides; SRG class, not patched by Forge 47.4.16), part 2 of 4:
 * the counting itself, at the two places an entity enters or leaves an entity section.
 *  - add (m_188346_): after the section's multimap took the entity, a tracked entity is counted.
 *  - remove (m_188355_): before the multimap removes, a removal that involves a tracked entity (the argument, or any member
 *    of a section holding one) looks up the element ArrayList.remove will take out; after it, that element is uncounted
 *    only if it is the argument itself (otherwise the level's counts become doubtful for good). Every other removal costs a
 *    class lookup and a field read.
 * The argument and the return value are passed through unchanged (ModifyArg returns the same object, the two
 * ModifyExpressionValue hooks return the same boolean). No allocation on either path.
 */
@Mixin(value = EntitySection.class, remap = false)
public abstract class EntityClassCountSectionMixin implements EntityClassCounts.Section {
    @Shadow
    @Final
    private ClassInstanceMultiMap<EntityAccess> f_156827_;
    @Unique
    private EntityClassCounts.Storage bons$classCountStorage;
    @Unique
    private int bons$trackedMembers;
    @Unique
    private Object bons$removing;

    @ModifyExpressionValue(method = "m_188346_", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ClassInstanceMultiMap;add(Ljava/lang/Object;)Z"))
    private boolean bons$countAdd(boolean added, @Local(argsOnly = true) EntityAccess entity) {
        if (added) EntityClassCounts.added(this, entity);
        return added;
    }

    @ModifyArg(method = "m_188355_", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ClassInstanceMultiMap;remove(Ljava/lang/Object;)Z"))
    private Object bons$beforeRemove(Object entity) {
        this.bons$removing = EntityClassCounts.beforeRemove(this, this.f_156827_, entity);
        return entity;
    }

    @ModifyExpressionValue(method = "m_188355_", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ClassInstanceMultiMap;remove(Ljava/lang/Object;)Z"))
    private boolean bons$countRemove(boolean removed, @Local(argsOnly = true) EntityAccess entity) {
        Object first = this.bons$removing;
        if (first != null) {
            this.bons$removing = null;
            EntityClassCounts.afterRemove(this, entity, first, removed);
        }
        return removed;
    }

    @Override
    public EntityClassCounts.Storage bons$classCountStorage() {
        return this.bons$classCountStorage;
    }

    @Override
    public void bons$classCountStorage(EntityClassCounts.Storage storage) {
        this.bons$classCountStorage = storage;
    }

    @Override
    public int bons$trackedMembers() {
        return this.bons$trackedMembers;
    }

    @Override
    public void bons$trackedMembers(int members) {
        this.bons$trackedMembers = members;
    }

    @Override
    public int bons$trackedTruth(int index) {
        int n = 0;
        for (EntityAccess e : this.f_156827_) if ((EntityClassCounts.mask(e) & (1 << index)) != 0) n++;
        return n;
    }
}
