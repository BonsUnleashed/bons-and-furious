package bons.furious.mixin.mutantmonsters_c49;

import bons.furious.patch.mutantmonsters_c49.TypeCount;
import bons.furious.patch.mutantmonsters_c49.TypeCountHolder;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * mutantmonsters_spawn_limit_index (Minecraft 1.21.1; both sides, active only for a level a limited Mutant Monsters spawn
 * asked; since 1.0.49): EntityLookup gets one field, its TypeCount, created by the first query. While it exists, add
 * (add) and remove (remove) read byId under the entity's id before and after the vanilla call and report the
 * change (TypeCount.changed); without it they run unchanged. No Minecraft code is carried. The members it adds carry the
 * group in their names (bons$mutant...): other groups add their own members to EntityLookup (vanilla_selector_type_index
 * wraps the same two methods; the wrappers compose).
 *
 * Ported to 1.21.1: EntityLookup is the same code as on 1.20.1 (byId an Int2ObjectLinkedOpenHashMap, add skips a duplicate
 * UUID, remove takes the id's entry).
 */
@Mixin(value = EntityLookup.class, remap = false)
public abstract class EntityLookupTypeCountMixin implements TypeCountHolder {
    @Shadow
    @Final
    private Int2ObjectMap<?> byId;
    @Unique
    private TypeCount bons$mutantTypeCountField;

    @Override
    public TypeCount bons$mutantTypeCount(boolean create) {
        TypeCount t = this.bons$mutantTypeCountField;
        if (t == null && create) t = this.bons$mutantTypeCountField = new TypeCount();
        return t;
    }

    @Override
    public Int2ObjectMap<?> bons$mutantById() {
        return this.byId;
    }

    @WrapMethod(method = "add", remap = false)
    private void bons$typeCountAdd(EntityAccess entity, Operation<Void> original) {
        TypeCount t = this.bons$mutantTypeCountField;
        if (t == null) {
            original.call(entity);
            return;
        }
        int id = entity.getId();
        Object before = this.byId.get(id);
        original.call(entity);
        t.changed(before, this.byId.get(id));
    }

    @WrapMethod(method = "remove", remap = false)
    private void bons$typeCountRemove(EntityAccess entity, Operation<Void> original) {
        TypeCount t = this.bons$mutantTypeCountField;
        if (t == null) {
            original.call(entity);
            return;
        }
        int id = entity.getId();
        Object before = this.byId.get(id);
        original.call(entity);
        t.changed(before, this.byId.get(id));
    }
}
