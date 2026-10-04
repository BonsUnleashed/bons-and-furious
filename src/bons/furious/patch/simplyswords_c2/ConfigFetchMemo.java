package bons.furious.patch.simplyswords_c2;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch simplyswords_config_unchanged_reuse (Simply Swords 1.56.0-1.20.1, Forge; both sides). No Simply
 * Swords code is carried here (its licence is the custom Timefall Development License): the mixins wrap its
 * Config.safeValueFetch and three kinds of calls inside it, and the logic below is our own.
 *
 * Every Simply Swords config read (Config.getBoolean / getFloat / getDouble / getInt: about 70 classes, on hits, per tick
 * for Magiblade holders, per Wildfire effect tick, per runic sword in an inventory) first calls safeValueFetch(type,
 * parent): it reads the parent's json5 file with a Scanner, parses all of it with Gson, and puts every entry that converts
 * to the type into one of four static maps; entries that do not convert throw a NumberFormatException each, caught and
 * dropped (General: 6 per float read, RunicEffects: 29). Then getX reads its key from that map.
 *
 * The file is still read on every call, through the same FileInputStream open the Scanner uses. What is kept, for each
 * (type, parent), is the result of the last completed run: the file's bytes and the puts that run made, in order. When the
 * bytes read now are the same, the kept puts are applied (and not even that when the last writes to that map were exactly
 * these puts) and Simply Swords' read, parse and conversions are skipped. When they differ, Simply Swords' own code runs
 * unchanged (its readFile, its parse, its conversions) and its puts are recorded; the run is kept under the bytes read
 * just before only when the text its readFile returned is exactly the text those bytes give through the JDK's path for
 * Scanner(File) (a Scanner over a channel of the bytes, the platform charset's decoder, delimiter \Z). That comparison
 * only decides whether to keep: the puts depend on the text alone, so a kept run always belongs to its bytes, even if the
 * file changed while Simply Swords read it. Bytes the charset cannot decode cleanly, a file that cannot be opened, an
 * empty file or text that does not parse are never kept, so the original code runs (and prints and throws) as before. An
 * edit is seen on the very next call; there is no interval and no timestamp.
 *
 * Why this is identical: the puts are a pure function of (type, text) and the text a pure function of the bytes; the maps
 * are private to Config and only safeValueFetch writes them (1.56.0: four put sites, and the guard pins Config's method
 * set), getX only reads them and unboxes, so getX sees the same map contents, key for key and in the same insertion order.
 * Calls are serialized by our own lock (the original let the client and server threads write the same LinkedHashMaps at
 * the same time); that removes a data race and changes nothing for a single thread.
 *
 * Runtime flag: -Dbons_and_furious.simplyswordsConfigUnchangedReuse=false runs the original on every call. Shadow mode for
 * rigs: -Dbons_and_furious.simplyswordsConfigUnchangedReuse.shadow=true parses on every call and compares the puts with the
 * kept ones (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines).
 */
public final class ConfigFetchMemo {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsConfigUnchangedReuse", "true"));
    /** Shadow mode (see the class comment): every call parses and the kept puts are only compared. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.simplyswordsConfigUnchangedReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Calls answered from kept puts / calls that parsed (for probes and the harness; written under LOCK). */
    public static long reused, parsed;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Handed from readFile to getJsonObject (identity only) when the kept puts were applied. */
    private static final String KEPT_MARKER = new String("bons_and_furious:simplyswords_config_unchanged_reuse");
    private static final Object LOCK = new Object();
    /** The safeValueFetch call in progress (set and cleared under LOCK by the thread that runs it). */
    private static volatile Call current;
    /** Set when one of the maps was written outside a tracked call (runtime flag off); forgets every last-writer mark. */
    private static volatile boolean untracked;
    // guarded by LOCK
    private static final Map<String, Kept> KEPT = new HashMap<>();
    private static final Map<Object, Kept> LAST_WRITER = new IdentityHashMap<>();
    private static boolean announced;

    /** One completed run: the file bytes its text was made from, and its puts in order (map, key, value triples). */
    private static final class Kept {
        final byte[] bytes;
        final Object[] puts;

        Kept(byte[] bytes, Object[] puts) {
            this.bytes = bytes;
            this.puts = puts;
        }
    }

    private static final class Call {
        final Thread thread = Thread.currentThread();
        final String key;
        byte[] bytes;          // the bytes our text was made from (null when the original read the file itself)
        String text;           // that text (identity: the string handed to getJsonObject)
        List<Object> puts;     // non-null while the original's puts are being recorded
        Kept shadowOf;

        Call(String key) {
            this.key = key;
        }
    }

    private ConfigFetchMemo() {
    }

    /** The @WrapMethod handler for Config.safeValueFetch(type, parent). */
    public static void fetch(String type, String parent, Operation<Void> original) {
        if (!enabled) {
            original.call(type, parent);
            return;
        }
        synchronized (LOCK) {
            if (untracked) {
                untracked = false;
                LAST_WRITER.clear();
            }
            Call outer = current;
            Call c = new Call(type + '\u0000' + parent);
            current = c;
            boolean completed = false;
            try {
                original.call(type, parent);
                completed = true;
            } finally {
                current = outer;
                if (c.puts != null && completed) finish(c);
            }
        }
    }

    /** The @WrapOperation handler for the readFile(file) call inside safeValueFetch (one per parent case). */
    public static String readFile(File file, Operation<String> original) {
        Call c = current;
        if (c == null || c.thread != Thread.currentThread()) return original.call(file);
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(file)) {     // the open Scanner(File) does
            bytes = in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            return original.call(file);                             // the original reports it (and reads again) as before
        }
        Kept k = KEPT.get(c.key);
        if (k != null && Arrays.equals(bytes, k.bytes)) {
            if (!SHADOW) {
                apply(k);
                reused++;
                return KEPT_MARKER;
            }
            c.shadowOf = k;
        }
        String text = original.call(file);             // Simply Swords' own read, exactly as before
        // Keep this run only when the text it read is exactly the text these bytes give (Scanner(File)'s path over them):
        // then its puts belong to these bytes even if the file changed in between, because the puts depend on the text.
        if (text != null && text.equals(scannerText(bytes))) {
            c.bytes = bytes;
            c.text = text;
        }
        return text;
    }

    /** The @WrapOperation handler for the getJsonObject(text) call inside safeValueFetch. */
    public static JsonObject json(String text, Operation<JsonObject> original) {
        if (text == KEPT_MARKER) return null;      // the original skips its typed loop when the parse result is null
        Call c = current;
        if (c == null || c.thread != Thread.currentThread()) return original.call(text);
        JsonObject parsedJson = original.call(text);
        parsed++;
        if (parsedJson != null && c.bytes != null && text == c.text) c.puts = new ArrayList<>();
        return parsedJson;
    }

    /** The @WrapOperation handler for the four map.put(key, value) calls inside safeValueFetch. */
    public static Object put(HashMap<Object, Object> map, Object key, Object value, Operation<Object> original) {
        Call c = current;
        if (c != null && c.thread == Thread.currentThread()) {
            LAST_WRITER.remove(map);                 // a run that is not kept yet writes this map
            if (c.puts != null) {
                c.puts.add(map);
                c.puts.add(key);
                c.puts.add(value);
            }
        } else {
            untracked = true;
        }
        return original.call(map, key, value);
    }

    /**
     * The text Scanner(File) with delimiter \Z gives for these bytes (JDK behaviour, used only to check that a run's text
     * belongs to the bytes before keeping it): Scanner(File) is Scanner(new FileInputStream(file).getChannel()), which
     * decodes with the platform charset's decoder through Channels.newReader; the same constructor path is used here over
     * a channel of the bytes. Bytes the charset cannot decode cleanly, and any exception (an empty file:
     * NoSuchElementException), give null: such a run is not kept.
     */
    static String scannerText(byte[] bytes) {
        try {
            Charset.defaultCharset().newDecoder().decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return null;
        }
        try (Scanner scanner = new Scanner(Channels.newChannel(new ByteArrayInputStream(bytes)))) {
            scanner.useDelimiter("\\Z");
            return scanner.next();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Applies kept puts unless the last writes to their map were exactly these puts (then the map already holds them). */
    private static void apply(Kept k) {
        Object[] p = k.puts;
        if (p.length == 0) return;
        Object map = p[0];
        if (LAST_WRITER.get(map) == k) return;
        @SuppressWarnings("unchecked")
        HashMap<Object, Object> m = (HashMap<Object, Object>) map;
        for (int i = 0; i < p.length; i += 3) m.put(p[i + 1], p[i + 2]);
        LAST_WRITER.put(map, k);
    }

    private static void finish(Call c) {
        Object[] puts = c.puts.toArray();
        Kept k = new Kept(c.bytes, puts);
        if (c.shadowOf != null) {
            SHADOW_CHECKS.incrementAndGet();
            if (!samePuts(c.shadowOf.puts, puts) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: simplyswords_config_unchanged_reuse SHADOW MISMATCH for {}: {} kept puts, {} made by the original",
                        c.key.replace('\u0000', '/'), c.shadowOf.puts.length / 3, puts.length / 3);
            }
        }
        KEPT.put(c.key, k);
        if (puts.length > 0) LAST_WRITER.put(puts[0], k);
        if (!announced) {
            announced = true;
            LOGGER.info(SHADOW ? "Bons and Furious: simplyswords_config_unchanged_reuse SHADOW MODE: Simply Swords parses its config on every read; "
                    + "the kept results are only compared (mismatches as WARN)"
                    : "Bons and Furious: simplyswords_config_unchanged_reuse: Simply Swords config files are parsed again only when their bytes change");
        }
    }

    private static boolean samePuts(Object[] a, Object[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (i % 3 == 0 ? a[i] != b[i] : !Objects.equals(a[i], b[i])) return false;
        }
        return true;
    }
}
