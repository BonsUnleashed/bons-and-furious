package bons.furious.patch.vanilla_framed_maps;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_framed_map_holder_scan (Minecraft 1.20.1 on Forge 47.4.16; server side, including the
 * integrated server). SRG member names.
 *
 * What vanilla does. Every 10 ticks, for every map in an item frame, ServerEntity.sendChanges calls
 * MapItemSavedData.tickCarriedBy (m_77918_) once for every player in the level. tickCarriedBy first asks whether that
 * player's inventory holds the map (Inventory.contains), then walks every player the map has seen (carriedBy) and asks
 * for each one `!player.isRemoved() && (player.getInventory().contains(map) || map.isFramed())`. A framed map keeps
 * every player of the level in carriedBy, so each framed map costs players x (1 + players) inventory scans per update;
 * with Curios installed (this pack) every contains also scans the player's curio slots.
 *
 * What the switch does. That second contains call, the one inside the loop, is answered `true` without scanning when the
 * map stack is framed (ItemStack.isFramed, the same test the expression makes next). For a framed stack
 * `contains(map) || isFramed()` is true whatever contains returns, so the branch, the decorations, carriedBy and every
 * player's dirty state come out the same; contains only reads the inventory (vanilla's loop over the compartments, and
 * Curios' TAIL hook that reads the curio stacks). Maps held in hands (not framed) and the first contains call (whose
 * answer matters: it removes the player's own marker) are unchanged.
 *
 * -Dbons_and_furious.framedMapHolderScan=false switches it off at run time.
 * -Dbons_and_furious.framedMapHolderScan.shadow=true (verification runs only): the scan runs as in vanilla, and
 * SHADOW_SKIPPABLE counts the scans the switch would leave out (SHADOW_CHECKS / SHADOW_MISMATCHES: a skipped scan whose
 * stack was not framed, which cannot happen).
 */
public final class FramedMaps {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.framedMapHolderScan", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.framedMapHolderScan.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_SKIPPABLE = new AtomicLong();
    private static volatile boolean announced;

    private FramedMaps() {
    }

    /** MapItemSavedData.tickCarriedBy: the per-holder Inventory.contains(mapStack) call. */
    public static boolean holderHasMap(Inventory inventory, ItemStack mapStack, Operation<Boolean> original) {
        if (!enabled || !mapStack.m_41794_()) {
            return original.call(inventory, mapStack);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_framed_map_holder_scan applies (framed maps no longer scan every player's inventory for every player){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            boolean vanilla = original.call(inventory, mapStack);
            SHADOW_CHECKS.incrementAndGet();
            SHADOW_SKIPPABLE.incrementAndGet();
            if (!mapStack.m_41794_()) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: framed map shadow mismatch #{}: the stack stopped being framed during the scan", m);
            }
            return vanilla;
        }
        return true;
    }
}
