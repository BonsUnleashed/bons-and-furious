package bons.furious.mixin.biolith_c49;

import bons.furious.patch.biolith_c49.BiolithLean;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import java.util.function.Function;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * biolith_biome_entries_lock_free (Biolith 1.0.1-beta.1, both sides): a MixinExtras @WrapOperation on the same Either.map
 * call in MultiNoiseBiomeSource.parameters() (m_274409_) that Biolith wraps; priority 1500 (applied after Biolith's
 * mixin) makes it the outer wrapper. Biolith's handler builds its biome list once per biome source, under
 * synchronized(this), and from then on returns that same object on every call; the outer wrapper keeps the object
 * Biolith's handler returned the first time in its own field and returns it without entering Biolith's synchronized
 * handler (BiolithLean.entries). Until then, Biolith's handler runs as shipped.
 */
@Mixin(value = MultiNoiseBiomeSource.class, priority = 1500, remap = false)
public abstract class BiolithEntriesLockFreeMixin {
    /** The list Biolith's handler returned (written once, never changed; read without a lock, see BiolithLean). */
    @Unique
    private Object bons$biolithEntries;

    @WrapOperation(method = "m_274409_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/datafixers/util/Either;map(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object bons$biolithEntriesWrap(Either<?, ?> either, Function<?, ?> left, Function<?, ?> right, Operation<Object> original) {
        Object built = this.bons$biolithEntries;
        Object r = BiolithLean.entries(built, either, left, right, original);
        if (built == null && r != null && BiolithLean.lockFree) this.bons$biolithEntries = r;
        return r;
    }
}
