package bons.furious.mixin.mysticalagriculture_coo49;

import bons.furious.patch.mysticalagriculture_coo49.SoulExtractorIdle;
import com.blakebr0.cucumber.inventory.CachedRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * mysticalagriculture_soul_extractor_idle_skip (Mystical Agriculture with Cucumber, MIT; 1.21.1 tested build: Mystical
 * Agriculture 1.21.1-8.0.29 with Cucumber 1.21.1-8.0.16, server; kind opt, default on; no Mystical Agriculture or Cucumber
 * code carried). Cucumber's CachedRecipe.check(input, level): right before its look-up
 * (level.getRecipeManager().getRecipeFor(type, input, level), reached after the remembered recipe did not match), the check
 * answers false and forgets the remembered recipe, exactly what the look-up's empty answer makes it do, when the look-up
 * certainly finds no soul extraction recipe (an empty input slot and a proven recipe set; see SoulExtractorIdle). Every other
 * recipe type and every other case runs the mod's look-up itself, from the mod's own call (an @Inject before it, so the scan
 * keeps the shape it has as shipped). require = 0: when another mod already changed that call, the switch stands aside and
 * the group's plugin logs one INFO line (MysticalAgriculturePlugin).
 *
 * Ported to 1.21.1: Cucumber 8.0.16 has one check(RecipeInput, Level) (1.20.1 had a Container and an item handler overload);
 * the remembered recipe is stored from getRecipeFor(...).map(RecipeHolder::value).orElse(null).
 */
@Mixin(value = CachedRecipe.class, remap = false)
public abstract class SoulRecipeScanMixin {
    @Shadow
    @Final
    private RecipeType<?> type;

    @Shadow
    private Recipe<?> recipe;

    @Inject(method = "check(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getRecipeManager()Lnet/minecraft/world/item/crafting/RecipeManager;"),
            cancellable = true, require = 0, allow = 1)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void bons$mysticalagricultureSoulScan(RecipeInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        RecipeType t = this.type;
        if (SoulExtractorIdle.enabled && t != null && t == SoulExtractorIdle.soulType() && SoulExtractorIdle.none(level, t, input)) {
            if (SoulExtractorIdle.SHADOW) {
                // shadow mode: the mod's own look-up runs next as shipped; the answer of the same look-up is compared here
                SoulExtractorIdle.shadow(level.getRecipeManager().getRecipeFor(t, input, level));
                return;
            }
            this.recipe = null;
            cir.setReturnValue(false);
        }
    }
}
