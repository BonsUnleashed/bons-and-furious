package bons.furious.mixin.cataclysm_client;

import com.github.L_Ender.cataclysm.client.sound.ItemTickableSound;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cataclysm_client_leaks: the entity an ItemTickableSound (Meat Shredder) follows (for ClientLeaks' identity check). 1.21.1
 * tested build L_Ender's Cataclysm 1.21.1-3.33 (protected final LivingEntity user, as in 3.16; MeatShredderSound still
 * extends it).
 */
@Mixin(value = ItemTickableSound.class, remap = false)
public interface ItemTickableSoundAccessor {
    @Accessor("user")
    LivingEntity bons$user();
}
