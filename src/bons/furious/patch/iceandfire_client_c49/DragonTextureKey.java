package bons.furious.patch.iceandfire_client_c49;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch iceandfire_dragon_texture_key (Ice and Fire 2.1.13-1.20.1-beta-5, client, since 1.0.49). Idea:
 * Collections Of Optimizations (iceandfire.cacheDragonTexture), idea text only. Our own logic; no Ice and Fire code
 * (LGPL-3.0 target, wrapper only).
 *
 * RenderDragonBase.getTextureLocation runs for every dragon every frame (and for every layer that asks): it reads seven
 * values (variant name, stage, model dead, male, skeletal, sleeping, blinking), joins them into a new string and looks the
 * string up in the renderer's own texture map; the first time a string is seen it builds the layered texture and puts it
 * into the map. Nothing else writes or clears that map, so once a string has a texture, every later call with the same
 * seven values returns the same ResourceLocation object.
 *
 * What changes: the same seven reads are made, in the same order (isModelDead's client-side field write included), and
 * then the answer comes from a small per-renderer table keyed by the variant name and the six other values; a table miss
 * runs the original method (which makes the reads again and does everything stock does) and keeps its answer. Why it is
 * identical: the string is a function of the seven values, so the table hands back exactly the object the renderer's map
 * holds for that string. Values the table cannot hold (no variant name, a stage outside 0-7) run the original.
 *
 * -Dbons_and_furious.iceandfireDragonTextureKey=false runs the original. Shadow mode (.shadow=true) runs the original
 * every time and compares it with the table's answer (SHADOW_CHECKS / SHADOW_MISMATCHES; up to 20 WARN lines).
 */
public final class DragonTextureKey {
    public static final String KEY = "iceandfire_dragon_texture_key";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireDragonTextureKey", "true"));
    public static volatile boolean SHADOW = Boolean.getBoolean("bons_and_furious.iceandfireDragonTextureKey.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private DragonTextureKey() {
    }

    /** One renderer's table: variant name -> 256 slots (stage << 5 | five flags). Render thread only, like the stock map. */
    public static final class Memo {
        final HashMap<String, ResourceLocation[]> rows = new HashMap<>();
    }

    public static ResourceLocation texture(Memo memo, EntityDragonBase d, Operation<ResourceLocation> original) {
        if (!enabled) return original.call(d);
        String name = d.getVariantName(d.getVariant());
        int stage = d.getDragonStage();
        boolean dead = d.isModelDead(), male = d.isMale(), skeletal = d.isSkeletal(), sleeping = d.m_5803_(), blinking = d.isBlinking();
        if (name == null || (stage & ~7) != 0) return original.call(d);
        int slot = stage << 5 | (dead ? 16 : 0) | (male ? 8 : 0) | (skeletal ? 4 : 0) | (sleeping ? 2 : 0) | (blinking ? 1 : 0);
        ResourceLocation[] row = memo.rows.get(name);
        if (row == null) memo.rows.put(name, row = new ResourceLocation[256]);
        ResourceLocation kept = row[slot];
        if (kept == null || SHADOW) {
            ResourceLocation r = original.call(d);
            if (kept == null) {
                row[slot] = r;
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: {} applies (Ice and Fire's dragon renderer keeps its texture per variant and state)", KEY);
                }
            } else {
                long n = SHADOW_CHECKS.incrementAndGet();
                if (kept != r && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: {} shadow mismatch #{} of {} ({} stage {} flags {}): kept {}, stock {}", KEY, SHADOW_MISMATCHES.get(), n, name, stage, slot & 31, kept, r);
            }
            return r;
        }
        return kept;
    }
}
