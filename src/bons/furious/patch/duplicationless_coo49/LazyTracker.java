package bons.furious.patch.duplicationless_coo49;

import it.unimi.dsi.fastutil.objects.ObjectList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import me.kall.duplicationless.data.EntityTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch duplicationless_lazy_tracker (Duplicationless 1.20.1-1.2.1; kind change, off by default;
 * helper of LazyTrackerMixin; no Duplicationless code carried).
 *
 * What Duplicationless does. Its entity trackers (EntityTracker on the server, ClientEntityTracker on the client, both an
 * AbstractEntityTracker) keep, per dimension, maps from chunk and section to the ids of the entities there (by type and by
 * filter: living, enemy). Every entity join, leave and chunk or section crossing calls update(), which queues a task (a
 * lambda holding nine values) that the tick handler drains into the maps at the start of the next tick. Other mods read the
 * maps through getEntities / getEntityList / forEach / count (every one of them goes through chunkSections first). When no
 * mod asks (Fluidium, the mod that pulls Duplicationless into most packs, never does: jar census), all of that is work for
 * nothing.
 *
 * What the switch does. Per tracker and dimension, update() queues nothing until something asks about that dimension. The
 * first question (at chunkSections) builds the dimension's maps from every entity in the level's entity sections (hidden
 * sections included), through the tracker's own update() and task drain, with Duplicationless' own cached filter matches;
 * other dimensions' queued tasks are set aside during that build and put back in their order. From then on the dimension is
 * kept as Duplicationless keeps it. reset() (server stop, client logout) forgets the built dimensions. The mode is decided
 * per tracker at its first use (the runtime flag, and whether the level's entity sections can be read).
 * Trade-off (off by default), two differences: (1) in the tick of a dimension's first question the answers already show the
 * entities that joined, left or moved earlier in that tick (the stock tracker shows them from the next tick); (2) the stock
 * tracker drops an entity when its chunk stops being tracked (Forge's leave event) and does not add it back when the chunk
 * is tracked again until it moves; a dimension built here lists every entity still loaded at its first question. Nothing is
 * saved or sent. Runtime flag bons_and_furious.duplicationlessLazyTracker.
 */
public final class LazyTracker {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.duplicationlessLazyTracker", "true"));
    public static final int UNDECIDED = 0, LAZY = 1, STOCK = 2;
    /** Statistics for probes (plain fields, owner threads). */
    public static long skipped, builds, builtEntities;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private LazyTracker() {
    }

    /** What LazyTrackerMixin adds to Duplicationless' AbstractEntityTracker. */
    public interface Host {
        int bons$duplicationlessMode();

        void bons$duplicationlessSetMode(int mode);

        Set<ResourceLocation> bons$duplicationlessBuilt();

        /** The tracker's own update(entity, level, true, filters) (queues the add task). */
        void bons$duplicationlessAdd(Entity entity, Level level, ObjectList<ResourceLocation> filters);

        ConcurrentLinkedQueue<Runnable> bons$duplicationlessTasks();

        void bons$duplicationlessDrain();
    }

    static int mode(Host t, Level level) {
        int m = t.bons$duplicationlessMode();
        if (m == UNDECIDED) {
            m = enabled && Storage.readable(level) ? LAZY : STOCK;
            t.bons$duplicationlessSetMode(m);
        }
        return m;
    }

    /** At the head of AbstractEntityTracker.update: true = queue nothing (the dimension has not been asked about yet). */
    public static boolean skipUpdate(Host t, Level level) {
        if (level == null || mode(t, level) != LAZY) return false;
        if (t.bons$duplicationlessBuilt().contains(level.m_46472_().m_135782_())) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: duplicationless_lazy_tracker: Duplicationless keeps its entity maps only for dimensions a mod asks about (built at the first question)");
        }
        return true;
    }

    /** At the head of AbstractEntityTracker.chunkSections (every question passes there): build the dimension once. */
    public static void beforeQuestion(Host t, Level level) {
        if (level == null || mode(t, level) != LAZY) return;
        ResourceLocation dim = level.m_46472_().m_135782_();
        if (!t.bons$duplicationlessBuilt().add(dim)) return;
        build(t, level, dim);
    }

    /** At the return of AbstractEntityTracker.reset: forget the built dimensions and the mode. */
    public static void reset(Host t) {
        t.bons$duplicationlessBuilt().clear();
        t.bons$duplicationlessSetMode(UNDECIDED);
    }

    static void build(Host t, Level level, ResourceLocation dim) {
        List<Entity> all;
        try {
            all = Storage.entities(level);
        } catch (Throwable e) {
            LOGGER.error("Bons and Furious: duplicationless_lazy_tracker could not read the entities of {} ({}); its entity map lists only what changes from now on", dim, e.toString());
            return;
        }
        ConcurrentLinkedQueue<Runnable> queue = t.bons$duplicationlessTasks();
        List<Runnable> pending = new ArrayList<>();
        for (Runnable r; (r = queue.poll()) != null; ) pending.add(r);   // other dimensions' tasks: drained at the next tick as before
        try {
            boolean server = level instanceof ServerLevel;
            for (Entity e : all) t.bons$duplicationlessAdd(e, level, server ? EntityTracker.Filterable.getMatched(e) : ClientMatches.of(e));
            t.bons$duplicationlessDrain();
        } finally {
            queue.addAll(pending);
        }
        builds++;
        builtEntities += all.size();
        LOGGER.info("Bons and Furious: duplicationless_lazy_tracker: built the entity map of {} from {} entities at its first question", dim, all.size());
    }

    /** The client tracker's filter matches (its own interface), loaded only on the client path. */
    static final class ClientMatches {
        static ObjectList<ResourceLocation> of(Entity e) {
            return me.kall.duplicationless.data.ClientEntityTracker.ClientFilterable.getMatched(e);
        }
    }

    /** Every entity in a level's entity sections (server: PersistentEntitySectionManager; client: TransientEntitySectionManager). */
    static final class Storage {
        static final Field SERVER_MANAGER, SERVER_STORAGE, SECTIONS;

        static {
            Field a = null, b = null, c = null;
            try {
                a = field(ServerLevel.class, "f_143244_");
                b = field(Class.forName("net.minecraft.world.level.entity.PersistentEntitySectionManager"), "f_157495_");
                c = field(Class.forName("net.minecraft.world.level.entity.EntitySectionStorage"), "f_156852_");
            } catch (Throwable e) {
                LOGGER.warn("Bons and Furious: duplicationless_lazy_tracker cannot reach the entity sections ({}); Duplicationless keeps its maps as shipped", e.toString());
            }
            SERVER_MANAGER = a;
            SERVER_STORAGE = b;
            SECTIONS = c;
        }

        static Field field(Class<?> c, String name) throws NoSuchFieldException {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }

        /** True when this level's entity sections can be read (checked once per tracker, before anything is skipped). */
        static boolean readable(Level level) {
            try {
                return SECTIONS != null && sections(level) != null;
            } catch (Throwable e) {
                LOGGER.warn("Bons and Furious: duplicationless_lazy_tracker cannot read the entity sections of {} ({}); Duplicationless keeps its maps as shipped", level, e.toString());
                return false;
            }
        }

        static Map<?, ?> sections(Level level) throws Exception {
            Object storage = level instanceof ServerLevel ? SERVER_STORAGE.get(SERVER_MANAGER.get(level)) : ClientStorage.of(level);
            return (Map<?, ?>) SECTIONS.get(storage);
        }

        static List<Entity> entities(Level level) throws Exception {
            List<Entity> out = new ArrayList<>();
            for (Object s : sections(level).values()) ((EntitySection<?>) s).m_156845_().forEach(e -> out.add((Entity) e));
            return out;
        }
    }

    /** The client level's section storage (by reflection: no client class is named outside this holder). */
    static final class ClientStorage {
        static final Field LEVEL_STORAGE, MANAGER_STORAGE;

        static {
            Field a = null, b = null;
            try {
                a = Storage.field(Class.forName("net.minecraft.client.multiplayer.ClientLevel"), "f_171631_");
                b = Storage.field(Class.forName("net.minecraft.world.level.entity.TransientEntitySectionManager"), "f_157638_");
            } catch (Throwable e) {
                LOGGER.warn("Bons and Furious: duplicationless_lazy_tracker cannot reach the client's entity sections ({})", e.toString());
            }
            LEVEL_STORAGE = a;
            MANAGER_STORAGE = b;
        }

        static Object of(Level level) throws Exception {
            return MANAGER_STORAGE.get(LEVEL_STORAGE.get(level));
        }
    }
}
