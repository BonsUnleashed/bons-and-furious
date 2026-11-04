package bons.furious.mixin.l2library_effects_c49;

import bons.furious.patch.l2library_effects_c49.TrackedEffects;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * l2library_tracked_effect_memo (both sides), part 1 of 2: Forge 47.4.28's ForgeRegistryTag answers its package-private
 * getContents() (the list its contains walks; rebuilt only after a bind) through TrackedEffects.Contents.
 */
@Mixin(targets = "net.minecraftforge.registries.ForgeRegistryTag", remap = false)
public interface ForgeRegistryTagContentsMixin extends TrackedEffects.Contents {
    @Override
    @Invoker("getContents")
    List<Object> bons$l2library_effects_c49Contents();
}
