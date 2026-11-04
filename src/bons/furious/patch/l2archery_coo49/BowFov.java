package bons.furious.patch.l2archery_coo49;

import dev.xkmc.l2archery.content.config.BowArrowStatConfig;
import dev.xkmc.l2archery.content.feature.core.PotionArrowFeature;
import dev.xkmc.l2archery.content.item.BowData;
import dev.xkmc.l2archery.content.item.GenericBowItem;
import dev.xkmc.l2archery.content.item.IBowConfig;
import dev.xkmc.l2archery.content.upgrade.Upgrade;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2archery_fov_config_memo (L2 Archery 2.5.0, client; exact). L2 Archery is LGPL-2.1; none of its
 * code is carried. Ours, idea from Collections Of Optimizations (l2archery.skipIdleBowFov), idea text only.
 *
 * What L2 Archery does: GenericEventHandler.fov runs on every frame while the player holds an L2 bow in the main hand
 * (Forge's ComputeFovModifierEvent), drawn or not. It builds BowData.of(bow, stack) (the upgrade list read from the stack,
 * a copy of the stack's enchantment map) and asks data.getConfig() twice, once for fov_time() and once for fov(); each
 * getConfig() wraps the bow's own config in one CompoundBowConfig per stat upgrade and stat enchantment, and every value
 * read goes through the datapack config (BowArrowStatConfig.get(): L2 Library's merged config of the "stats" type).
 *
 * What the switch does: BowData.of still runs every frame (a @Redirect that makes the shipped call). When its BowData holds
 * the same content as the kept one (the same bow item, the same upgrade objects in the same order, the same enchantments
 * with the same levels in the same iteration order) and the merged config is the same object (L2 Library builds a new one
 * after every reload or sync), the handler gets the kept BowData, and its two getConfig() calls (a @Redirect) get a fixed
 * config whose fov_time() and fov() are the numbers read from that content once; L2 Archery's own float arithmetic then runs
 * unchanged on them (the same bits, the NaN of a zero fov_time included). Otherwise the numbers are read from the new
 * BowData (one getConfig() instead of two) and kept. Why the numbers are the same: getConfig() reads only the BowData's
 * three parts and the merged config; the bow's config is a final field, every StatFeature is an immutable record, an
 * upgrade's feature is made once (potion upgrades give no StatFeature), a bow enchantment's features are made in its
 * constructor, and the wrappers are chained in the BowData's own order, which the comparison checks. Nothing about the
 * stack itself is kept, so a stack whose other data changes every tick (energy, damage) still hits.
 *
 * Shadow mode (-Dbons_and_furious.l2archeryFovConfigMemo.shadow=true): on every frame that would use the kept numbers the
 * shipped getConfig() runs as well and its fov_time() and fov() bits are compared with the kept ones (SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20); the shipped values are used. Flag: -Dbons_and_furious.l2archeryFovConfigMemo=false
 * runs the shipped code every frame. HITS counts frames that used the kept numbers only with
 * -Dbons_and_furious.l2archeryFovConfigMemo.count=true (the harness; nothing is counted in game).
 */
public final class BowFov {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2archeryFovConfigMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.l2archeryFovConfigMemo.shadow");
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.l2archeryFovConfigMemo.count");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Frames answered from the kept numbers (counted only with the count property: for the harness). */
    public static final AtomicLong HITS = new AtomicLong();
    private static volatile Kept kept;
    private static volatile boolean announced;

    /** The two numbers read from one BowData under one merged config (immutable), handed to the handler as its config:
     *  fov_time() and fov() are the kept numbers, anything else asks the config they were read from. */
    static final class Kept implements IBowConfig {
        final BowData data;
        final Object config;
        final IBowConfig from;
        final int fovTime;
        final float fov;

        Kept(BowData data, Object config, IBowConfig from) {
            this.data = data;
            this.config = config;
            this.from = from;
            this.fovTime = from.fov_time();
            this.fov = from.fov();
        }

        @Override
        public int fov_time() {
            return fovTime;
        }

        @Override
        public float fov() {
            return fov;
        }

        @Override
        public float speed() {
            return from.speed();
        }

        @Override
        public int pull_time() {
            return from.pull_time();
        }

        @Override
        public PotionArrowFeature getEffects() {
            return from.getEffects();
        }

        @Override
        public float damage() {
            return from.damage();
        }

        @Override
        public int punch() {
            return from.punch();
        }

        @Override
        public void addStatTooltip(List<Component> list) {
            from.addStatTooltip(list);
        }
    }

    private BowFov() {
    }

    /** The same bow item, upgrade objects in order, and enchantments with levels in iteration order (getConfig chains the
     *  wrappers in that order). */
    static boolean same(BowData a, BowData b) {
        if (a.item() != b.item()) return false;
        ArrayList<Upgrade> ua = a.upgrade(), ub = b.upgrade();
        int n = ua.size();
        if (n != ub.size()) return false;
        for (int i = 0; i < n; i++) if (ua.get(i) != ub.get(i)) return false;
        HashMap<Enchantment, Integer> ea = a.ench(), eb = b.ench();
        if (ea.size() != eb.size()) return false;
        if (ea.isEmpty()) return true;
        Iterator<Map.Entry<Enchantment, Integer>> ia = ea.entrySet().iterator(), ib = eb.entrySet().iterator();
        while (ia.hasNext()) {
            Map.Entry<Enchantment, Integer> x = ia.next(), y = ib.next();
            if (x.getKey() != y.getKey() || !Objects.equals(x.getValue(), y.getValue())) return false;
        }
        return true;
    }

    /** The handler's BowData.of(bow, stack) call: the shipped call, then the kept BowData when the content is the same. */
    public static BowData data(GenericBowItem bow, ItemStack stack) {
        BowData d = BowData.of(bow, stack);
        if (!enabled) return d;
        Object config = BowArrowStatConfig.get();
        Kept k = kept;
        if (k != null && k.config == config && same(k.data, d)) {
            if (SHADOW) {
                IBowConfig c = d.getConfig();
                int t = c.fov_time();
                float f = c.fov();
                SHADOW_CHECKS.incrementAndGet();
                if ((t != k.fovTime || Float.floatToRawIntBits(f) != Float.floatToRawIntBits(k.fov)) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                    LOGGER.warn("Bons and Furious: l2archery_fov_config_memo shadow mismatch: kept fov_time {} fov {}, the shipped code {} {}", k.fovTime, k.fov, t, f);
                }
                return d;
            }
            if (COUNT) HITS.incrementAndGet();
            return k.data;
        }
        kept = new Kept(d, config, d.getConfig());
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2archery_fov_config_memo applies (the bow FOV keeps the numbers of the held bow's upgrades and enchantments)");
        }
        return d;
    }

    /** The handler's two data.getConfig() calls. */
    public static IBowConfig config(BowData data) {
        Kept k = kept;
        if (k != null && data == k.data && enabled && !SHADOW) return k;
        return data.getConfig();
    }
}
