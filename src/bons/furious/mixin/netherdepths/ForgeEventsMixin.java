package bons.furious.mixin.netherdepths;

import com.scouter.netherdepthsupgrade.enchantments.NDUEnchantments;
import com.scouter.netherdepthsupgrade.events.ForgeEvents;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * netherdepths_reuse_enchantment_decode (Nether Depths Upgrade 3.1.5).
 *
 * The Hell Strider lava-speed handler decoded the boots' enchantment list twice on every player tick: once to check
 * for Hell Strider and again to read its level. For the three vanilla player classes (exact class, compared by name so
 * the client-only classes are never loaded on a server) the level is now read from the map decoded for the check; any
 * other Player class, including modded subclasses whose getItemBySlot might differ between calls, still reads and
 * decodes a second time and uses that result, as before. The handler keeps its @SubscribeEvent registration.
 */
@Mixin(value = ForgeEvents.class, remap = false)
public abstract class ForgeEventsMixin {
    /**
     * @author BonsUnleashed
     * @reason Reuse the decoded boot enchantments for the Hell Strider level instead of decoding them twice per tick.
     */
    @Overwrite
    @SubscribeEvent
    public static void lavaMovementSpeed(TickEvent.PlayerTickEvent event) {
        if (event.player == null || event.player.m_7500_() || event.player.m_5833_()) {
            return;
        }
        double d0 = 0.0;
        boolean flag = event.player.m_20184_().f_82480_ <= 0.0;
        if (flag && event.player.m_21023_(MobEffects.f_19591_)) {
            d0 = 0.01;
        }
        Map<Enchantment, Integer> bootEnchantments;
        if ((bootEnchantments = EnchantmentHelper.m_44831_(event.player.m_6844_(EquipmentSlot.FEET))).containsKey(NDUEnchantments.HELL_STRIDER.get())) {
            double level = (!ac$ordinaryPlayer(event.player)
                    ? EnchantmentHelper.m_44831_(event.player.m_6844_(EquipmentSlot.FEET))
                    : bootEnchantments).get(NDUEnchantments.HELL_STRIDER.get());
            Player player = event.player;
            BlockPos eyePos = new BlockPos((int) player.m_146892_().m_7096_(), (int) player.m_146892_().m_7098_(), (int) player.m_146892_().m_7094_());
            FluidState state = player.m_9236_().m_6425_(eyePos);
            if (player.m_20077_() && player.m_6129_() && state.m_205070_(FluidTags.f_13132_)) {
                double e = player.m_20186_();
                float speed = (float) (1.15 + 0.1 * level);
                player.m_20256_(player.m_20184_().m_82542_(speed, 0.8F, speed));
                Vec3 vec33 = player.m_20994_(d0, flag, player.m_20184_());
                player.m_20256_(vec33);
                if (player.m_6144_()) {
                    player.m_20334_(vec33.f_82479_, -0.075000001192092896 * level, vec33.f_82481_);
                }
                if (player.f_19862_ && player.m_20229_(vec33.f_82479_, vec33.f_82480_ + 0.6000000238418579 - player.m_20186_() + e, vec33.f_82481_)) {
                    player.m_20334_(vec33.f_82479_, 0.30000001192092896, vec33.f_82481_);
                }
            }
        }
    }

    /** True for null and the three vanilla player classes (exact class), whose two equipment reads always agree. */
    @Unique
    private static boolean ac$ordinaryPlayer(Player player) {
        if (player == null) {
            return true;
        }
        String name = player.getClass().getName();
        return name.equals("net.minecraft.server.level.ServerPlayer") || name.equals("net.minecraft.client.player.LocalPlayer")
                || name.equals("net.minecraft.client.player.RemotePlayer");
    }
}
