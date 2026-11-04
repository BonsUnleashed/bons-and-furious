package bons.furious.patch.emf_lean;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import traben.entity_texture_features.features.property_reading.properties.optifine_properties.NBTProperty;
import traben.entity_texture_features.features.state.ETFEntityRenderState;

/**
 * Bons and Furious switch emf_nbt_tick_memo (Entity Model Features 3.2.4, also 3.3.10 / 3.3.12, with Entity Texture
 * Features; Minecraft 1.20.1 / Forge 47.4.28, client only). Trade-off switch, off by default. Idea: Collections Of
 * Optimizations (emf.cacheNbtPerTick), idea text only. SRG member names.
 *
 * What EMF does. A custom entity model's nbt(key, query) animation function asks ETF's NBTProperty.testEntity(state, false)
 * on every evaluation, both from the compiled expression (NBTMethod.nbtMethodStatic) and from the interpreted one (the
 * supplier lambda of NBTMethod's constructor). testEntity reads the entity's NBT (ETF keeps one copy per frame) and walks the
 * key path and the query on it. Animations are evaluated every frame, so at 120 fps each nbt() condition of each drawn mob
 * is evaluated six times per game tick.
 *
 * What the switch does. The result is remembered per (NBTProperty object, entity UUID) for the current client tick (the
 * client level's game time; another level starts afresh) and reused by the later frames of that tick; the next tick
 * evaluates again. Only on the thread that first used it (EMF animates on the render thread); calls with update = true,
 * without an entity state or without a client level run as shipped.
 *
 * Trade-off. An nbt() condition sees a change of the mob's NBT (equipment, name, other data) up to one tick (1/20 s) later
 * than before, and ETF's per-call log lines for a property in print mode or for an entity without NBT appear once per tick
 * instead of once per frame. Nothing else changes: the same NBTProperty decides, saved data and packets are untouched.
 *
 * -Dbons_and_furious.emfNbtTickMemo=false switches it off at run time (the config key emf_nbt_tick_memo decides whether
 * the mixin is applied at all). -Dbons_and_furious.emfNbtTickMemo.shadow=true (verification runs only): every call is
 * evaluated as shipped and its result returned; SHADOW_CHECKS counts the calls the memo would have answered and
 * SHADOW_MISMATCHES those whose remembered result differs from the fresh one (NBT changed within the tick; WARN for the
 * first 20).
 */
public final class EmfNbtTickMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.emfNbtTickMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.emfNbtTickMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Offline proof only: when set, this level object and testTick stand for the client's level and game time. */
    public static volatile Object testLevel;
    public static volatile long testTick;
    private static final IdentityHashMap<Object, HashMap<UUID, Boolean>> MEMO = new IdentityHashMap<>();
    private static volatile Thread owner;
    private static Object level;
    private static long tick;
    private static boolean announced;

    private EmfNbtTickMemo() {
    }

    /** NBTMethod's two NBTProperty.testEntity(state, update) calls, wrapped. */
    public static boolean test(NBTProperty property, ETFEntityRenderState state, boolean update, Operation<Boolean> original) {
        if (!enabled || update || state == null || property == null) return original.call(property, state, update);
        Thread t = Thread.currentThread();
        Thread o = owner;
        if (o == null) owner = o = t;
        if (o != t) return original.call(property, state, update);
        Object lvl;
        long now;
        Object fixture = testLevel;
        if (fixture != null) {
            lvl = fixture;
            now = testTick;
        } else {
            Minecraft mc = Minecraft.m_91087_();
            ClientLevel cl = mc == null ? null : mc.f_91073_;
            if (cl == null) return original.call(property, state, update);
            lvl = cl;
            now = cl.m_46467_();
        }
        if (lvl != level || now != tick) {
            MEMO.clear();
            level = lvl;
            tick = now;
        }
        UUID id = state.uuid();
        if (id == null) return original.call(property, state, update);
        HashMap<UUID, Boolean> byEntity = MEMO.computeIfAbsent(property, k -> new HashMap<>());
        Boolean known = byEntity.get(id);
        if (known != null) {
            if (!SHADOW) return known;
            boolean fresh = original.call(property, state, update);
            SHADOW_CHECKS.incrementAndGet();
            if (fresh != known) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: emf_nbt_tick_memo shadow: an nbt() result changed within tick {} for entity {} (remembered {}, now {})", now, id, known, fresh);
            }
            return fresh;
        }
        boolean result = original.call(property, state, update);
        byEntity.put(id, result);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: emf_nbt_tick_memo applies (EMF nbt() animation conditions are evaluated once per client tick per mob){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return result;
    }
}
