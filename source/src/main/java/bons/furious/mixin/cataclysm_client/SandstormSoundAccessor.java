package bons.furious.mixin.cataclysm_client;

import com.github.L_Ender.cataclysm.client.sound.SandstormSound;
import com.github.L_Ender.cataclysm.entity.effect.Sandstorm_Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cataclysm_client_leaks: the sandstorm a SandstormSound follows (for ClientLeaks' identity check). 1.21.1 tested build
 * L_Ender's Cataclysm 1.21.1-3.33 (private final Sandstorm_Entity sandstom, as in 3.16).
 */
@Mixin(value = SandstormSound.class, remap = false)
public interface SandstormSoundAccessor {
    @Accessor("sandstom")
    Sandstorm_Entity bons$sandstorm();
}
