package bons.furious.mixin.goety_brew_coo49;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * goety_brew_effects_page_memo (Goety 3.2.2; client): read access to HolderSet.Named's contents list (Minecraft 1.21.1
 * field contents), so the kept brew table can be checked by identity against the goety:villagers entity tag's current
 * binding (MappedRegistry.bindTags / resetTags bind it again; HolderSet.Named.bind stores List.copyOf of the new
 * contents). Read only; the member name carries the group (unique member names). See GoetyBrews.
 */
@Mixin(targets = "net.minecraft.core.HolderSet$Named", remap = false)
public interface GbNamedContentsAccessor {
    @Accessor("contents")
    List<?> bons$goetyBrewNamedContents();
}
