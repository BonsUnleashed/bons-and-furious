package bons.furious.mixin.goetydelight_coo49;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * goetydelight_log_items_memo (Goety's Delight 1.5.0-1.21.1; both sides): read access to HolderSet.Named's contents list
 * (Minecraft 1.21.1 field contents), so the kept log list can be checked by identity against the minecraft:logs tag's
 * current binding (MappedRegistry.bindTags / resetTags bind it again; HolderSet.Named.bind stores List.copyOf of the new
 * contents). Read only; the member name carries the group (unique member names). See GdLogs.
 */
@Mixin(targets = "net.minecraft.core.HolderSet$Named", remap = false)
public interface GdNamedContentsAccessor {
    @Accessor("contents")
    List<?> bons$goetydelightLogContents();
}
