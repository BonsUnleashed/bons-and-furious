package bons.furious.patch.cataclysm_client;

import bons.furious.mixin.cataclysm_client.ItemTickableSoundAccessor;
import bons.furious.mixin.cataclysm_client.SandstormSoundAccessor;
import com.github.L_Ender.cataclysm.ClientProxy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_client_leaks (L_Ender's Cataclysm, 1.21.1 tested build L_Ender's Cataclysm 1.21.1-3.33;
 * client, since 1.0.36). Fix. Ours; no Cataclysm code (CC-BY-NC-ND-4.0).
 *
 * Two client caches of Cataclysm keep entities (and through them their ClientLevel, with its chunks and entities) after
 * those entities left the client level:
 *  - five lightning holders (Boltstrike_Renderer, Death_Laser_beam_Renderer, Maledictus_Cicle_Layer, Scylla_Anchor_Layer,
 *    Scylla_Eye_Spark_Layer) keep a Map<UUID, LightningRender> whose bolts hold their owner entity; an entry is dropped
 *    only when render() is called for a removed (or, for the layers, dead) entity, which an entity that leaves the level
 *    alive never gets, so every such bolt or beam stays until the next resource reload;
 *  - ClientProxy.ENTITY_SOUND_INSTANCE_MAP (entity id -> sandstorm / Meat Shredder sound) drops a sandstorm sound only at
 *    the storm's natural end and a Meat Shredder sound only when the local player releases the item.
 * The fix: when an entity leaves a client level (NeoForge EntityLeaveLevelEvent), its UUID is removed from the five maps
 * and its id from the sound map when that entry's sound belongs to this very entity; when a client level unloads, all six
 * are cleared. A removed entity's sound entry is never reused (isSameEntity requires the old entity alive), so dropping it
 * changes nothing. Visible differences: a lightning-casting entity that leaves and comes back while its old bolts are
 * still fading starts with fresh bolts; and after a level change a sandstorm or Meat Shredder user that happens to get the
 * id of an entity of the old level no longer has its sound bound to that old entity (stock keeps playing it at the old
 * entity's last position).
 *
 * Ported to 1.21.1: the listeners go to NeoForge.EVENT_BUS (ClientLevel's entity callbacks post EntityLeaveLevelEvent on
 * tracking end, Minecraft.setLevel / disconnect / clearClientLevel post LevelEvent.Unload for the old client level); the
 * caches, their holders and playWorldSound / clearSoundCacheFor / isSameEntity are unchanged in 3.33. Registration moved
 * with Cataclysm's proxy (ClientProxyLeakHooksMixin).
 *
 * -Dbons_and_furious.cataclysmClientLeaks=false keeps every entry as before.
 */
public final class ClientLeaks {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmClientLeaks", "true"));
    private static final List<WeakReference<Map<UUID, ?>>> LIGHTNING_MAPS = new ArrayList<>();
    private static volatile boolean registered, announced;

    private ClientLeaks() {
    }

    /** From the five holders' constructors: their lightning map. */
    public static void track(Map<UUID, ?> map) {
        synchronized (LIGHTNING_MAPS) {
            LIGHTNING_MAPS.removeIf(r -> r.get() == null);
            LIGHTNING_MAPS.add(new WeakReference<>(map));
        }
    }

    /** From ClientProxy's constructor (client only): the two listeners. */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityLeaveLevelEvent.class, ClientLeaks::onLeave);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LevelEvent.Unload.class, ClientLeaks::onUnload);
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!enabled || !event.getLevel().isClientSide()) return;
        leave(event.getEntity());
    }

    public static void leave(Entity entity) {
        UUID uuid = entity.getUUID();
        boolean any = false;
        synchronized (LIGHTNING_MAPS) {
            for (Iterator<WeakReference<Map<UUID, ?>>> it = LIGHTNING_MAPS.iterator(); it.hasNext(); ) {
                Map<UUID, ?> m = it.next().get();
                if (m == null) it.remove();
                else any |= m.remove(uuid) != null;
            }
        }
        int id = entity.getId();
        AbstractTickableSoundInstance s = ClientProxy.ENTITY_SOUND_INSTANCE_MAP.get(id);
        if (s != null && ((s instanceof SandstormSoundAccessor a && a.bons$sandstorm() == entity)
                || (s instanceof ItemTickableSoundAccessor b && b.bons$user() == entity))) {
            ClientProxy.ENTITY_SOUND_INSTANCE_MAP.remove(id);
            any = true;
        }
        if (any) announce();
    }

    public static void onUnload(LevelEvent.Unload event) {
        if (!enabled || !event.getLevel().isClientSide()) return;
        unload();
    }

    public static void unload() {
        synchronized (LIGHTNING_MAPS) {
            for (Iterator<WeakReference<Map<UUID, ?>>> it = LIGHTNING_MAPS.iterator(); it.hasNext(); ) {
                Map<UUID, ?> m = it.next().get();
                if (m == null) it.remove();
                else m.clear();
            }
        }
        ClientProxy.ENTITY_SOUND_INSTANCE_MAP.clear();
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cataclysm_client_leaks applies (lightning and sound caches drop entities that left the client level)");
        }
    }
}
