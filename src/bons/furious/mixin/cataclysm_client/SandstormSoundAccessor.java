package bons.furious.mixin.cataclysm_client;

import com.github.L_Ender.cataclysm.client.sound.SandstormSound;
import com.github.L_Ender.cataclysm.entity.effect.Sandstorm_Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** cataclysm_client_leaks: the sandstorm a SandstormSound follows (for ClientLeaks' identity check). */
@Mixin(value = SandstormSound.class, remap = false)
public interface SandstormSoundAccessor {
    @Accessor("sandstom")
    Sandstorm_Entity bons$sandstorm();
}
