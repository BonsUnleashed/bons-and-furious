package bons.furious.patch.cucumber_coo49;

import com.blakebr0.cucumber.config.ModConfigs;
import com.blakebr0.cucumber.helper.FluidHelper;
import com.blakebr0.cucumber.lib.Tooltips;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch cucumber_tag_tooltip_presence_first (since 1.0.49; Cucumber 1.21.1-8.0.16; client). Helper of
 * CucumberTagTooltipMixin; no Cucumber code is carried.
 *
 * What Cucumber does. With its tag tooltips on and advanced tooltips on (F3+H), TagTooltipHandler.onItemTooltip builds,
 * for every hovered item on every frame (and for every item a recipe viewer indexes): the list of the item's block's tags,
 * the list of its item tags, and its fluid tags (FluidHelper.getFluidTags: the NeoForge fluid-handler item capability, per
 * non-empty tank fluid a sorted tag list, then all of them flattened, made distinct and sorted). Unless CTRL is held it then
 * prints only "Hold CTRL for tags", and only when one of the three lists is not empty.
 *
 * What the switch does. With CTRL not held it decides that one line from presence alone: the block (not air) has a tag,
 * or the item has a tag, or one of the fluids FluidHelper reports has a tag. FluidHelper.getFluidTags is still called
 * exactly once, as the original calls it (it asks the capability); only the flattening, distinct and sort of its result
 * and the two location lists go (a flattened list is empty exactly when every per-fluid list is). The tag reads are plain
 * reads of the registry holders, so leaving the item's tags unread once the block has one changes nothing. CTRL held,
 * tooltips off or advanced tooltips off: the original runs. The CTRL state is read first instead of last; it is a plain
 * key-state read. Same tooltip lines, in the same order. -Dbons_and_furious.cucumberTagTooltipPresenceFirst=false runs the
 * original.
 */
public final class CucumberTagTooltips {
    /** cucumber_tag_tooltip_presence_first: -Dbons_and_furious.cucumberTagTooltipPresenceFirst=false runs the original. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cucumberTagTooltipPresenceFirst", "true"));
    private static volatile boolean announced;

    private CucumberTagTooltips() {
    }

    /** True when the tooltip was handled here (the original must not run); false = run the original. */
    public static boolean handled(ItemTooltipEvent event) {
        if (!enabled || !ModConfigs.ENABLE_TAG_TOOLTIPS.get() || !Minecraft.getInstance().options.advancedItemTooltips || Screen.hasControlDown()) return false;
        ItemStack stack = event.getItemStack();
        Block block = Block.byItem(stack.getItem());
        boolean any = block != Blocks.AIR && block.defaultBlockState().getTags().findAny().isPresent();
        if (!any) any = stack.getTags().findAny().isPresent();
        Map<Fluid, List<ResourceLocation>> fluids = FluidHelper.getFluidTags(stack);
        if (!any) {
            for (List<ResourceLocation> tags : fluids.values()) {
                if (!tags.isEmpty()) {
                    any = true;
                    break;
                }
            }
        }
        if (any) event.getToolTip().add(Tooltips.HOLD_CTRL_FOR_TAGS.build());
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: cucumber_tag_tooltip_presence_first applies (the tag tooltip line is decided without building the tag lists)");
        }
        return true;
    }
}
