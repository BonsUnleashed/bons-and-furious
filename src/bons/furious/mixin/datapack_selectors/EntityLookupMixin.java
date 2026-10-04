package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.IndexedLookup;
import bons.furious.patch.datapack_selectors.SelectorPrefilter;
import bons.furious.patch.datapack_selectors.TypeIndex;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_selector_type_index (Minecraft 1.20.1, both sides; the index only exists for a lookup a type-first selector
 * scans): getEntities (m_260822_) called with a TypeFirstOption walks the lookup's TypeIndex (built from byId on that
 * first scan, refreshed when it lost step) instead of every entity; add (m_156814_) and remove (m_156822_) keep an
 * existing index in step by reading byId before and after the vanilla call (see TypeIndex). Every other call, and every
 * call while no index exists, runs the original method unchanged. No Minecraft code is carried.
 */
@Mixin(value = EntityLookup.class, remap = false)
public abstract class EntityLookupMixin implements IndexedLookup {
    @Shadow
    @Final
    private Int2ObjectMap<?> f_156807_;
    @Unique
    private TypeIndex bons$typeIndex;

    @WrapMethod(method = "m_260822_")
    private void bons$indexedScan(EntityTypeTest<?, ?> test, AbortableIterationConsumer<?> consumer, Operation<Void> original) {
        if (test instanceof SelectorPrefilter.TypeFirstOption type && SelectorPrefilter.typeIndexEnabled) {
            this.bons$index().forEach(type, consumer);
            return;
        }
        original.call(test, consumer);
    }

    @WrapMethod(method = "m_156814_")
    private void bons$addToIndex(EntityAccess entity, Operation<Void> original) {
        TypeIndex index = this.bons$typeIndex;
        if (index == null) {
            original.call(entity);
            return;
        }
        int id = entity.m_19879_(), size = this.f_156807_.size();
        Object before = this.f_156807_.get(id);
        original.call(entity);
        Object after = this.f_156807_.get(id);
        if (after == before && this.f_156807_.size() == size) return;            // duplicate UUID: nothing added
        if (before == null && after == entity && this.f_156807_.size() == size + 1) index.append(id, entity);
        else index.markDirty();
    }

    @WrapMethod(method = "m_156822_")
    private void bons$removeFromIndex(EntityAccess entity, Operation<Void> original) {
        TypeIndex index = this.bons$typeIndex;
        if (index == null) {
            original.call(entity);
            return;
        }
        int id = entity.m_19879_(), size = this.f_156807_.size();
        Object before = this.f_156807_.get(id);
        original.call(entity);
        Object after = this.f_156807_.get(id);
        if (after == before && this.f_156807_.size() == size) return;            // nothing under that id
        if (before != null && after == null && this.f_156807_.size() == size - 1) index.remove(id);
        else index.markDirty();
    }

    @Override
    public void bons$visitIndexed(SelectorPrefilter.TypeFirstOption filter, AbortableIterationConsumer<Object> consumer) {
        this.bons$index().forEach(filter, consumer);
    }

    @Unique
    private TypeIndex bons$index() {
        TypeIndex index = this.bons$typeIndex;
        if (index == null) {
            index = this.bons$typeIndex = TypeIndex.build(this.f_156807_);
        } else {
            index.refresh(this.f_156807_);
        }
        return index;
    }
}
