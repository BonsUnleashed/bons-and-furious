package bons.furious.patch.shouldersurfing_c49;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch shouldersurfing_item_pattern_memo (Shoulder Surfing Reloaded 1.21.1-5.3.0, MIT; client side).
 * Ours; no Shoulder Surfing code is carried.
 *
 * What Shoulder Surfing does. Its api.util.Util.expressionToMatchPredicate(String) returns
 * Pattern.compile(expression).asMatchPredicate(), or expression::equals when the compile throws. Its callers compile every
 * configured expression again on every use: the adaptive crosshair check per hand item per client tick
 * (ComputePlayerAimStateEventHandlerImpl), the pick filter per candidate entity (PickContext), the camera's non-collidable
 * block check per non-full block (ShoulderSurfingCamera.hasNoCollision), so a regular expression is compiled thousands of
 * times a minute.
 *
 * What the switch does. Inside that method only, Pattern.compile(expression) is answered from a memo of the Pattern it
 * returned for that expression (for a null expression Pattern.compile runs as before; once 1,024 expressions are kept
 * the memo stops answering and Pattern.compile runs as before for every expression, without a lookup, so a caller that
 * passes ever new expressions pays one flag read). An expression whose compile threw keeps that exception, and it is
 * thrown again from the same place, where the method's own catch discards it and returns expression::equals as before.
 * Errors (StackOverflowError on a pathological expression) are never kept.
 *
 * Why it is identical. A Pattern is immutable and thread-safe, and compiling the same expression gives a Pattern that
 * matches exactly the same strings; asMatchPredicate() still runs on every call, so every caller still gets a new
 * predicate object, as before; the kept exception is never seen outside the method's own catch.
 *
 * -Dbons_and_furious.shouldersurfingItemPatternMemo=false switches it off at run time.
 */
public final class PatternMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.shouldersurfingItemPatternMemo", "true"));
    static final int CAP = 1024;
    /** expression -> its Pattern, or the RuntimeException its compile threw. */
    private static final ConcurrentHashMap<String, Object> MEMO = new ConcurrentHashMap<>();
    private static volatile boolean announced;
    /** Set once the memo holds CAP expressions: from then on every call compiles as before. */
    public static volatile boolean full;

    private PatternMemo() {}

    /** In place of Pattern.compile(expression) inside expressionToMatchPredicate. */
    public static Pattern compile(String expression) {
        if (!enabled || expression == null || full) return Pattern.compile(expression);
        Object kept = MEMO.get(expression);
        if (kept instanceof Pattern pattern) return pattern;
        if (kept instanceof RuntimeException failure) throw failure;
        Pattern pattern;
        try {
            pattern = Pattern.compile(expression);
        } catch (RuntimeException failure) {
            remember(expression, failure);
            throw failure;
        }
        remember(expression, pattern);
        return pattern;
    }

    private static void remember(String expression, Object answer) {
        if (MEMO.size() >= CAP) {
            full = true;
            return;
        }
        MEMO.putIfAbsent(expression, answer);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: shouldersurfing_item_pattern_memo applies (Shoulder Surfing's configured expressions are compiled once)");
        }
    }
}
