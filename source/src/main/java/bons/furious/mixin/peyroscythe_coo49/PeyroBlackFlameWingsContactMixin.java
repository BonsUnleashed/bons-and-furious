package bons.furious.mixin.peyroscythe_coo49;

import bons.furious.patch.peyroscythe_coo49.PeyroAuras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * peyroscythe_black_flame_wings_server_contact (Peyro's Scythes &amp; Spells 1.21.1-1.3.2 for NeoForge, GPLv3; client;
 * gameplay fix, off by default): MixinExtras @WrapOperation on the Level.getEntities(Entity, AABB) call in
 * BlackFlameWingsEffect.applyEffectTick(LivingEntity, int) (a boolean method on 1.21.1; it ticks every 10 ticks), the
 * search for the entities touching the wearer: on a client level it answers an empty list, so the client's copies of
 * those entities get no contact damage call and no invulnerability timer from it; on a server level the search runs as
 * shipped. The Angel Wings removal, the particles, the fall damage reset and the server player's mana drain run as before.
 * See PeyroAuras. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.peyroscythe.effect.BlackFlameWingsEffect", remap = false)
public abstract class PeyroBlackFlameWingsContactMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$peyroscytheCoo49WingsContact(Level level, Entity except, AABB box, Operation<List> original) {
        List empty = PeyroAuras.clientWingsSearch(level);
        return empty != null ? empty : original.call(level, except, box);
    }
}
