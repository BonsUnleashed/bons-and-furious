package bons.furious.mixin.radium_fixes;

import bons.furious.patch.radium_fixes.PartEntityCollisions;
import java.util.List;
import me.jellysquid.mods.lithium.common.entity.EntityClassGroup;
import me.jellysquid.mods.lithium.common.world.WorldHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * radium_part_entity_collisions (Radium, LGPL-3.0; 1.21.1 tested build radium-mc1.21.1-0.13.1+git.4994e83; both sides).
 * Fix.
 *
 * Target: Radium's own WorldHelper.getEntitiesForCollision, the entity source of Radium's movement collisions
 * (entity.collisions.movement: LithiumEntityCollisions.appendEntityCollisions) and of Level.noCollision
 * (entity.collisions.intersection: getEntityWorldBorderCollisionIterable). Its shortcut branch returns only section
 * entities of the hard class group (getEntitiesOfClassGroup); NeoForge's Level.getEntities also returns the level's
 * PartEntity objects, which live outside the sections. The redirect makes Radium's call unchanged and appends the hard
 * part entities vanilla's loop would add (PartEntityCollisions.withHardParts), so Radium's filter afterwards gives
 * vanilla's collision set. The handler captures the target method's arguments (plain Mixin argument capture) for the
 * level; MixinExtras' @Local would need its runtime-generated LocalRef classes on this hot path. The other branch
 * (moving entities that override canCollideWith) already calls vanilla's lookup and is not touched. Radium is LGPL-3.0;
 * no Radium code is carried, only the redirect of its call.
 *
 * Ported to 1.21.1: same redirect (method and call descriptors unchanged; the scoped method selector keeps it off the
 * second getEntitiesOfClassGroup call that Radium 0.13.1 added in getOtherEntitiesForCollision, see PartEntityCollisions).
 */
@Mixin(targets = "me.jellysquid.mods.lithium.common.world.WorldHelper", remap = false)
public abstract class PartEntityCollisionsMixin {
    @Redirect(method = "getEntitiesForCollision(Lnet/minecraft/world/level/EntityGetter;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/entity/Entity;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/lithium/common/world/WorldHelper;getEntitiesOfClassGroup(Lnet/minecraft/world/level/entity/EntitySectionStorage;Lnet/minecraft/world/entity/Entity;Lme/jellysquid/mods/lithium/common/entity/EntityClassGroup$NoDragonClassGroup;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List<Entity> bons$withHardPartEntities(EntitySectionStorage<Entity> sections, Entity colliding,
                                                          EntityClassGroup.NoDragonClassGroup hardGroup, AABB box,
                                                          EntityGetter view, AABB queryBox, Entity queryEntity) {
        List<Entity> sectionEntities = WorldHelper.getEntitiesOfClassGroup(sections, colliding, hardGroup, box);
        if (!PartEntityCollisions.enabled) return sectionEntities;
        return PartEntityCollisions.withHardParts(sectionEntities, (Level) view, colliding, hardGroup, box);
    }
}
