package bons.furious.patch.cucumber_coo49;

import java.io.File;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch cucumber_tag_file_failure_memo (fix; since 1.0.49; Cucumber 1.21.1-8.0.16; both sides). Helper of
 * CucumberTagFileMemoMixin; no Cucumber code is carried.
 *
 * The bug. TagMapper.getItemForTag(tag) answers a tag it has not mapped yet by opening config/cucumber-tags.json and
 * parsing the whole file; it only remembers a tag after a successful parse. With a malformed file every unmapped tag of
 * every call re-opens and re-parses the file and logs "An error occurred while reading cucumber-tags.json" with a full
 * stack trace, then answers AIR, changing nothing. The callers resolve recipe outputs lazily (Cucumber's tag recipes, e.g.
 * the client's recipe book setup at every join and every reload), so one broken file means one stack trace per tag recipe,
 * again and again.
 *
 * The fix. When such a read fails, the file's state BEFORE that read (exists, is a file, last-modified time, length) is
 * remembered; a later unmapped tag whose file still has exactly that state answers AIR at once, without opening, parsing or
 * logging (the original would read the same bytes, fail the same way and answer AIR). Any change of the file (edited,
 * replaced, deleted, a directory) takes the original path, and so does every call after Cucumber rebuilds its tag map
 * (reloadTagMappings, e.g. on tag updates): the memo is cleared there. A file that does not exist is never remembered (the
 * original creates it). Mapped tags, Almost Unified's preferred items and valid files are untouched. The one thing that
 * changes: one ERROR line per broken file state instead of one per call. -Dbons_and_furious.cucumberTagFileFailureMemo=false
 * runs the original.
 */
public final class CucumberTagFiles {
    /** cucumber_tag_file_failure_memo: -Dbons_and_furious.cucumberTagFileFailureMemo=false runs the original. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cucumberTagFileFailureMemo", "true"));
    /** Statistics for the offline proof: reads answered without the file, failures remembered. */
    public static long answered, remembered;

    /** A file's observable state. */
    private record Stat(boolean exists, boolean isFile, long modified, long length) {
        static Stat of(File f) {
            return new Stat(f.exists(), f.isFile(), f.lastModified(), f.length());
        }
    }

    /** The state of a file whose read failed (null = none). */
    private static volatile Stat failed;
    /** The state this thread saw right before its current read. */
    private static final ThreadLocal<Stat> BEFORE = new ThreadLocal<>();
    private static volatile boolean announced;

    private CucumberTagFiles() {
    }

    /** getItemForTag, before it looks at the file: true = the file is in the state whose read failed (answer AIR). */
    public static boolean knownFailure() {
        if (!enabled) return false;
        Stat now = Stat.of(FMLPaths.CONFIGDIR.get().resolve("cucumber-tags.json").toFile());
        Stat bad = failed;
        if (bad != null && bad.equals(now)) {
            answered++;
            return true;
        }
        BEFORE.set(now);
        return false;
    }

    /** getItemForTag, at its "could not read" error line: remember the state the file had before this read. */
    public static void readFailed() {
        if (!enabled) return;
        Stat before = BEFORE.get();
        BEFORE.remove();
        if (before == null || !before.exists() || !before.isFile()) return;
        failed = before;
        remembered++;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: cucumber_tag_file_failure_memo: cucumber-tags.json could not be read; until it changes, unmapped tags answer AIR without reading it again (one error line instead of one per tag)");
        }
    }

    /** TagMapper.reloadTagMappings: forget the failed state. */
    public static void forget() {
        failed = null;
    }
}
