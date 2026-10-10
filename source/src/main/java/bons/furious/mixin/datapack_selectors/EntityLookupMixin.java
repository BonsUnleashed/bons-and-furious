package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.IndexedLookup;
import bons.furious.patch.datapack_selectors.SelectorPrefilter;
import bons.furious.patch.datapack_selectors.SingleTypeTest;
import bons.furious.patch.datapack_selectors.TagIndex;
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
 * vanilla_selector_type_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; the index only exists for a lookup a
 * type-first selector scans): getEntities called with a TypeFirstOption walks the lookup's TypeIndex (built from byId on
 * that first scan, refreshed when it lost step) instead of every entity; add and remove keep an existing index in step
 * by reading byId before and after the vanilla call (see TypeIndex). Since 1.0.36 a SingleTypeTest
 * (vanilla_selector_single_type) is answered from the same index (TypeIndex.forEachSingle), and a TagScanTest
 * (vanilla_selector_tag_index) from its tag lists (TypeIndex.forEachTagged) or, when none can be used, through the inner
 * test's own path. Every other call, and every call while no index exists, runs the original method unchanged. No
 * Minecraft code is carried.
 *
 * Ported to 1.21.1: unchanged (EntityLookup is the same code as on 1.20.1).
 */
@Mixin(value = EntityLookup.class, remap = false)
public abstract class EntityLookupMixin implements IndexedLookup {
    @Shadow
    @Final
    private Int2ObjectMap<?> byId;
    @Unique
    private TypeIndex bons$typeIndex;

    @WrapMethod(method = "getEntities")
    private void bons$indexedScan(EntityTypeTest<?, ?> test, AbortableIterationConsumer<?> consumer, Operation<Void> original) {
        // since 1.0.36 (vanilla_selector_tag_index, whose EntitySelector mixin alone creates TagScanTests): the tag lists,
        // or the inner test's own path below when no tag list can be used or a type list is smaller
        if (test instanceof TagIndex.TagScanTest tags) {
            if (TagIndex.enabled && this.bons$index().forEachTagged(tags, consumer, this.byId)) return;
            test = tags.inner();
        }
        if (test instanceof SelectorPrefilter.TypeFirstOption type && SelectorPrefilter.typeIndexEnabled) {
            this.bons$index().forEach(type, consumer);
            return;
        }
        // since 1.0.36 (vanilla_selector_single_type, whose EntitySelector mixin alone creates SingleTypeTests)
        if (test instanceof SingleTypeTest single && SingleTypeTest.enabled && SelectorPrefilter.typeIndexEnabled) {
            this.bons$index().forEachSingle(single, consumer);
            return;
        }
        original.call(test, consumer);
    }

    @WrapMethod(method = "add")
    private void bons$addToIndex(EntityAccess entity, Operation<Void> original) {
        TypeIndex index = this.bons$typeIndex;
        if (index == null) {
            original.call(entity);
            return;
        }
        int id = entity.getId(), size = this.byId.size();
        Object before = this.byId.get(id);
        original.call(entity);
        Object after = this.byId.get(id);
        if (after == before && this.byId.size() == size) return;            // duplicate UUID: nothing added
        if (before == null && after == entity && this.byId.size() == size + 1) index.append(id, entity);
        else index.markDirty();
    }

    @WrapMethod(method = "remove")
    private void bons$removeFromIndex(EntityAccess entity, Operation<Void> original) {
        TypeIndex index = this.bons$typeIndex;
        if (index == null) {
            original.call(entity);
            return;
        }
        int id = entity.getId(), size = this.byId.size();
        Object before = this.byId.get(id);
        original.call(entity);
        Object after = this.byId.get(id);
        if (after == before && this.byId.size() == size) return;            // nothing under that id
        if (before != null && after == null && this.byId.size() == size - 1) index.remove(id);
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
            index = this.bons$typeIndex = TypeIndex.build(this.byId);
        } else {
            index.refresh(this.byId);
        }
        return index;
    }
}
