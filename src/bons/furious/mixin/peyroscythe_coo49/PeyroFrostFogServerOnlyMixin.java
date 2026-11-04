package bons.furious.mixin.peyroscythe_coo49;

import bons.furious.patch.peyroscythe_coo49.PeyroAuras;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * peyroscythe_aura_server_only (Peyro's Scythes &amp; Spells 1.20.1-1.3.3, GPLv3; client; trade-off, off by default):
 * MixinExtras @WrapOperation on the Level.getEntitiesOfClass (m_6443_) call in FrostFogEntity.m_8119_ (every tick: every
 * living entity in the fog's radius gets freeze ticks): on a client level the search answers an empty list, so the
 * client writes no freeze ticks of its own (the server's are synced); on a server level the search runs as shipped. See
 * PeyroAuras. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.rinko1231.peyroscythe.spellentity.ice.FrostFogEntity", remap = false)
public abstract class PeyroFrostFogServerOnlyMixin {
    @SuppressWarnings("rawtypes")
    @WrapOperation(method = "m_8119_()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$peyroscytheCoo49FrostFogSearch(Level level, Class<?> searched, AABB box, Predicate predicate, Operation<List> original) {
        List empty = PeyroAuras.clientFogSearch(level);
        return empty != null ? empty : original.call(level, searched, box, predicate);
    }
}
