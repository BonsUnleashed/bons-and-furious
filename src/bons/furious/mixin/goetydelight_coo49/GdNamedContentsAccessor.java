package bons.furious.mixin.goetydelight_coo49;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * goetydelight_log_items_memo (Goety's Delight 1.5.2; both sides): read access to HolderSet.Named's contents list
 * (Minecraft 1.20.1 field f_205830_), so the kept log list can be checked by identity against the minecraft:logs tag's
 * current binding (MappedRegistry.bindTags / resetTags give it a new list; HolderSet.Named.bind m_205835_ stores it).
 * Read only; the member name carries the group (unique member names). See GdLogs.
 */
@Mixin(targets = "net.minecraft.core.HolderSet$Named", remap = false)
public interface GdNamedContentsAccessor {
    @Accessor("f_205830_")
    List<?> bons$goetydelightLogContents();
}
