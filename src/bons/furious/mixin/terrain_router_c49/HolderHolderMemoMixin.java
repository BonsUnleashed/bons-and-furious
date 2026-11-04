package bons.furious.mixin.terrain_router_c49;

import bons.furious.patch.terrain_router_c49.RouterMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;

/**
 * terrain_router_shared_node_memo (Minecraft 1.20.1 world generation, both sides), part 2 of 2: HolderHolder.mapAll
 * (m_207456_, a registered density function referenced from the router) answers from RouterMemo's first-pass memo when the
 * visitor is NoiseChunk's first-pass visitor and this node was mapped before in the same pass; every other visitor runs
 * the method as shipped. Injectors only (Mojang code is no-copy).
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$HolderHolder", remap = false)
public abstract class HolderHolderMemoMixin {
    @WrapMethod(method = "m_207456_")
    private DensityFunction bons$sharedHolderMemo(DensityFunction.Visitor visitor, Operation<DensityFunction> original) {
        return RouterMemo.holder(this, visitor, original);
    }
}
