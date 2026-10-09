package bons.furious.patch.datapack_selectors;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_selector_tag_index (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the integrated
 * server; the tag sets themselves exist on both sides). Idea: Bellows ("entities are cached based on their type ... the
 * smallest cache"), idea text only; the tag form, the tracking set and the proof are ours.
 *
 * What vanilla does. A whole-level selector such as `@e[tag=ancient_golem]` (no distance or box) walks every loaded entity
 * of every dimension: the type test, then the predicate chain Entity::isAlive && one predicate per option, in option
 * order; the tag option's predicate is entity.getTags().contains(tag). Data pack tick functions run such scans every
 * tick (Forgotten Ruins 15, Moonlit Monoliths 12 in this pack), each one a walk over all entities.
 *
 * What the switch does.
 *  - Entity's constructor gets a TrackedTags (a HashSet that reports its changes) instead of a plain HashSet
 *    (EntityTagsMixin; only when the constructor's Sets.newHashSet() returned a plain empty HashSet).
 *  - The parser records a positive, non-empty tag= option when every predicate added before it came from a type= or tag=
 *    option (EntitySelectorParserTagMixin, EntitySelectorTagOptionMixin; the type option's wrapper marks its predicates).
 *  - addEntities' whole-level getEntities call gets its type test wrapped in a TagScanTest carrying those tags
 *    (EntitySelectorTagScanMixin, the innermost wrapper of that call).
 *  - EntityLookup.getEntities answers a TagScanTest from the level's TypeIndex (TypeIndex.forEachTagged): the entities
 *    holding the tag (a list per queried tag, kept in step through TrackedTags), merged in byId order with the entities
 *    that are always visited (classes with their own isAlive/getHealth/getType/getTags, or whose tag set is not a bound
 *    TrackedTags), the inner type test's tryCast asked for each, then the full vanilla chain. When the inner test is a type
 *    list (vanilla_selector_type_index / vanilla_selector_single_type) and that list is smaller, the type list is walked.
 *
 * Why the result is identical. Vanilla's chain for such a selector is isAlive && (type/tag predicates) && tag(T) && rest,
 * evaluated left to right with short circuits. An entity the walk skips is one of a plain class (vanilla isAlive,
 * getHealth, getType, getTags) whose tracked set does not hold T: vanilla runs its type test, isAlive and the pure
 * type/tag predicates before tag(T), all without side effects, and tag(T) rejects it. Entities that do reach tag(T) are
 * visited in byId order, with the same tryCast and the same chain, so the selected entities, their order and the limit's
 * cut-off are vanilla's. Assumption shared with vanilla's own iteration: the chain does not add or remove entities of
 * the scanned level or change entity tags.
 *
 * -Dbons_and_furious.selectorTagIndex=false switches it off at run time (no new tracked sets, no tag scans).
 * -Dbons_and_furious.selectorTagIndex.shadow=true (verification runs only): before every tag walk, the walk's entities are
 * compared, by identity and order, with an independent recount over byId (always-visited class or untracked set, or the
 * tag held), and the tag list with the bound sets holding the tag (SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 logged).
 */
public final class TagIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.selectorTagIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.selectorTagIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Scans answered from a tag list, and scans where a type list was smaller (counters, both always kept). */
    public static final AtomicLong TAG_WALKS = new AtomicLong(), TYPE_WALKS = new AtomicLong();
    private static volatile boolean announced;

    static {
        // TrackedTags sees every change through HashSet's add/remove/clear/iterator; the bulk operations must be the
        // inherited ones that call those. A JDK whose HashSet declares one itself would bypass the reports: stand down.
        for (String name : new String[]{"removeIf", "removeAll", "retainAll", "addAll"}) {
            for (java.lang.reflect.Method m : HashSet.class.getDeclaredMethods()) {
                if (m.getName().equals(name) && !m.isSynthetic()) {
                    enabled = false;
                    LOGGER.warn("Bons and Furious: vanilla_selector_tag_index stands down: this JDK's HashSet declares {} itself", name);
                }
            }
        }
    }

    /** Classes with their own getTags: their entities are always visited by a tag walk. */
    private static final ClassValue<Boolean> OWN_TAGS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("m_19880_").getDeclaringClass() != Entity.class;
            } catch (Throwable t) {
                return true;   // as SelectorPrefilter.OWN_CHECKS: a class that cannot be inspected is always visited
            }
        }
    };

    private TagIndex() {
    }

    static boolean ownTags(Class<?> entityClass) {
        return OWN_TAGS.get(entityClass);
    }

    /** Entity's constructor (EntityTagsMixin): a TrackedTags in place of the plain empty HashSet vanilla just made. */
    public static HashSet<?> trackedSet(Object entity, HashSet<?> original) {
        if (enabled && original != null && original.getClass() == HashSet.class && original.isEmpty()) return new TrackedTags(entity);
        return original;
    }

    /** EntitySelectorParser.getSelector: the selector keeps the tags its parse found usable (none: unchanged). */
    public static EntitySelector attach(EntitySelector selector, List<String> tags) {
        if (tags != null && !tags.isEmpty() && (Object) selector instanceof TagScanSelector holder) {
            holder.bons$setScanTags(tags.toArray(new String[0]));
        }
        return selector;
    }

    /** EntitySelector.addEntities' whole-level getEntities call (innermost wrapper): the type test gets the tags. */
    public static void scan(TagScanSelector holder, ServerLevel level, EntityTypeTest<?, ?> test, Predicate<?> predicate, List<?> out, int limit,
                            Operation<Void> original) {
        String[] tags = holder.bons$scanTags();
        if (tags == null || !enabled) {
            original.call(level, test, predicate, out, SelectorPrefilter.boxed(limit));
            return;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_selector_tag_index applies (entity selectors with a tag= visit only the entities holding that tag){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        TagScanTest t = holder.bons$tagTest();
        if (t == null || t.inner != test) {
            t = new TagScanTest(test, tags);
            holder.bons$setTagTest(t);
        }
        original.call(level, t, predicate, out, SelectorPrefilter.boxed(limit));
    }

    static void mismatch(String what) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_selector_tag_index shadow mismatch #{}: {}", m, what);
    }

    /**
     * The scan's type test for a selector with usable tags: the inner test (the selector's own, or the TypeFirstOption /
     * SingleTypeTest the other keys made) plus the tags. tryCast and baseClass are the inner test's, so a lookup that
     * does not know it gets exactly the inner test's answers.
     */
    public static final class TagScanTest implements EntityTypeTest<Entity, Entity> {
        final EntityTypeTest<?, ?> inner;
        final String[] tags;

        TagScanTest(EntityTypeTest<?, ?> inner, String[] tags) {
            this.inner = inner;
            this.tags = tags;
        }

        public EntityTypeTest<?, ?> inner() {
            return this.inner;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Entity m_141992_(Entity entity) {
            return (Entity) ((EntityTypeTest) this.inner).m_141992_(entity);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class<? extends Entity> m_142225_() {
            return ((EntityTypeTest) this.inner).m_142225_();
        }
    }
}
