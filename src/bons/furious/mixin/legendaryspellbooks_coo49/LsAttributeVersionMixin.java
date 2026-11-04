package bons.furious.mixin.legendaryspellbooks_coo49;

import bons.furious.patch.legendaryspellbooks_coo49.LsAttributeVersionDuck;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.function.Consumer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * legendary_spellbooks_annihilation_bonus_memo (vanilla AttributeInstance, both sides; applied only while Legendary
 * Spellbooks is installed): counts every setDirty (m_22129_) call, through which every base value, modifier, replaceFrom and
 * load change goes, with a @ModifyExpressionValue on setDirty's read of its onDirty callback that hands the callback back
 * unchanged; and lets LsAnnihilationMemo read the instance's own dirty flag (f_22093_). Nothing of the instance's behaviour
 * changes.
 */
@Mixin(value = AttributeInstance.class, remap = false)
public abstract class LsAttributeVersionMixin implements LsAttributeVersionDuck {
    @Shadow
    private boolean f_22093_;
    @Unique
    private int bons$legendaryspellbooksCoo49Version;

    @ModifyExpressionValue(method = "m_22129_()V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;f_22095_:Ljava/util/function/Consumer;"),
            require = 1, allow = 1)
    private Consumer<AttributeInstance> bons$legendaryspellbooksCoo49Dirtied(Consumer<AttributeInstance> onDirty) {
        this.bons$legendaryspellbooksCoo49Version++;
        return onDirty;
    }

    @Override
    public int bons$legendaryspellbooksCoo49Version() {
        return this.bons$legendaryspellbooksCoo49Version;
    }

    @Override
    public boolean bons$legendaryspellbooksCoo49Dirty() {
        return this.f_22093_;
    }
}
