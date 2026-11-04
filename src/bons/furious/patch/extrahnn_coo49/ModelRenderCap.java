package bons.furious.patch.extrahnn_coo49;

import bons.pure.config.PureConfig;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch extrahnn_rendered_model_cap (Extra Hostile Neural Networks 1.2.2, client; kind change, off by
 * default; value: the largest number of models drawn, default 4, 0 = no cap). Extra Hostile Neural Networks is MIT; none
 * of its code is carried. Ours, idea from Collections Of Optimizations (extrahnn.renderedModelCap), idea text only.
 *
 * What Extra Hostile Neural Networks does: ExtraDataModelItemStackRenderer.renderByItem draws the item's base model and
 * then, for every bound data model the stack stores, a live cached entity through renderEntityInInventory(..., count),
 * where count numbers the bound models (0, 1, 2, ...). The quadrant offsets only distinguish counts 0 to 3 (count 4 and up
 * land on the third quadrant). The creative-tab stack of the item stores EVERY data model id (fillItemCategory), so that
 * one icon draws one entity per data model in the game on every frame it is visible (creative menu, recipe viewers).
 *
 * Trade-off (with the switch on): the renderEntityInInventory call is skipped for count >= the value, so an item holding
 * more models than the value draws only its first value models; everything else in renderByItem runs as before (the base
 * model, every model's entity cache lookup and its tickCount update). A model item made by the merger holds four models,
 * so with the default 4 every legitimately built item looks exactly as before. Value 0 draws every model (stock).
 *
 * Flag: -Dbons_and_furious.extrahnnRenderedModelCap=false draws every model.
 */
public final class ModelRenderCap {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.extrahnnRenderedModelCap", "true"));
    /** Entity draws left out (for probes and the harness). */
    public static final AtomicLong SKIPPED = new AtomicLong();
    private static volatile boolean announced;

    /** The value, read once on the switch-on path (this class is reached only from the switch's mixin). */
    private static final class Value {
        static final int CAP = PureConfig.intValue("extrahnn_rendered_model_cap", 4, 0, 1024);
    }

    private ModelRenderCap() {
    }

    /** The value in use (for the harness). */
    public static int cap() {
        return Value.CAP;
    }

    /** True when the entity of the bound model numbered count is drawn. */
    public static boolean draws(int count) {
        if (!enabled) return true;
        int cap = Value.CAP;
        if (cap == 0 || count < cap) return true;
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: extrahnn_rendered_model_cap applies (data model items draw at most {} model entities)", cap);
        }
        return false;
    }
}
