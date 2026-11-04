package bons.furious.patch.fancymenu_files_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.keksuccino.fancymenu.util.file.LocalSourcePathResolver;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fancymenu_local_source_memo (FancyMenu 3.9.14; client side; kind change, off by default). Ours;
 * none of FancyMenu's code is carried.
 *
 * What FancyMenu does. LocalSourcePathResolver.resolve(rawPath) checks a layout's local file path lexically (no parent
 * traversal, inside the game or .minecraft folder) and then, through ConfinedPathResolver, walks the disk twice
 * (projectRealPath of the root and of the path: attribute reads up the path and toRealPath) to make sure no link leads
 * outside the allowed folder. Every layout construction resolves each of its local sources again (images, texts, JSON
 * and video placeholders, a local JSON placeholder on every evaluation), and every reader checks the path again
 * (revalidate) right before it reads.
 *
 * What the switch does (trade-off). Each resolver keeps the result of every successful resolve per raw path (at most 4,096;
 * a failed resolve is never kept and runs again next time) until FancyMenu reloads (its reload drops every kept result at
 * its start). The kept result is the same lexical path; the readers still check it right before reading, so nothing is
 * read through a link that leads outside the folder. What the player can notice: if a layout's local file or folder is
 * replaced during the session by a link pointing outside the game folder, the menu keeps treating that source as found
 * (and an image already loaded from it may keep showing) until FancyMenu reloads, where FancyMenu would drop it at the
 * next menu opening.
 *
 * -Dbons_and_furious.fancymenuLocalSourceMemo=false switches it off at run time.
 */
public final class LocalSourceMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fancymenuLocalSourceMemo", "true"));
    static final int CAP = 4096;
    /** Bumped at the start of every FancyMenu reload: results kept under an older epoch are resolved again. */
    private static volatile long epoch;
    public static volatile long hits, misses;
    private static volatile boolean announced;

    public record Kept(long epoch, LocalSourcePathResolver.ResolvedPath path) {}

    private LocalSourceMemo() {}

    /** In place of LocalSourcePathResolver.resolve(rawPath), with the resolver's own memo. */
    public static LocalSourcePathResolver.ResolvedPath resolve(ConcurrentHashMap<String, Kept> memo, String rawPath,
                                                               Operation<LocalSourcePathResolver.ResolvedPath> original) {
        if (!enabled || rawPath == null || memo == null) return original.call(rawPath);
        long now = epoch;
        Kept kept = memo.get(rawPath);
        if (kept != null && kept.epoch() == now) {
            hits++;
            return kept.path();
        }
        misses++;
        LocalSourcePathResolver.ResolvedPath resolved = original.call(rawPath);   // exceptions are thrown, never kept
        if (resolved != null && (kept != null || memo.size() < CAP)) memo.put(rawPath, new Kept(now, resolved));
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fancymenu_local_source_memo applies (FancyMenu's local sources are resolved once until FancyMenu reloads)");
        }
        return resolved;
    }

    /** At the start of ScreenCustomization.reloadFancyMenu. */
    public static void reloaded() {
        epoch++;
    }
}
