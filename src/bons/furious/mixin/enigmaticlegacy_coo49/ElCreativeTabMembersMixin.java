package bons.furious.mixin.enigmaticlegacy_coo49;

import bons.furious.patch.enigmaticlegacy_coo49.CreativeTabMembers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * enigmaticlegacy_creative_tab_members_memo (Enigmatic Legacy 2.30.1, both sides; Enigmatic Legacy's own licence, treated
 * as All Rights Reserved: no code of the mod is carried). MixinExtras @WrapOperation on the ForgeRegistries.ITEMS.forEach
 * call of EnigmaticLegacy.onCreativeTabContents (one call per creative tab build event): CreativeTabMembers hands the same
 * consumer only the registry's ICreativeTabMember items, in the registry's own order, from a kept list that is checked
 * against the registry on every event. The consumer returns at once for every other item, so its calls (getTab,
 * getCreativeTab, getCreativeTabStacks, accept) and their order are unchanged.
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.EnigmaticLegacy", remap = false)
public abstract class ElCreativeTabMembersMixin {
    @WrapOperation(method = "onCreativeTabContents(Lnet/minecraftforge/event/BuildCreativeModeTabContentsEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/IForgeRegistry;forEach(Ljava/util/function/Consumer;)V"),
            require = 1, allow = 1)
    private void bons$enigmaticlegacyCoo49TabMembers(IForgeRegistry<?> registry, Consumer<?> consumer, Operation<Void> original) {
        CreativeTabMembers.forEach(registry, consumer, original);
    }
}
