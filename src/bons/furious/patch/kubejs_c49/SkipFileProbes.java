package bons.furious.patch.kubejs_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch kubejs_skip_file_probes (KubeJS 2001.6.5-build.26, LGPL-3.0; both sides; kind change, off by
 * default). Ours; no KubeJS code is carried.
 *
 * What KubeJS does. At start-up and on every reload it walks kubejs/assets and kubejs/data to warn about file names with
 * upper-case or invalid characters (scanForInvalidFiles), testing every file with Files.isRegularFile and Files.isReadable
 * (on Windows isReadable opens the file, which an antivirus scanner may inspect), and getGenerated walks kubejs/data or
 * kubejs/assets again with isRegularFile, isReadable and isHidden per file, one file after the other.
 *
 * What the switch does (a trade-off). scanForInvalidFiles returns at once, and getGenerated leaves out the isReadable test.
 * What changes for the player: KubeJS no longer reports file names with upper-case or invalid characters, and a file that
 * cannot be read (no permission, locked) becomes a generated entry whose read fails when it is loaded (KubeJS prints the
 * error and the entry is empty) instead of being left out. Readable files are generated exactly as before.
 *
 * -Dbons_and_furious.kubejsSkipFileProbes=false switches it off at run time (with the switch on in the config).
 */
public final class SkipFileProbes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.kubejsSkipFileProbes", "true"));
    private static volatile boolean announced;

    private SkipFileProbes() {}

    public static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: kubejs_skip_file_probes applies (KubeJS skips its file-name scan and the per-file readability test)");
        }
    }
}
