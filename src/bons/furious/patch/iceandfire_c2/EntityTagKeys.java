package bons.furious.patch.iceandfire_c2;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.tags.ITagManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch iceandfire_entity_tag_keys (Ice and Fire 2.1.13-1.20.1-beta-5, LGPL; both sides, used on the
 * server tick).
 *
 * Ice and Fire asks "is this entity type in tag X?" with ForgeRegistries.ENTITY_TYPES.tags().createTagKey(location)
 * followed by EntityType.is(key): ServerEvents.isInEntityTag (behind isChicken, which ChickenData calls for every living
 * entity on every server tick, and isLivestock / isVillager / isSheep / ...), DragonUtils.isVillager and
 * DragonUtils.isDragonTargetable. createTagKey builds a new TagKey and interns it through Guava's weak interner on every
 * call, although the locations are Ice and Fire's static constants.
 *
 * {@link #key} hands back the TagKey that createTagKey made the first time for that location and that tag manager. A
 * TagKey is a value (registry key + location; createTagKey depends on nothing else), the interner returns that same
 * canonical instance for every equal key while it is held, and EntityType.is looks the key up by equality in the type's
 * current tags, so every answer, before and after /reload or a datapack change, is the one a fresh key gives; tag
 * membership itself is never cached. A null location, a null manager or anything new goes through createTagKey exactly
 * as before (same exceptions). The table holds at most 64 locations. No Ice and Fire code is carried here; its methods
 * are unchanged except for where their key comes from.
 */
public final class EntityTagKeys {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch. -Dbons_and_furious.iceandfireEntityTagKeys=false makes every call create its key again. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.iceandfireEntityTagKeys", "true"));
    private static final int LIMIT = 64;
    /** Append-only, copy-on-write: [manager, location, key] triples. */
    private static volatile Object[] table = new Object[0];
    private static volatile boolean announced;

    private EntityTagKeys() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static TagKey key(ITagManager tags, ResourceLocation location) {
        if (!enabled || tags == null || location == null) return tags.createTagKey(location);
        Object[] t = table;
        for (int i = 0; i < t.length; i += 3) {
            if (t[i + 1] == location && t[i] == tags) return (TagKey) t[i + 2];
        }
        for (int i = 0; i < t.length; i += 3) {
            if (t[i] == tags && location.equals(t[i + 1])) return (TagKey) t[i + 2];
        }
        TagKey key = tags.createTagKey(location);
        remember(tags, location, key);
        return key;
    }

    private static synchronized void remember(Object tags, ResourceLocation location, TagKey<?> key) {
        Object[] t = table;
        if (t.length >= 3 * LIMIT) return;
        for (int i = 0; i < t.length; i += 3) if (t[i] == tags && location.equals(t[i + 1])) return;
        Object[] n = java.util.Arrays.copyOf(t, t.length + 3);
        n[t.length] = tags;
        n[t.length + 1] = location;
        n[t.length + 2] = key;
        table = n;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: iceandfire_entity_tag_keys: Ice and Fire's entity tag checks reuse their tag keys");
        }
    }
}
