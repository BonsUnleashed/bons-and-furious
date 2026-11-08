package bons.furious.patch.arsnspells_coo49;

import net.neoforged.neoforge.attachment.AttachmentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group arsnspells_coo49 (Ars 'n' Spells 3.3.5 for NeoForge 1.21.1): arsnspells_keep_cooldowns_on_clone
 * (gameplay fix, off by default). Helper of AnsCooldownAttachmentMixin; Ars 'n' Spells is GPL-3.0, no code of it is carried
 * (the logic here is ours).
 *
 * <p>The mod keeps its per-category spell cooldowns (absolute end ticks, CooldownData) in a NeoForge data attachment that is
 * saved with the player and sent to the client on respawn and dimension change (CapabilityResyncHandler). Its attachment
 * types (AttachmentTypes) mark the affinity and progression data copyOnDeath but not the cooldowns, so NeoForge's
 * PlayerEvent.Clone copy (AttachmentInternals.onPlayerClone: on a death only copyOnDeath types, on the way back from the
 * End every serialized type) leaves the respawned player without cooldowns: a death clears every cooldown, and the respawn
 * sync then tells the client they are over. With the switch the cooldown attachment type is built copyOnDeath as well, so
 * NeoForge copies it through the mod's own codec (CooldownData.CODEC: category name -> end tick) like the other two. What a
 * player can notice: cooldowns survive a death as they survive a log-out and the way back from the End.
 *
 * <p>Runtime flag (=false: off): bons_and_furious.arsnspellsKeepCooldownsOnClone. The type is built once, when Ars 'n'
 * Spells registers its attachment types, so the flag counts at start-up only.
 */
public final class AnsCooldowns {
    static final String KEY = "arsnspells_keep_cooldowns_on_clone";
    public static volatile boolean keep = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.arsnspellsKeepCooldownsOnClone", "true"));

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    private AnsCooldowns() {
    }

    /**
     * Inside AttachmentTypes' cooldown supplier, right before its builder's build(): the builder, marked copyOnDeath when
     * the switch is on (the builder already has its serializer, CooldownData.CODEC; without one it is left as it is).
     */
    public static AttachmentType.Builder<?> copyOnDeath(AttachmentType.Builder<?> builder) {
        if (!keep || builder == null) return builder;
        try {
            builder.copyOnDeath();
            LOGGER.info("Bons and Furious: {}: Ars 'n' Spells' spell cooldowns are copied to the respawned player", KEY);
        } catch (IllegalStateException e) {
            LOGGER.warn("Bons and Furious: {}: the cooldown attachment has no serializer ({}); it is built as shipped", KEY, e.getMessage());
        }
        return builder;
    }
}
