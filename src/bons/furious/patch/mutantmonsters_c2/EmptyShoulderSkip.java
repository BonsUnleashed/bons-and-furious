package bons.furious.patch.mutantmonsters_c2;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mutantmonsters_empty_shoulder_skip (Mutant Monsters 8.0.7 for Forge 1.20.1, AGPL-3.0-or-later;
 * both sides: the player tick runs on the server, and on the client for every player it shows).
 *
 * At the end of every player tick Mutant Monsters (PlayerEventsHandler.onPlayerTick$End) checks both shoulders for its
 * creeper minion, which then hisses now and then: it reads the shoulder tag's "id" string, parses it into a
 * ResourceLocation, looks that up in the entity type registry and compares the result with the creeper minion. A
 * shoulder without a passenger holds an empty tag, so every player pays two parses and two failed registry lookups per
 * tick on each side.
 *
 * {@link #nothingToPlay} says "skip" for a non-null tag that has no "id" key, and only then. Why it is identical: for
 * such a tag CompoundTag.getString("id") is "", which parses to minecraft: (an empty path) and names no entity type, so
 * Mutant Monsters' lookup finds nothing and its method does nothing else either: no sound, no random number drawn, no
 * exception (its "Silent" checks are plain reads). A tag with an "id" key of any type, and a null tag (Mutant Monsters'
 * own null handling, a NullPointerException), still go Mutant Monsters' own way, unchanged. No Mutant Monsters code is
 * carried here.
 *
 * Shadow mode (-Dbons_and_furious.mutantmonstersEmptyShoulderSkip.shadow=true): every skip also asks the entity type
 * registry what the skipped lookup would have found and counts a mismatch if it finds any type (only an entity type
 * registered under the empty name minecraft: could make that happen).
 */
public final class EmptyShoulderSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch. -Dbons_and_furious.mutantmonstersEmptyShoulderSkip=false runs Mutant Monsters' check for every shoulder. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mutantmonstersEmptyShoulderSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mutantmonstersEmptyShoulderSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private EmptyShoulderSkip() {
    }

    /** True when Mutant Monsters' shoulder check for this tag can only end without doing anything: a tag without "id". */
    public static boolean nothingToPlay(CompoundTag tag) {
        if (!enabled || tag == null || tag.m_128441_("id")) return false;
        if (!announced) announce();
        if (SHADOW) shadow(tag);
        return true;
    }

    private static void shadow(CompoundTag tag) {
        SHADOW_CHECKS.incrementAndGet();
        if (EntityType.m_20632_(tag.m_128461_("id")).isPresent() && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: mutantmonsters_empty_shoulder_skip shadow mismatch: a shoulder tag without an id names an entity type ({})", tag);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: mutantmonsters_empty_shoulder_skip: Mutant Monsters skips its creeper-minion check for empty shoulders");
    }
}
