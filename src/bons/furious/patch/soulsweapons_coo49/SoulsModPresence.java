package bons.furious.patch.soulsweapons_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch soulsweapons_mod_presence_memo (Soulslike Weaponry 1.4.10, same bytes in 1.4.8; CC0-1.0; both
 * sides; since 1.0.49). Ours; no Soulslike Weaponry code is carried. Idea: Collections Of Optimizations
 * (soulsweapons.cacheModPresence), idea text only.
 *
 * WeaponUtil.isModLoaded(id) is FMLLoader.getLoadingModList().getModFileById(id) != null, and isFightModLoaded() is
 * isModLoaded("bettercombat") || isModLoaded("epicfight"); Soulslike's Player.getItemBySlot HEAD hook asks the latter on
 * every equipment lookup of every player (both sides), its item predicates and charge-time code ask the former. Forge's
 * LoadingModList builds its file-by-id map once in its constructor (a private final field no method writes again), and
 * FMLLoader keeps one list for the whole session, so for one list object and one id string object the answer never
 * changes.
 *
 * isModLoaded keeps (id object, list object, answer) in a small direct-mapped table indexed by the id's identity hash, and
 * isFightModLoaded keeps (list object, answer); an entry is used only while FMLLoader.getLoadingModList() returns the same
 * list object, otherwise (and with no list at all, where the original throws its NullPointerException) the original runs
 * and its answer is kept. Entries are immutable objects (final fields), so a racy read sees a whole entry or an older one.
 * Runtime flag -Dbons_and_furious.soulsweaponsModPresenceMemo=false.
 */
public final class SoulsModPresence {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.soulsweaponsModPresenceMemo", "true"));
    private static final int SLOTS = 32;
    private static final Answer[] TABLE = new Answer[SLOTS];
    private static Answer fight;
    private static volatile boolean announced;

    private static final class Answer {
        final String id;
        final LoadingModList list;
        final boolean loaded;

        Answer(String id, LoadingModList list, boolean loaded) {
            this.id = id;
            this.list = list;
            this.loaded = loaded;
        }
    }

    private SoulsModPresence() {
    }

    /** In place of WeaponUtil.isModLoaded(id). */
    public static boolean isModLoaded(String id, Operation<Boolean> original) {
        if (enabled && id != null) {
            LoadingModList list = FMLLoader.getLoadingModList();
            if (list != null) {
                int slot = System.identityHashCode(id) & (SLOTS - 1);
                Answer a = TABLE[slot];
                if (a != null && a.id == id && a.list == list) return a.loaded;
                boolean loaded = original.call(id);
                TABLE[slot] = new Answer(id, list, loaded);
                if (!announced) announce();
                return loaded;
            }
        }
        return original.call(id);
    }

    /** In place of WeaponUtil.isFightModLoaded(). */
    public static boolean isFightModLoaded(Operation<Boolean> original) {
        if (enabled) {
            LoadingModList list = FMLLoader.getLoadingModList();
            if (list != null) {
                Answer f = fight;
                if (f != null && f.list == list) return f.loaded;
                boolean loaded = original.call();
                fight = new Answer(null, list, loaded);
                return loaded;
            }
        }
        return original.call();
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: soulsweapons_mod_presence_memo applies (Soulslike Weaponry's installed-mod checks are answered once per mod list)");
    }
}
