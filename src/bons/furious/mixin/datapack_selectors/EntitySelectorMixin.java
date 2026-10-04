package bons.furious.mixin.datapack_selectors;

import bons.furious.patch.datapack_selectors.PrefilteredSelector;
import bons.furious.patch.datapack_selectors.SelectorPrefilter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_selector_scan_prefilter (Minecraft 1.20.1 on Forge 47.4.16, server side incl. the integrated server): the
 * selector's scan filter (null unless the parser found a usable first option), and addEntities' (m_121154_) whole-level
 * ServerLevel.getEntities call (m_261178_) receives it as its type test instead of the selector's ANY test
 * (SelectorPrefilter.scan; same predicate, list and limit). The position-box call (m_260826_) is not touched.
 */
@Mixin(value = EntitySelector.class, remap = false)
public abstract class EntitySelectorMixin implements PrefilteredSelector {
    @Unique
    private EntityTypeTest<Entity, Entity> bons$prefilter;

    @Override
    public void bons$setScanPrefilter(EntityTypeTest<Entity, Entity> filter) {
        this.bons$prefilter = filter;
    }

    @Override
    public EntityTypeTest<Entity, Entity> bons$scanPrefilter() {
        return this.bons$prefilter;
    }

    @WrapOperation(method = "m_121154_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;m_261178_(Lnet/minecraft/world/level/entity/EntityTypeTest;Ljava/util/function/Predicate;Ljava/util/List;I)V"))
    private void bons$prefilteredScan(ServerLevel level, EntityTypeTest<?, ?> test, Predicate<?> predicate, List<?> out, int limit,
                                      Operation<Void> original) {
        SelectorPrefilter.scan(this.bons$prefilter, level, test, predicate, out, limit, original);
    }
}
