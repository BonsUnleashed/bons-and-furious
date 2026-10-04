package bons.furious.patch.mutantmonsters_c2;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mutantmonsters_empty_shoulder_skip (Mutant Monsters, AGPL-3.0; 1.21.1 tested build: Mutant
 * Monsters v21.1.1 for NeoForge 1.21.1, MutantMonsters-v21.1.1-1.21.1-NeoForge.jar; both sides: the player tick runs on
 * the server, and on the client for every player it shows).
 *
 * At the end of every player tick Mutant Monsters (PlayerEventsHandler.onEndPlayerTick) checks both shoulders for its
 * creeper minion, which then hisses now and then: it reads the shoulder tag's "id" string, parses it into a
 * ResourceLocation, looks that up in the entity type registry and compares the result with the creeper minion. A
 * shoulder without a passenger holds an empty tag, so every player pays two parses and two failed registry lookups per
 * tick on each side.
 *
 * {@link #nothingToPlay} says "skip" for a non-null tag that has no "id" key, and only then. Why it is identical: for
 * such a tag CompoundTag.getString("id") is "", which parses to minecraft: (an empty path) and names no entity type, so
 * Mutant Monsters' lookup finds nothing and its method does nothing else either: no sound, no random number drawn, no
 * exception (its "Silent" checks are plain reads, and the creeper minion holder it reads is bound whenever players
 * tick). A tag with an "id" key of any type, and a null tag (Mutant Monsters' own null handling, a
 * NullPointerException), still go Mutant Monsters' own way, unchanged. No Mutant Monsters code is carried here.
 *
 * Ported to 1.21.1: the listener is now onEndPlayerTick (PuzzlesLib PlayerTickEvents.END); playShoulderEntitySound is the
 * same code except that the creeper minion is a Holder.Reference (ModEntityTypes) instead of a PuzzlesLib
 * RegistryReference. 1.21.1's ResourceLocation.tryParse("") goes through tryBySeparator, which still builds minecraft:
 * with an empty path, and EntityType.byString answers through DefaultedMappedRegistry.getOptional (empty for that id).
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
        if (!enabled || tag == null || tag.contains("id")) return false;
        if (!announced) announce();
        if (SHADOW) shadow(tag);
        return true;
    }

    private static void shadow(CompoundTag tag) {
        SHADOW_CHECKS.incrementAndGet();
        if (EntityType.byString(tag.getString("id")).isPresent() && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: mutantmonsters_empty_shoulder_skip shadow mismatch: a shoulder tag without an id names an entity type ({})", tag);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: mutantmonsters_empty_shoulder_skip: Mutant Monsters skips its creeper-minion check for empty shoulders");
    }
}
