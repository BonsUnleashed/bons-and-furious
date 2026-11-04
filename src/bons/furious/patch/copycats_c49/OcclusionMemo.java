package bons.furious.patch.copycats_c49;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch copycats_occlusion_holder_memo (Copycats+ 3.0.10, also checked on 3.0.8; both sides). Copycats+ is
 * All Rights Reserved: no Copycats+ code is carried; its handler keeps running, one of its calls is answered from a
 * remembered value. Ours, idea from Collections Of Optimizations (copycats.memoStateOcclusion), idea text only.
 *
 * What Copycats+ does: its BlockStateBaseMixin.customOcclusion runs at the start of every BlockStateBase.canOcclude call (per
 * light propagation step and per face test of chunk meshing): getBlockHolder().is(create:copycat_base) inside a
 * try/catch for unbound holders (a ResourceKey lookup and a ResourceLocation.equals), then an instanceof BracketBlock.
 *
 * What the switch does: the answer of that one is(...) call is remembered per block state (a byte: unknown, false, true)
 * after the first call that returned normally with the handler's own COPYCAT_BASE object (the first location object seen
 * is that constant; any other location calls through and is not remembered). Why identical: a block state's holder is its
 * block's intrusive registry holder (a final field), whose key, once bound, can never change (Holder.Reference refuses a
 * different key), so is(location) is constant from then on; an unbound holder throws before anything is remembered, into
 * Copycats+' own catch, exactly as before. The instanceof test and the handler's return value handling are unchanged.
 *
 * Flag: -Dbons_and_furious.copycatsOcclusionHolderMemo=false asks the holder on every call again.
 */
public final class OcclusionMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.copycatsOcclusionHolderMemo", "true"));
    /** The handler's COPYCAT_BASE: the first location object the handler passes (it only ever passes that constant). */
    private static volatile ResourceLocation known;
    private static volatile boolean announced;

    private OcclusionMemo() {
    }

    /** byte memo values */
    public static final byte UNKNOWN = 0, FALSE = 1, TRUE = 2;

    /**
     * In place of holder.is(location) in Copycats+' handler: memo is the state's remembered answer; returns the answer in the
     * low bit and the byte to remember in the next bits (0 = do not change the memo).
     */
    public static int is(Holder<?> holder, ResourceLocation location, byte memo) {
        ResourceLocation k = known;
        if (k == null) {
            known = location;
            k = location;
        }
        boolean same = location == k;
        if (enabled && same && memo != UNKNOWN) return memo == TRUE ? 1 : 0;
        boolean answer = holder.m_203373_(location);   // throws for an unbound holder: nothing is remembered
        if (!enabled || !same) return answer ? 1 : 0;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: copycats_occlusion_holder_memo applies (Copycats+ asks each block state's holder for copycat_base once)");
        }
        return (answer ? 1 : 0) | ((answer ? TRUE : FALSE) << 1);
    }
}
