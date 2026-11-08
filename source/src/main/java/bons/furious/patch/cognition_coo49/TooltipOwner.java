package bons.furious.patch.cognition_coo49;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Bons and Furious switch cognition_tooltip_owner_first (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for NeoForge
 * 1.21.1, client; exact; helper of DescriptionTooltipOwnerMixin; no Cognition code carried).
 *
 * What Cognition does. DescriptionTooltips.handleTooltip runs for every tooltip of every item: it takes the item's creator mod
 * id (Item.getCreatorModId), then builds the item's description id + ".description", resolves that through the language (a
 * translatable component rendered to a string) and polls both shift keys off the window, and only then adds lines, and only
 * when the creator mod id is "cognition" and the description exists. For every other item nothing is added.
 *
 * What the switch does. It takes the creator mod id first (the stock method's first statement, the same calls) and returns at
 * once when it is not "cognition"; Cognition's own items run the stock method unchanged (which asks for the id again).
 * Exact: the skipped work only builds strings and components and reads key states (pure reads) before deciding to add
 * nothing. Runtime flag bons_and_furious.cognitionTooltipOwnerFirst.
 */
public final class TooltipOwner {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cognitionTooltipOwnerFirst", "true"));
    static final String OWNER = "cognition";

    private TooltipOwner() {
    }

    /** True when the tooltip's item is not Cognition's (the handler adds nothing then). */
    public static boolean foreign(ItemTooltipEvent event) {
        if (!enabled) return false;
        ItemStack item = event.getItemStack();
        String modId = item.getItem().getCreatorModId(item);
        return !OWNER.equals(modId);
    }
}
