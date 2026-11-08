package bons.furious.mixin.biolith_c49;

import bons.furious.patch.biolith_c49.BiolithLean;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import java.util.function.Function;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * biolith_biome_entries_lock_free (Biolith, LGPL-3.0; 1.21.1 tested build: Biolith 3.0.14 for NeoForge; both sides): a
 * MixinExtras @WrapOperation on the same Either.map call in MultiNoiseBiomeSource.parameters() that Biolith wraps; priority
 * 1500 (applied after Biolith's mixin, priority 900) makes it the outer wrapper. Biolith's handler builds its biome list
 * under synchronized(this) while its field is null and returns that field; the outer wrapper reads the field through
 * Biolith's getter and returns the list it holds without entering Biolith's synchronized handler (BiolithLean.entries).
 * While the field is null (not built yet, or cleared by Biolith's refresh), Biolith's handler runs as shipped.
 *
 * Ported to 1.21.1: Biolith 3.0.14's biolith$injectEntries is the 1.0.1-beta.1 handler, but 3.0.14 can clear the list
 * (biolith$refreshBiomeEntries, at the head of NeoForge's ChunkGenerator.refreshFeaturesPerStep), so the wrapper keeps
 * no copy of its own (the 1.20.1 wrapper kept the first list in a field) and reads Biolith's current one each call.
 */
@Mixin(value = MultiNoiseBiomeSource.class, priority = 1500, remap = false)
public abstract class BiolithEntriesLockFreeMixin {
    @WrapOperation(method = "parameters()Lnet/minecraft/world/level/biome/Climate$ParameterList;", at = @At(value = "INVOKE",
            target = "Lcom/mojang/datafixers/util/Either;map(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object bons$biolithEntriesWrap(Either<?, ?> either, Function<?, ?> left, Function<?, ?> right, Operation<Object> original) {
        return BiolithLean.entries(this, either, left, right, original);
    }
}
