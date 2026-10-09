package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.TagIndex;
import bons.furious.patch.datapack_selectors.TagScanSelector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_selector_tag_index (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the integrated server): the selector's
 * usable tags (set by EntitySelectorParserTagMixin at parse time) and addEntities' (m_121154_) whole-level
 * ServerLevel.getEntities call (m_261178_): with tags, its type test is wrapped in a TagScanTest (TagIndex.scan; same
 * predicate, list and limit). Priority 500 makes this the innermost wrapper of that call, so it wraps whatever test the
 * other keys' wrappers made (TypeFirstOption, SingleTypeTest). require = 0: degrades to vanilla. No Minecraft code.
 */
@Mixin(value = EntitySelector.class, priority = 500, remap = false)
public abstract class EntitySelectorTagScanMixin implements TagScanSelector {
    @Unique
    private String[] bons$scanTags;
    @Unique
    private TagIndex.TagScanTest bons$tagTest;

    @Override
    public void bons$setScanTags(String[] tags) {
        this.bons$scanTags = tags;
    }

    @Override
    public String[] bons$scanTags() {
        return this.bons$scanTags;
    }

    @Override
    public TagIndex.TagScanTest bons$tagTest() {
        return this.bons$tagTest;
    }

    @Override
    public void bons$setTagTest(TagIndex.TagScanTest test) {
        this.bons$tagTest = test;
    }

    @WrapOperation(method = "m_121154_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;m_261178_(Lnet/minecraft/world/level/entity/EntityTypeTest;Ljava/util/function/Predicate;Ljava/util/List;I)V"))
    private void bons$tagScan(ServerLevel level, EntityTypeTest<?, ?> test, Predicate<?> predicate, List<?> out, int limit,
                              Operation<Void> original) {
        TagIndex.scan(this, level, test, predicate, out, limit, original);
    }
}
