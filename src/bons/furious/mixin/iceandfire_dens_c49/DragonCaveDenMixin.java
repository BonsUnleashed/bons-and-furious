package bons.furious.mixin.iceandfire_dens_c49;

import bons.furious.patch.iceandfire_dens_c49.Dens;
import com.github.alexthe666.iceandfire.world.gen.WorldGenDragonCave;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;

/**
 * iceandfire_structure_dens (Ice and Fire 2.1.13; server world generation; since 1.0.49; a trade-off, off by default): the
 * dragon cave feature (fire, ice and lightning share this method) generates nothing while the structure dens are in place;
 * the den structures call its generateCave themselves. Our own logic only.
 */
@Mixin(value = WorldGenDragonCave.class, remap = false)
public abstract class DragonCaveDenMixin {
    @WrapMethod(method = "m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z", remap = false)
    private boolean bons$structureDens(FeaturePlaceContext<NoneFeatureConfiguration> context, Operation<Boolean> original) {
        return Dens.stockCave(context, original);
    }
}
