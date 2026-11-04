package bons.furious.patch.arsnspells_coo49;

import com.otectus.arsnspells.data.CooldownData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group arsnspells_coo49 (Ars 'n' Spells 3.3.5): arsnspells_keep_cooldowns_on_clone (gameplay fix,
 * off by default). Helper of AnsCloneCooldownsMixin; Ars 'n' Spells is GPL-3.0, no code of it is carried (the logic here
 * is ours, through the mod's public save / load).
 *
 * <p>The mod keeps its per-category spell cooldowns (absolute end ticks) in a player capability, saves them with the
 * player and sends them to the client on respawn and dimension change, but its PlayerEvent.Clone handler copies only the
 * affinity and progression data into the new player: a death (or the way back from the End) clears every cooldown on the
 * server while the client's copy keeps counting. With the switch the handler also copies the cooldowns (CooldownData.save
 * of the old player's data, load into the new one), before it invalidates the old player's capabilities. What a player
 * can notice: cooldowns survive a death and the return from the End, as they survive a log-out.
 *
 * <p>Runtime flag (=false: off): bons_and_furious.arsnspellsKeepCooldownsOnClone.
 */
public final class AnsCooldowns {
    static final String KEY = "arsnspells_keep_cooldowns_on_clone";
    public static volatile boolean keep = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.arsnspellsKeepCooldownsOnClone", "true"));
    /** Statistics (plain field): clones whose cooldowns were copied. */
    public static long copied;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private AnsCooldowns() {
    }

    /** Inside ModCapabilityProvider.onPlayerClone, right before the old player's capabilities are invalidated. */
    public static void copy(PlayerEvent.Clone event) {
        if (!keep || event == null) return;
        Player old = event.getOriginal(), now = event.getEntity();
        if (old == null || now == null) return;
        old.getCapability(CooldownData.COOLDOWN_CAP).ifPresent(from -> now.getCapability(CooldownData.COOLDOWN_CAP).ifPresent(to -> {
            CompoundTag tag = new CompoundTag();
            from.save(tag);
            to.load(tag);
            copied++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {}: spell cooldowns are carried to the respawned player", KEY);
            }
        }));
    }
}
