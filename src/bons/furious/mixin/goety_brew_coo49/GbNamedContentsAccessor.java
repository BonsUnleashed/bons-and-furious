package bons.furious.mixin.goety_brew_coo49;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * goety_brew_effects_page_memo (Goety 2.5.60.0; client): read access to HolderSet.Named's contents list (Minecraft
 * 1.20.1 field f_205830_), so the kept brew table can be checked by identity against the goety:villagers entity tag's
 * current binding (MappedRegistry.bindTags / resetTags give it a new list; HolderSet.Named.bind m_205835_ stores it).
 * Read only; the member name carries the group (unique member names). See GoetyBrews.
 */
@Mixin(targets = "net.minecraft.core.HolderSet$Named", remap = false)
public interface GbNamedContentsAccessor {
    @Accessor("f_205830_")
    List<?> bons$goetyBrewNamedContents();
}
