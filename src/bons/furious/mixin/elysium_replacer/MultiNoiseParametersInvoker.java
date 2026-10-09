package bons.furious.mixin.elysium_replacer;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * elysium_lean_replacer (ElysiumAPI 1.1.3, both sides): calls MultiNoiseBiomeSource.parameters() (m_274409_, private in
 * vanilla, public through ElysiumAPI's access transformer), which ElysiumAPI's handler uses when TerraBlender is not
 * installed. Invoker only.
 */
@Mixin(value = MultiNoiseBiomeSource.class, remap = false)
public interface MultiNoiseParametersInvoker {
    @Invoker("m_274409_")
    Climate.ParameterList<Holder<Biome>> bons$elParameters();
}
