package bons.furious.patch.summonity_coo49;

import com.fevzi.summonity.entity.SummonedMinion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch summonity_owned_minions_tick_memo (Summonity 0.1.7.1, server side; kind change, off by default).
 * Ours; no Summonity code is carried. Idea: Collections Of Optimizations (summonity.cacheOwnedMinionScans), idea text only.
 *
 * MinionHelper.getOwnedMinions(level, owner) searches every Mob in the owner's box grown by 128 blocks, keeps the
 * SummonedMinions whose owner id is the owner's UUID and sorts them by entity id; most minions ask it several times per
 * tick (formation slots, focus targets, slot counts), so ten minions make 10-15 searches of a 257-block box per tick.
 *
 * With the switch on, the first ask of a server tick for an owner computes the list as shipped and later asks of that
 * owner in the same tick (same level, same game time) get the same list. A tick in which a summoned minion joined or left
 * a level (Forge's EntityJoinLevelEvent / EntityLeaveLevelEvent, listened to at the highest priority, cancelled events
 * included) keeps nothing: every ask of that tick runs as shipped, so summoning and removal are seen at once. Trade-off:
 * within one tick, a minion or owner moving across the 128-block edge, a minion changing owner, or a minion becoming
 * visible by chunk loading is seen at the next tick (the list's order is the entity id order either way). A player who
 * leaves a level drops his kept list. Flag: -Dbons_and_furious.summonityOwnedMinionsTickMemo=false.
 */
public final class OwnedMinions {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.summonityOwnedMinionsTickMemo", "true"));
    /** Asks answered from the kept list, and summoned minions seen joining or leaving. */
    public static final AtomicLong REUSES = new AtomicLong();
    public static final AtomicLong CHANGES = new AtomicLong();
    private static final Map<Player, Kept> KEPT = new ConcurrentHashMap<>();
    /** Per level: the game time of the last tick in which a summoned minion joined or left it (weak keys). */
    private static final Map<ServerLevel, long[]> CHANGED = new java.util.WeakHashMap<>();
    private static volatile int listening;   // 0 not yet, 1 registered, -1 failed (then stock)
    private static volatile boolean announced;

    private OwnedMinions() {
    }

    private record Kept(ServerLevel level, long time, List<?> list) {
    }

    /** The wrapped getOwnedMinions call. */
    public static List<?> get(ServerLevel level, Player owner, Operation<List<?>> original) {
        if (!enabled || level == null || owner == null || !listening()) return original.call(level, owner);
        long now = level.m_46467_();
        Kept k = KEPT.get(owner);
        if (k != null && k.time == now && k.level == level && !changedNow(level, now)) {
            REUSES.incrementAndGet();
            if (!announced) announce();
            return k.list;
        }
        List<?> list = original.call(level, owner);
        if (!changedNow(level, now)) KEPT.put(owner, new Kept(level, now, list));
        else KEPT.remove(owner);
        return list;
    }

    private static boolean changedNow(ServerLevel level, long now) {
        synchronized (CHANGED) {
            long[] t = CHANGED.get(level);
            return t != null && t[0] == now;
        }
    }

    private static boolean listening() {
        int l = listening;
        if (l == 0) l = register();
        return l > 0;
    }

    private static synchronized int register() {
        if (listening != 0) return listening;
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, EntityJoinLevelEvent.class, e -> changed(e.getEntity(), e.getLevel()));
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, EntityLeaveLevelEvent.class, e -> changed(e.getEntity(), e.getLevel()));
            listening = 1;
        } catch (Throwable t) {
            listening = -1;
            LOGGER.warn("Bons and Furious: summonity_owned_minions_tick_memo cannot listen to entity join / leave events ({}); getOwnedMinions runs as shipped", t.toString());
        }
        return listening;
    }

    /** A join or leave: a summoned minion ends keeping for this tick; a player drops his kept list. */
    public static void changed(Entity entity, Object level) {
        if (entity instanceof SummonedMinion && level instanceof ServerLevel server) {
            long now = server.m_46467_();
            synchronized (CHANGED) {
                CHANGED.computeIfAbsent(server, l -> new long[1])[0] = now;
            }
            CHANGES.incrementAndGet();
        }
        if (entity instanceof Player p) KEPT.remove(p);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: summonity_owned_minions_tick_memo applies (an owner's minion list is searched once per tick)");
    }

    /** For the offline proof: listeners treated as registered (the proof calls changed() itself). */
    public static void listeningForProof() {
        listening = 1;
    }
}
