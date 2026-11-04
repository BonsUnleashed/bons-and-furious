package bons.furious.patch.cognition_coo49;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * Bons and Furious 1.0.49 switch cognition_tooltip_owner_first (Cognition / experienceobelisk 2.4.12, client; exact; helper of
 * DescriptionTooltipOwnerMixin; no Cognition code carried).
 *
 * What Cognition does. DescriptionTooltips.handleTooltip runs for every tooltip of every item: it takes the item's creator mod
 * id, then builds the item's description id + ".description", resolves that through the language (a translatable component
 * rendered to a string) and polls both shift keys off the window, and only then adds lines, and only when the creator mod id
 * is "experienceobelisk" and the description exists. For every other item nothing is added.
 *
 * What the switch does. It takes the creator mod id first (the stock method's first statement, the same calls) and returns at
 * once when it is not "experienceobelisk"; Cognition's own items run the stock method unchanged (which asks for the id
 * again). Exact: the skipped work only builds strings and components and reads key states (pure reads) before deciding to
 * add nothing. Runtime flag bons_and_furious.cognitionTooltipOwnerFirst.
 */
public final class TooltipOwner {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cognitionTooltipOwnerFirst", "true"));
    static final String OWNER = "experienceobelisk";

    private TooltipOwner() {
    }

    /** True when the tooltip's item is not Cognition's (the handler adds nothing then). */
    public static boolean foreign(ItemTooltipEvent event) {
        if (!enabled) return false;
        ItemStack item = event.getItemStack();
        String modId = item.m_41720_().getCreatorModId(item);
        return !OWNER.equals(modId);
    }
}
