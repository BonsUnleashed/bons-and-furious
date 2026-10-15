package bons.portprobe;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.mcreator.sharks.item.SpetumItem;

/** Disposable player's client animation hand transitions. */
public final class SharksClient42 {
    public static Map<String, Object> run() {
        var player = Minecraft.getInstance().player;
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("benssharks", "spetum"));
        if (!(item instanceof SpetumItem spetum)) throw new AssertionError("Spetum unavailable");
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        String old = spetum.animationprocedure;
        int checks = 0;
        try {
            for (InteractionHand hand : InteractionHand.values()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                ItemStack stack = new ItemStack(item);
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("geckoAnim", "probe_" + hand));
                player.setItemInHand(hand, stack);
                net.mcreator.sharks.init.ItemAnimationFactory.animatedItems(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
                if (!spetum.animationprocedure.equals("probe_" + hand)
                        || !stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("geckoAnim").isEmpty())
                    throw new AssertionError("Client Spetum hand transition failed");
                checks++;
            }
            return Map.of("passed", true, "hand_checks", checks);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
            spetum.animationprocedure = old;
        }
    }
}