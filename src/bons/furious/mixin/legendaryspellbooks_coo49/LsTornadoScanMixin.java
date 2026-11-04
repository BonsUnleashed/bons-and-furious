package bons.furious.mixin.legendaryspellbooks_coo49;

import bons.furious.patch.legendaryspellbooks_coo49.LsTornadoGate;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * legendary_spellbooks_tornado_presence_gate (Legendary Spellbooks 0.4.1, LGPL-3.0; server levels): @Redirect of the
 * Level.getEntitiesOfClass (m_45976_) call in TornadoEntityHandler.onLivingUpdate (every living entity, every tick): while
 * vanilla_entity_class_count_layer says the level holds no Legendary Monsters Tornado, the search is marked EMPTY for
 * vanilla_entity_walk_skip (its section walk is left out; the call itself and the in_tornado tag logic run). A @Redirect,
 * not a @WrapOperation: with a tornado present the call stays a plain call (the speed gate's stand-aside case). See
 * LsTornadoGate. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "dev.higurashi.legendary_spellbooks.common.entities.handler.TornadoEntityHandler", remap = false)
public abstract class LsTornadoScanMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = "onLivingUpdate(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$legendaryspellbooksCoo49TornadoScan(Level level, Class searched, AABB box) {
        return LsTornadoGate.tornadoSearch(level, searched, box);
    }
}
