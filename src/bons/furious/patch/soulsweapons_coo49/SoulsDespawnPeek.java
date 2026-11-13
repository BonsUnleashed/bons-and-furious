package bons.furious.patch.soulsweapons_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch soulsweapons_despawn_timer_no_write (Soulslike Weaponry 1.4.10, same bytes in 1.4.8; CC0-1.0;
 * server side; since 1.0.49). Exact variant. Ours; no Soulslike Weaponry code is carried. Idea: Collections Of Optimizations
 * (soulsweapons.leanDespawnTimer), idea text only.
 *
 * DespawnTimerData.getDespawnTicks(entity) answers 0 for a player; otherwise it writes "despawn_timer" = 0 into
 * entity.getPersistentData() when the key is missing and then answers getInt("despawn_timer"). Its two callers
 * (Soulslike's Entity.tick hook, for every server entity on every tick, and the Chungus Tonic effect) only compare the
 * answer with 0. The key is read and written only by DespawnTimerData (getDespawnTicks, addDespawnTicks, setDespawnTicks:
 * a byte census of the jar), and a missing key reads as 0 in every one of them, exactly as a stored 0.
 *
 * ticks(entity) gives the same answer without the write: for a non-player entity whose class (up to Entity) does not
 * declare its own getPersistentData(), Forge's Entity.getPersistentData() returns the persistentData field (creating it
 * when null), so: field null or no "despawn_timer" key -> 0 (what the getter returns after its write); key present ->
 * getInt("despawn_timer")'s answer, taken from one lookup of the key (a numeric tag with a numeric type id -> its int
 * value, anything else -> 0, getInt's cases) where the getter makes three (contains, then getInt's type check and read).
 * Anything else (a player, a class with its own getPersistentData, the switch off) answers RUN_ORIGINAL and the getter
 * runs as shipped. The class check (PLAIN) is made once per entity and kept in a byte SoulsEntityPeekMixin adds to Entity.
 *
 * The one difference (exact variant): an entity that has no despawn timer is no longer given "despawn_timer": 0, and an
 * entity that nothing else gives ForgeData (items, arrows, experience orbs; Soulslike's posture / bleed / frost getters
 * still write their own keys into living entities) no longer gets an empty ForgeData compound, in memory and in every saved
 * chunk. Runtime flag -Dbons_and_furious.soulsweaponsDespawnTimerNoWrite=false.
 */
public final class SoulsDespawnPeek {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.soulsweaponsDespawnTimerNoWrite", "true"));
    /** "Run the original getter" (no int answer maps to it). */
    public static final long RUN_ORIGINAL = Long.MIN_VALUE;
    static final String KEY = "despawn_timer";
    private static volatile boolean announced;

    /** True for entity classes that do not declare their own getPersistentData() below Entity (Forge's code answers). */
    static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            // the JVM's resolution of that one method (bons.furious.guard.Declared): Class.getDeclaredMethod resolves the types of every
            // method the class declares, and on a dedicated server Forge logged its red "invalid dist" ERROR for each entity class with a
            // client-typed method (bug sweep 2026-11-12). A class that cannot be asked keeps the original getter.
            return bons.furious.guard.Declared.inherits(type, Entity.class, "getPersistentData", java.lang.invoke.MethodType.methodType(CompoundTag.class));
        }
    };

    private SoulsDespawnPeek() {
    }

    /** The getter's answer without its write, or RUN_ORIGINAL (see the class comment). */
    public static long ticks(Entity entity) {
        if (!enabled || entity == null || entity instanceof Player || !(entity instanceof SoulsEntityPeek peek)) return RUN_ORIGINAL;
        byte plain = peek.bons$soulsweaponsCoo49Plain();
        if (plain == 0) peek.bons$soulsweaponsCoo49Plain(plain = PLAIN.get(entity.getClass()) ? (byte) 1 : (byte) 2);
        if (plain != 1) return RUN_ORIGINAL;
        CompoundTag tag = peek.bons$soulsweaponsCoo49PersistentData();
        Tag value = tag == null ? null : tag.m_128423_(KEY);                  // get(String): one lookup
        if (value instanceof NumericTag n) {                                  // getInt: contains(key, 99) = id 1..6, then the cast
            byte id = n.m_7060_();                                            // getId()
            if (id >= 1 && id <= 6) return n.m_7047_();                       // getAsInt()
        }
        if (value == null && !announced) announce();
        return 0L;                                                            // missing (the getter writes 0) or not a number
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: soulsweapons_despawn_timer_no_write applies (Soulslike Weaponry's despawn timer is read without writing despawn_timer: 0 into every entity)");
    }
}
