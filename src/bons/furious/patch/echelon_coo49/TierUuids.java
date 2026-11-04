package bons.furious.patch.echelon_coo49;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch echelon_tier_uuid_memo (Echelon - Linggango Variant 1.2.6, mod id tiered; both sides). Echelon is
 * MIT; none of its code is carried. Ours, idea from Collections Of Optimizations (echelon.cacheTierAttributeUuids), idea
 * text only.
 *
 * What Echelon does: ForgeTieredAttributeSubscriber.onItemAttributeModifiers (every ItemStack.getAttributeModifiers of a
 * tiered stack: tooltips every frame for each slot, equipment changes, other mods' attribute queries) derives one modifier
 * UUID per tier attribute entry with UUID.nameUUIDFromBytes((attribute id + "_" + slot name + "_" + salt) as UTF-8
 * bytes): a fresh MessageDigest.getInstance("MD5") and an MD5 digest per entry per call.
 *
 * What the switch does ({@link #uuid}, a Redirect of that one call): UUID.nameUUIDFromBytes is a pure function of the
 * bytes, so the answer for bytes it has already seen is kept in a fixed table of {@value #SIZE} slots (one entry per slot,
 * a new key replaces the old one: bounded) and handed out again when the next call's bytes are equal (hash, then the full
 * bytes). The UUID is equal to the one the call would make (UUIDs are compared by value everywhere: attribute modifiers,
 * their maps and packets); only the MD5 work is skipped. The slots' hashes sit in their own int array, so a miss is seen
 * without touching an entry object. A miss derives the UUID with UUID.nameUUIDFromBytes' own steps (MD5 of the bytes,
 * version 3 and IETF variant bits, big-endian halves) on a MessageDigest kept per thread, which also leaves out the
 * MessageDigest.getInstance lookup the shipped call makes every time. An entry keeps the call's byte array itself:
 * Echelon builds it with String.getBytes for this one call and never touches it again (the guard row pins that code).
 * The table is read and written without locks: an entry is an immutable object, a racing reader may see a hash and an
 * entry of different writers, and every hit is checked against the entry's own hash and bytes.
 *
 * Flag: -Dbons_and_furious.echelonTierUuidMemo=false makes every call as shipped. Null bytes and a JDK without MD5 take the
 * shipped call (and throw as it does).
 */
public final class TierUuids {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.echelonTierUuidMemo", "true"));
    static final int SIZE = 2048;
    private static final Entry[] TABLE = new Entry[SIZE];
    private static final int[] HASHES = new int[SIZE];
    private static final ThreadLocal<MessageDigest> MD5 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    });
    /** Hits and misses (plain counters for probes and the harness, not exact under threads). */
    public static long hits, misses;
    private static volatile boolean announced;

    private static final class Entry {
        final byte[] key;
        final int hash;
        final UUID uuid;

        Entry(byte[] key, int hash, UUID uuid) {
            this.key = key;
            this.hash = hash;
            this.uuid = uuid;
        }
    }

    private TierUuids() {
    }

    /**
     * UUID.nameUUIDFromBytes(name), from the table when these bytes were seen before. One method on purpose, with the
     * digest's big-endian halves written out: its byte code is larger than the JIT inlines into a hot caller (325 bytes),
     * so Echelon's handler compiles as small as the shipped one, which matters on its paths that never ask for a UUID (an
     * inlined table made the untiered query 11 % slower, 2026-11-02); the call itself costs a few nanoseconds per UUID.
     */
    public static UUID uuid(byte[] name) {
        if (!enabled || name == null) return UUID.nameUUIDFromBytes(name);
        int h = Arrays.hashCode(name);
        int slot = (h ^ (h >>> 16)) & (SIZE - 1);
        if (HASHES[slot] == h) {
            Entry e = TABLE[slot];
            if (e != null && e.hash == h && Arrays.equals(e.key, name)) {
                hits++;
                return e.uuid;
            }
        }
        MessageDigest md = MD5.get();
        if (md == null) return UUID.nameUUIDFromBytes(name);
        byte[] d = md.digest(name);
        // UUID.nameUUIDFromBytes' last steps: version 3, IETF variant, big-endian halves
        d[6] &= 0x0f;
        d[6] |= 0x30;
        d[8] &= 0x3f;
        d[8] |= (byte) 0x80;
        long msb = ((long) (d[0] & 0xff) << 56) | ((long) (d[1] & 0xff) << 48) | ((long) (d[2] & 0xff) << 40) | ((long) (d[3] & 0xff) << 32)
                | ((long) (d[4] & 0xff) << 24) | ((long) (d[5] & 0xff) << 16) | ((long) (d[6] & 0xff) << 8) | (long) (d[7] & 0xff);
        long lsb = ((long) (d[8] & 0xff) << 56) | ((long) (d[9] & 0xff) << 48) | ((long) (d[10] & 0xff) << 40) | ((long) (d[11] & 0xff) << 32)
                | ((long) (d[12] & 0xff) << 24) | ((long) (d[13] & 0xff) << 16) | ((long) (d[14] & 0xff) << 8) | (long) (d[15] & 0xff);
        UUID u = new UUID(msb, lsb);
        TABLE[slot] = new Entry(name, h, u);
        HASHES[slot] = h;
        misses++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: echelon_tier_uuid_memo applies (Echelon's tier modifier UUIDs are derived once per attribute, slot and salt)");
        }
        return u;
    }
}
