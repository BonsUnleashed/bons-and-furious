package bons.furious.patch.cataclysm_client;

import com.github.alexmodguy.alexscaves.server.entity.item.NuclearBombEntity;
import com.github.alexmodguy.alexscaves.server.entity.living.GumWormSegmentEntity;
import com.github.alexmodguy.alexscaves.server.entity.living.SubterranodonEntity;
import com.github.alexmodguy.alexscaves.server.item.CandyCaneHookItem;
import com.github.alexmodguy.alexscaves.server.item.GalenaGauntletItem;
import com.github.alexmodguy.alexscaves.server.item.RaygunItem;
import com.github.alexmodguy.alexscaves.server.item.ResistorShieldItem;
import com.github.alexmodguy.alexscaves.server.item.ShotGumItem;
import com.github.alexmodguy.alexscaves.server.item.SpearItem;
import com.github.alexmodguy.alexscaves.server.potion.ACEffectRegistry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/**
 * citadel_pose_hand_events (Alex's Caves 2.0.2, client, since 1.0.36): whether Alex's Caves' ClientEvents.onPoseHand can act
 * for this entity. Ours; only names Alex's Caves classes (loaded only when its listener is registered).
 *
 * Read from the listener's bytecode (its INSTANCEOF types and the branches around every ModelPart write and setResult):
 * it writes only when a hand holds a Resistor Shield, Galena Gauntlet, Spear (while used), Raygun, Shot Gum or Candy Cane
 * Hook (both hands, riding a gum worm), when the entity rides a Subterranodon or a Nuclear Bomb, or when it has the Sugar
 * Rush effect. This test is true in each of those cases (a superset: item classes are tested without their use-time,
 * upright or active conditions); when it is false the listener only reads. Pure reads, the same ones the listener makes.
 */
public final class AlexsCavesPoseHand {
    private AlexsCavesPoseHand() {
    }

    static boolean mayAct(LivingEntity e) {
        if (acItem(e.m_21120_(InteractionHand.MAIN_HAND).m_41720_()) || acItem(e.m_21120_(InteractionHand.OFF_HAND).m_41720_())) return true;
        Entity v = e.m_20202_();
        if (v instanceof SubterranodonEntity || v instanceof NuclearBombEntity || v instanceof GumWormSegmentEntity) return true;
        return e.m_21023_(ACEffectRegistry.SUGAR_RUSH.get());
    }

    private static boolean acItem(Item i) {
        return i instanceof ResistorShieldItem || i instanceof GalenaGauntletItem || i instanceof SpearItem || i instanceof RaygunItem
                || i instanceof ShotGumItem || i instanceof CandyCaneHookItem;
    }
}
