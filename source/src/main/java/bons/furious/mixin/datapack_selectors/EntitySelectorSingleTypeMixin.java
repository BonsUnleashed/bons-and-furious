package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.SingleTypeTest;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_selector_single_type (Minecraft 1.21.1 with NeoForge 21.1.252; server side incl. the integrated server):
 * addEntities' whole-level ServerLevel.getEntities call gets its EntityType test wrapped in a SingleTypeTest, which
 * vanilla_selector_type_index's EntityLookupMixin answers from the level's type index (SingleTypeTest). Chains with that
 * key's own WrapOperation on the same call in either order. require = 0: degrades to vanilla. No Minecraft code.
 *
 * Ported to 1.21.1: unchanged. addEntities now takes the absolute AABB instead of the position; its whole-level call
 * (taken when that AABB is null) and its descriptor are the same.
 */
@Mixin(value = EntitySelector.class, remap = false)
public abstract class EntitySelectorSingleTypeMixin {
    @WrapOperation(method = "addEntities", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Ljava/util/function/Predicate;Ljava/util/List;I)V"))
    private void bons$singleTypeScan(ServerLevel level, EntityTypeTest<?, ?> test, Predicate<?> predicate, List<?> out, int limit,
                                     Operation<Void> original) {
        SingleTypeTest.scan(level, test, predicate, out, limit, original);
    }
}
