package bons.furious.patch.cataclysm_client_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_tentacle_light_memo (L_Ender's Cataclysm 3.16, 3.31 identical; client; since 1.0.49).
 * Idea: Collections Of Optimizations (cataclysm.cacheTentacleLight), idea text only. Our own logic; no Cataclysm code
 * (CC-BY-NC-ND-4.0 target, injectors only).
 *
 * The Leviathan's tongue (The_Leviathan_Renderer.render) and the Tidal Claws' tentacle (Tidal_Tentacle_Renderer.render)
 * are drawn as up to 128 segments; each segment, and the claw at the end, asks the renderer's private
 * getLightColor(entity, position) for the packed light, which reads level.hasChunkAt and two LevelRenderer.getLightColor
 * (the block state and the block and sky light at the position and the position above it). Consecutive segments are half a
 * block apart, so most of them ask for a block position an earlier segment of the same draw asked for.
 *
 * What changes: within one render() call, the answer for a block position (Mth.floor of x, y, z, exactly the BlockPos the
 * private method builds) is kept and handed to later calls for the same position of the same entity. Why it is identical:
 * the private method's answer depends only on the entity's level and that block position (its whole body, fingerprinted);
 * nothing writes the client level while one entity renders (block, light and chunk changes run between frames, and the
 * light engine's pending updates are applied at the start of LevelRenderer.renderLevel, before any entity renders). The
 * memo lives for one call (a generation number per call), so nothing is carried from one frame or entity to the next.
 *
 * -Dbons_and_furious.cataclysmTentacleLightMemo=false asks the private method every time. .shadow=true asks it every time,
 * returns its answer and counts any kept answer that differs.
 */
public final class TentacleLight {
    public static final String KEY = "cataclysm_tentacle_light_memo";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmTentacleLightMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cataclysmTentacleLightMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    private static final int SIZE = 512, MASK = SIZE - 1, LIMIT = 300;
    private static final long[] KEYS = new long[SIZE];
    private static final int[] VALUES = new int[SIZE], GENS = new int[SIZE];
    private static int generation, used;
    /**
     * The entity of the current render() call. Since 1.0.49.2 held weakly: a strong static kept the last tentacle entity drawn,
     * and with it its whole ClientLevel, after the player left the world (the next world only let go once it drew one).
     */
    private static java.lang.ref.WeakReference<Entity> owner = new java.lang.ref.WeakReference<>(null);
    private static Thread thread;
    private static volatile boolean announced;

    private TentacleLight() {
    }

    /**
     * The wrapped getLightColor(entity, position) call of a render() call; `call` is that render() call's shared int
     * (0 at its first light lookup).
     */
    public static int light(Entity head, Vec3 position, Operation<Integer> original, Object renderer, LocalIntRef call) {
        if (!enabled) return original.call(renderer, head, position);
        Thread t = Thread.currentThread();
        if (thread == null) thread = t;
        if (t != thread) return original.call(renderer, head, position);         // only the render thread keeps answers
        if (call.get() == 0) {                                                   // the first lookup of this render() call
            if (++generation == 0) {
                java.util.Arrays.fill(GENS, 0);
                generation = 1;
            }
            call.set(generation);
            used = 0;
            if (owner.get() != head) owner = new java.lang.ref.WeakReference<>(head);   // one small object per drawn tentacle entity
        }
        if (call.get() != generation || head != owner.get()) return original.call(renderer, head, position);
        long key = BlockPos.m_121882_(Mth.m_14107_(position.f_82479_), Mth.m_14107_(position.f_82480_), Mth.m_14107_(position.f_82481_));
        int slot = (int) (key ^ key >>> 29 ^ key >>> 47) * 0x9E3779B9 >>> 23 & MASK;
        while (GENS[slot] == generation) {
            if (KEYS[slot] == key) {
                if (SHADOW) return shadow(head, position, original, renderer, VALUES[slot]);
                return VALUES[slot];
            }
            slot = slot + 1 & MASK;
        }
        int value = original.call(renderer, head, position);
        if (used < LIMIT) {
            used++;
            GENS[slot] = generation;
            KEYS[slot] = key;
            VALUES[slot] = value;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (Cataclysm's tongue and tentacle segments share the light of a block within one draw){}",
                        KEY, SHADOW ? " - shadow verification on" : "");
            }
        }
        return value;
    }

    private static int shadow(Entity head, Vec3 position, Operation<Integer> original, Object renderer, int kept) {
        int asked = original.call(renderer, head, position);
        SHADOW_CHECKS.incrementAndGet();
        if (asked != kept && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} shadow mismatch at {}: kept light {}, Cataclysm answers {}", KEY, position, kept, asked);
        return asked;
    }
}
