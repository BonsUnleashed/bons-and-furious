package bons.furious.mixin.dungeonsdelight_c2;

import bons.furious.patch.dungeonsdelight_c2.YamSingleAdd;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.yirmiri.dungeonsdelight.common.entity.monster_yam.MonsterYamEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dungeonsdelight_yam_single_add (Dungeons Delight, AZURUNE licence; 1.21.1 tested build: Dungeon's Delight 1.5.1 for
 * NeoForge 1.21.1; both sides, acts on the server; a fix): the two addFreshEntity calls of the summon loop in
 * MonsterYamEntity.tick share whether the first one added the zombie; the second is skipped only then (see
 * {@link YamSingleAdd}).
 *
 * Ported to 1.21.1: unchanged; the two calls are the same Level.addFreshEntity(Entity) invocations (ordinals 0 and 1).
 */
@Mixin(value = MonsterYamEntity.class, remap = false)
public abstract class YamSingleAddMixin {
    @WrapOperation(method = "tick", require = 1, allow = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean bons$firstAdd(Level level, Entity entity, Operation<Boolean> original, @Share("bons$yamAdded") LocalRef<Entity> added) {
        return YamSingleAdd.first(level, entity, original, added);
    }

    @WrapOperation(method = "tick", require = 1, allow = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean bons$secondAdd(Level level, Entity entity, Operation<Boolean> original, @Share("bons$yamAdded") LocalRef<Entity> added) {
        return YamSingleAdd.second(level, entity, original, added);
    }
}
