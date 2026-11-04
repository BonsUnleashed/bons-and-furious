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
 * peyroscythe_black_flame_wings_server_contact (Peyro's Scythes &amp; Spells 1.20.1-1.3.3, GPLv3; client; gameplay fix,
 * off by default): MixinExtras @WrapOperation on the Level.getEntities(Entity, AABB) (m_45933_) call in
 * BlackFlameWingsEffect.applyEffectTick (m_6742_), the search for the entities touching the wearer: on a client level it
 * answers an empty list, so the client's copies of those entities get no contact damage call and no invulnerability
 * timer from it; on a server level the search runs as shipped. The particles, the Angel Wings removal and the fall
 * damage reset run on both sides as before. See PeyroAuras. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.peyroscythe.effect.BlackFlameWingsEffect", remap = false)
public abstract class PeyroBlackFlameWingsContactMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "m_6742_(Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45933_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$peyroscytheCoo49WingsContact(Level level, Entity except, AABB box, Operation<List> original) {
        List empty = PeyroAuras.clientWingsSearch(level);
        return empty != null ? empty : original.call(level, except, box);
    }
}
