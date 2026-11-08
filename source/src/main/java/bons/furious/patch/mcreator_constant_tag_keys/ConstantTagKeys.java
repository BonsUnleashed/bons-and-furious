package bons.furious.patch.mcreator_constant_tag_keys;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, layer mcreator_constant_tag_keys (both sides): constant tag keys for MCreator procedures, the
 * 1.0.19 goblins_cached_disguise_tag pattern made shared. No Minecraft or mod code is carried.
 *
 * What the mods do. MCreator writes a tag test as {@code is(TagKey.create(Registries.X, ResourceLocation.parse("ns:path")))}
 * (or ItemTags.create / BlockTags.create of such a location), so every call builds a location (parse and validation), a new
 * TagKey record, and looks that key up in TagKey's global interner, which hands back the one canonical key, the same object
 * on every call. Critters n' Crawlers does it 216 times per sasquatch per tick (SasquatchWoodKnockProcedure's 6 x 6 x 6 log
 * search).
 *
 * What the layer does. A caller mixin redirects the two calls of one such expression: the location factory to
 * {@link Caller#location} (the location of that id string, parsed once with the same factory and kept) and the tag key
 * factory to {@link Caller#tag} / {@link Caller#itemTag} / {@link Caller#blockTag} (the key the factory returned the first
 * time for that registry and location, kept). The caller's proof shows that the location flows only into that factory call.
 *
 * Why the result is the same. TagKey.create interns (TagKey.VALUES, a weak interner): it returns the canonical key equal
 * to (registry, location), and while this layer holds that key strongly it stays the canonical one, so the original call would
 * return exactly the object kept here (identity, not only equality). The location of a constant id is a pure function of the
 * string; it is consumed by the factory call only, so the original's fresh location object is never seen by anything. What
 * no longer runs per call: the parse, the location and key allocations, the interner lookup. Exact.
 *
 * Registration (a caller in its own group): one Caller per switch, {@code ConstantTagKeys.caller(key, camelName, what)};
 * its runtime flag -Dbons_and_furious.<camelName>=false makes both handlers call the original factories.
 * Memory: one location and one key per distinct (registry, id) the callers name (a handful).
 */
public final class ConstantTagKeys {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Id string -> its location (parsed once). */
    private static final ConcurrentHashMap<String, ResourceLocation> IDS = new ConcurrentHashMap<>();
    /** Registry key -> location -> the canonical tag key. */
    private static final ConcurrentHashMap<ResourceKey<?>, ConcurrentHashMap<ResourceLocation, TagKey<?>>> TAGS = new ConcurrentHashMap<>();
    /** Ids and keys made (statistics for the harness). */
    public static long idsMade, keysMade;

    /** One caller switch: its key, its runtime flag, its one INFO line. */
    public static final class Caller {
        public final String key;
        public final String flag;
        public volatile boolean enabled;
        private volatile boolean announced;
        private final String what;

        Caller(String key, String camelName, String what) {
            this.key = key;
            this.flag = "bons_and_furious." + camelName;
            this.enabled = !"false".equalsIgnoreCase(System.getProperty(this.flag, "true"));
            this.what = what;
        }

        /** In place of {@code ResourceLocation.parse(id)} whose result goes straight into a tag key factory call. */
        public ResourceLocation location(String id) {
            if (!this.enabled) return ResourceLocation.parse(id);
            ResourceLocation rl = IDS.get(id);
            if (rl == null) {
                rl = ResourceLocation.parse(id);   // a bad id throws here exactly as the original does, and nothing is kept
                ResourceLocation prev = IDS.putIfAbsent(id, rl);
                if (prev != null) rl = prev;
                else idsMade++;
            }
            return rl;
        }

        /** In place of {@code TagKey.create(registry, id)}. */
        public <T> TagKey<T> tag(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
            if (!this.enabled) return TagKey.create(registry, id);
            return kept(registry, id, this);
        }

        /** In place of NeoForge's {@code ItemTags.create(id)} (= TagKey.create(Registries.ITEM, id)). */
        public TagKey<Item> itemTag(ResourceLocation id) {
            if (!this.enabled) return ItemTags.create(id);
            return kept(Registries.ITEM, id, this);
        }

        /** In place of NeoForge's {@code BlockTags.create(id)} (= TagKey.create(Registries.BLOCK, id)). */
        public TagKey<Block> blockTag(ResourceLocation id) {
            if (!this.enabled) return BlockTags.create(id);
            return kept(Registries.BLOCK, id, this);
        }

        private void announce() {
            this.announced = true;
            LOGGER.info("Bons and Furious: {} applies ({})", this.key, this.what);
        }
    }

    private ConstantTagKeys() {
    }

    /** A caller switch of this layer (call once, from the caller group's own helper). */
    public static Caller caller(String key, String camelName, String what) {
        return new Caller(key, camelName, what);
    }

    @SuppressWarnings("unchecked")
    static <T> TagKey<T> kept(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Caller caller) {
        ConcurrentHashMap<ResourceLocation, TagKey<?>> m = TAGS.get(registry);
        if (m == null) m = TAGS.computeIfAbsent(registry, k -> new ConcurrentHashMap<>());
        TagKey<?> t = m.get(id);
        if (t == null) {
            t = TagKey.create(registry, id);   // the canonical key; kept strongly from now on, so it stays canonical
            TagKey<?> prev = m.putIfAbsent(id, t);
            if (prev != null) t = prev;
            else keysMade++;
            if (!caller.announced) caller.announce();
        }
        return (TagKey<T>) t;
    }
}
