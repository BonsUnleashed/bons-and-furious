package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouEntitySets;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * mahoutsukai_all_entities_memo (vanilla Entity, client dist; applied only while Mahou Tsukai is installed): every setId
 * (m_20234_) call moves MahouEntitySets' id-change counter on, with a @ModifyVariable at its HEAD that hands the new id back
 * unchanged. An entity's hashCode is its id, so a kept hash set is never handed out again after any id change. At the
 * RETURN of the constructor every new entity's class is checked once per class (until the first class with its own
 * hashCode or equals, which raises MahouEntitySets.oddSeen for good). Nothing of the entity's behaviour changes.
 */
@Mixin(value = Entity.class, remap = false)
public abstract class MahouEntityIdMixin {
    @ModifyVariable(method = "m_20234_(I)V", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
    private int bons$mahoutsukaiCoo49IdChanged(int id) {
        MahouEntitySets.idChanged();
        return id;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"), require = 1, allow = 1)
    private void bons$mahoutsukaiCoo49Constructed(EntityType<?> type, Level level, CallbackInfo ci) {
        MahouEntitySets.constructed((Entity) (Object) this);
    }
}
