package bons.furious.patch.supplementaries_lean;

import bons.furious.mixin.supplementaries_lean.HolderReferenceTagsAccessor;
import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.mehvahdjukaar.supplementaries.common.misc.ColoredMapHandler;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;

/**
 * Bons and Furious switch supplementaries_map_tint_memo (Supplementaries 1.20-3.1.41 for Minecraft 1.20.1 Forge; server
 * side, including the integrated server). SRG member names.
 *
 * What Supplementaries does. For every map pixel a held, unlocked map updates (about a thousand per tick per map holder),
 * ColoredMapHandler.ColorData.markColored asks ColoredMapHandler.getCustomColor(block): up to five tag tests on the
 * block's registry holder (NOT_TINTED_ON_MAPS, TINTED_ON_MAPS_GC, _FC, _WC, _GENERIC) and two instanceof tests.
 *
 * What the switch does. The answer is remembered per Block together with the holder's tag set object it was computed for
 * (Holder.Reference.tags). getCustomColor reads nothing else: the block's tags (that set), its class (BushBlock,
 * LeavesBlock) and constant Blocks; the set is immutable and bindTags puts a new set object on the holder at every tag
 * (re)load, so the same set object always gives the same answer. A memo whose set is not the holder's current set (or no
 * memo) calls getCustomColor and stores the new answer. Unbound tags (null set) call getCustomColor every time, which
 * throws exactly as before. Same answer object for every call.
 *
 * -Dbons_and_furious.supplementariesMapTintMemo=false switches it off at run time.
 * -Dbons_and_furious.supplementariesMapTintMemo.shadow=true (verification runs only): every memo hit is checked against
 * getCustomColor (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20; the stock answer is returned).
 */
public final class MapTints {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.supplementariesMapTintMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.supplementariesMapTintMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** The answer of getCustomColor for one tag set object of the block's holder. */
    private record Memo(Set<?> tags, Block answer) {
    }

    private MapTints() {
    }

    /** ColorData.markColored: its ColoredMapHandler.getCustomColor(block) call. */
    public static Block customColor(Block block) {
        if (!enabled) return ColoredMapHandler.getCustomColor(block);
        Set<?> tags = ((HolderReferenceTagsAccessor) (Object) block.m_204297_()).bons$suppTagSet();
        MapTintSlot slot = (MapTintSlot) block;
        if (tags != null && slot.bons$mapTintMemo() instanceof Memo memo && memo.tags() == tags) {
            if (SHADOW) return shadow(block, memo.answer());
            return memo.answer();
        }
        Block answer = ColoredMapHandler.getCustomColor(block);
        if (tags != null) {
            slot.bons$setMapTintMemo(new Memo(tags, answer));
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: supplementaries_map_tint_memo applies (Supplementaries' map tint lookup is remembered per block until the next tag reload){}",
                        SHADOW ? " - shadow verification on" : "");
            }
        }
        return answer;
    }

    private static Block shadow(Block block, Block memo) {
        Block stock = ColoredMapHandler.getCustomColor(block);
        SHADOW_CHECKS.incrementAndGet();
        if (stock != memo) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: supplementaries_map_tint_memo shadow mismatch #{}: {} -> memo {}, stock {}", m, block, memo, stock);
        }
        return stock;
    }
}
