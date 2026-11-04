package bons.furious.patch.goety_brew_coo49;

import bons.furious.mixin.goety_brew_coo49.GbNamedContentsAccessor;
import com.Polarice3.Goety.common.effects.brew.BrewEffects;
import com.Polarice3.Goety.config.BrewConfig;
import com.Polarice3.Goety.init.ModTags;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.IdMappingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group goety_brew_coo49 (Goety 2.5.60.0; client). Helper of the group's mixin; no code of Goety
 * (MIT) is carried: the logic is ours.
 *
 * <p>Goety's brewing book pages (Patchouli page processors BrewingCatalystProcessor and BrewingSacrificeProcessor) build a
 * whole new BrewEffects table twice per page: once in setup (to find the page's brew effect) and once when the page asks
 * for its "input" (the catalyst item or the sacrifice creature). Each table registers about a hundred brew effects from
 * Goety's brew config and walks every registered item and entity type, and a book build (resource reload, world join)
 * runs a few hundred of them. The table is a function of: the brew config's values (read at construction), the item and
 * entity type registries' iteration order (changes only with an id remap: IdMappingEvent), and the goety:villagers entity
 * tag (changes only with a tag binding: TagsUpdatedEvent; the tag object's contents list is also checked by identity).
 * goety_brew_effects_page_memo keeps one table and hands it out again at those two `new BrewEffects()` sites while all of
 * that is unchanged (the config compared value by value on every hand-out, the event counters and the tag contents read
 * before the table was built); otherwise a new table is built and kept. The pages only read the table and the objects it
 * returns (getters of BrewEffect, a catalyst ItemStack that Patchouli's IVariable.from serializes at once), so a page
 * cannot tell a kept table from a new one. Exact variant, the one internal difference: the pages share one table (and its
 * BrewEffect / ItemStack objects) instead of each building its own. Goety's own BrewEffects.INSTANCE and every other caller
 * are left alone.
 *
 * <p>Runtime flag: bons_and_furious.goetyBrewEffectsPageMemo (=false: off). SHADOW MODE:
 * -Dbons_and_furious.goetyBrewEffectsPageMemo.shadow=true builds a new table at every kept hand-out, compares it with the
 * kept one (every map, key order, every brew effect's and modifier's fields, the catalyst stacks' item / count / tag, the
 * sacrifice types): SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20, and hands out the new table.
 */
public final class GoetyBrews {
    static final String KEY = "goety_brew_effects_page_memo";
    public static volatile boolean enabled = flag("goetyBrewEffectsPageMemo");
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goetyBrewEffectsPageMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics (plain fields, racy by design): tables handed out again, tables built. */
    public static long keptHits, builds;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Bumped by the listeners (TagsUpdatedEvent, IdMappingEvent). */
    static volatile int tagGeneration, idGeneration;
    private static volatile boolean announced;
    private static volatile Kept kept;

    private record Kept(BrewEffects table, Object[] config, Object villagers, int tagGen, int idGen) {}

    private GoetyBrews() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The two listeners, registered when the memo is first used; READY false = the memo stays off (new tables). */
    private static final class Listeners {
        static final boolean READY = register();

        private static boolean register() {
            try {
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TagsUpdatedEvent.class, e -> tagGeneration++);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, IdMappingEvent.class, e -> idGeneration++);
                return true;
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: {} cannot listen for tag reloads and id remaps ({}); Goety's brewing pages build their tables as before", KEY, t.toString());
                return false;
            }
        }
    }

    /** Every config value of Goety's brew config (found once, by reflection: the table reads them at construction). */
    private static final class Config {
        static final ForgeConfigSpec.ConfigValue<?>[] VALUES = find();

        private static ForgeConfigSpec.ConfigValue<?>[] find() {
            try {
                List<ForgeConfigSpec.ConfigValue<?>> out = new ArrayList<>();
                for (Field f : BrewConfig.class.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) || !ForgeConfigSpec.ConfigValue.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v instanceof ForgeConfigSpec.ConfigValue<?> cv) out.add(cv);
                }
                return out.isEmpty() ? null : out.toArray(new ForgeConfigSpec.ConfigValue<?>[0]);
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: {} cannot read Goety's brew config ({}); Goety's brewing pages build their tables as before", KEY, t.toString());
                return null;
            }
        }
    }

    /** The brew config's current values, or null (unreadable: the caller builds a new table as before). */
    static Object[] config() {
        ForgeConfigSpec.ConfigValue<?>[] values = Config.VALUES;
        if (values == null) return null;
        Object[] out = new Object[values.length];
        try {
            for (int i = 0; i < values.length; i++) out[i] = values[i].get();
        } catch (Throwable t) {
            return null;
        }
        return out;
    }

    /** The goety:villagers entity tag object (MappedRegistry keeps it across reloads), found once. */
    private static volatile HolderSet.Named<EntityType<?>> villagersTag;

    static Object villagersContents() {
        HolderSet.Named<EntityType<?>> named = villagersTag;
        if (named == null) {
            Optional<HolderSet.Named<EntityType<?>>> found = BuiltInRegistries.f_256780_.m_203431_(ModTags.EntityTypes.VILLAGERS);
            if (found.isEmpty()) return null;            // not bound yet: nothing is kept
            villagersTag = named = found.get();
        }
        return ((GbNamedContentsAccessor) (Object) named).bons$goetyBrewNamedContents();
    }

    /**
     * True when every brew config value is the kept one: the same object (ConfigValue hands out its cached value), or an
     * equal one. Compared in place, without a copy (copying all values into a new array on every call cost time even when
     * the memo was then refused).
     */
    private static boolean sameConfig(ForgeConfigSpec.ConfigValue<?>[] values, Object[] keptValues) {
        if (keptValues.length != values.length) return false;
        try {
            for (int i = 0; i < values.length; i++) {
                Object v = values[i].get(), w = keptValues[i];
                if (v != w && (v == null || !v.equals(w))) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Wrapper of `new BrewEffects()` in the two brewing page processors' setup and process. */
    public static BrewEffects table(Operation<BrewEffects> original) {
        if (!enabled || !Listeners.READY) return original.call();
        int tg = tagGeneration, ig = idGeneration;       // read before anything the table is built from
        Object villagers = villagersContents();
        ForgeConfigSpec.ConfigValue<?>[] values = Config.VALUES;
        if (villagers == null || values == null) return original.call();
        Kept k = kept;
        if (k != null && k.villagers == villagers && k.tagGen == tg && k.idGen == ig && sameConfig(values, k.config)) {
            if (SHADOW) return shadow(k.table, original.call());
            keptHits++;
            return k.table;
        }
        Object[] config = config();                      // the values the new table is built from, read before it
        if (config == null) return original.call();
        BrewEffects fresh = original.call();
        builds++;
        kept = new Kept(fresh, config, villagers, tg, ig);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {}: Goety's brewing book pages share one brew table until the brew config changes, tags reload or ids remap{}",
                    KEY, SHADOW ? " (SHADOW MODE: every hand-out is compared with a new table)" : "");
        }
        return fresh;
    }

    private static BrewEffects shadow(BrewEffects kept, BrewEffects fresh) {
        SHADOW_CHECKS.incrementAndGet();
        String diff = difference(kept, fresh);
        if (diff != null && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the kept brew table differs from a new one ({})", KEY, SHADOW_MISMATCHES.get(), diff);
        return fresh;
    }

    // ---- structural comparison (shadow mode and the offline proof) ----

    /** Null when the two tables answer alike: the same maps, keys in the same order, equal values (see the class comment). */
    public static String difference(Object a, Object b) {
        try {
            for (Field f : fields(a.getClass())) {
                String d = compare(f.get(a), f.get(b), f.getName());
                if (d != null) return d;
            }
            return null;
        } catch (Throwable t) {
            return "not comparable: " + t;
        }
    }

    private static List<Field> fields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out;
    }

    private static String compare(Object x, Object y, String where) throws IllegalAccessException {
        if (x == y) return null;
        if (x == null || y == null) return where + ": one is null";
        if (x instanceof Map<?, ?> mx && y instanceof Map<?, ?> my) {
            if (mx.size() != my.size()) return where + ": " + mx.size() + " vs " + my.size() + " entries";
            Iterator<? extends Map.Entry<?, ?>> ix = mx.entrySet().iterator(), iy = my.entrySet().iterator();
            while (ix.hasNext()) {
                Map.Entry<?, ?> ex = ix.next(), ey = iy.next();
                if (!Objects.equals(ex.getKey(), ey.getKey())) return where + ": keys in another order (" + ex.getKey() + " / " + ey.getKey() + ")";
                String d = compare(ex.getValue(), ey.getValue(), where + "[" + ex.getKey() + "]");
                if (d != null) return d;
            }
            return null;
        }
        if (x instanceof ItemStack sx && y instanceof ItemStack sy) {
            return sx.m_41720_() == sy.m_41720_() && sx.m_41613_() == sy.m_41613_() && Objects.equals(sx.m_41783_(), sy.m_41783_()) ? null : where + ": another stack";
        }
        if (x.getClass() != y.getClass()) return where + ": " + x.getClass().getSimpleName() + " vs " + y.getClass().getSimpleName();
        if (x.getClass().getName().startsWith("com.Polarice3.")) {      // a brew effect or modifier: field by field
            for (Field f : fields(x.getClass())) {
                if (f.getName().equals("descriptionId")) continue;      // a lazy cache of effectID (filled on first use)
                String d = compare(f.get(x), f.get(y), where + "." + f.getName());
                if (d != null) return d;
            }
            return null;
        }
        return x.equals(y) ? null : where + ": " + x + " vs " + y;       // strings, numbers; registry objects by identity
    }
}
