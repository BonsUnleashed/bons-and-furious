package bons.furious.mixin.crypticfoes;

import bons.furious.patch.crypticfoes.EntityScan;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.min01.crypticfoes.event.EventHandlerForge;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crypticfoes_sound_listener_invariant (Cryptic Foes 1.0.4, both sides).
 *
 * Cryptic Foes wakes sleeping Howlers when a bell rings or a goat horn sounds nearby. Its two sound listeners run for
 * every sound played in a level and walk every entity of that level, and for each entity first test the sound (bell /
 * "goat_horn" in the sound id), which does not depend on the entity. The walk now stops after the first entity when
 * that sound test is false, because every later iteration would only skip (see EntityScan); bell and goat horn sounds
 * walk every entity exactly as before. Cryptic Foes is All Rights Reserved: this mixin only wraps the entity lookup
 * and carries none of its code.
 */
@Mixin(value = EventHandlerForge.class, remap = false)
public abstract class SoundListenerScanMixin {
    @WrapOperation(method = "onPlayLevelSoundAtPosition",
            at = @At(value = "INVOKE", target = "Lcom/min01/crypticfoes/util/CrypticUtil;getAllEntities(Lnet/minecraft/world/level/Level;)Ljava/lang/Iterable;"))
    private static Iterable<Entity> bons$bellScan(Level level, Operation<Iterable<Entity>> original, @Local Holder<SoundEvent> sound) {
        // compared as a plain reference, like the original (no cast to SoundEvent)
        return EntityScan.firstThenIf(original.call(level), () -> ((Holder<?>) sound).get() == SoundEvents.f_11699_);
    }

    @WrapOperation(method = "onPlayLevelSoundAtEntity",
            at = @At(value = "INVOKE", target = "Lcom/min01/crypticfoes/util/CrypticUtil;getAllEntities(Lnet/minecraft/world/level/Level;)Ljava/lang/Iterable;"))
    private static Iterable<Entity> bons$goatHornScan(Level level, Operation<Iterable<Entity>> original, @Local Holder<SoundEvent> sound) {
        return EntityScan.firstThenIf(original.call(level), () -> sound.get().m_11660_().toString().contains("goat_horn"));
    }
}
