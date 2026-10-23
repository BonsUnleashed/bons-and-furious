package bons.furious.patch.vanilla_rl_path;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch vanilla_rl_path_char_fast (Minecraft 1.20.1 with Oculus 1.8.0, client). Helper of
 * ResourceLocationPathCharMixin; no Minecraft or Oculus code is carried.
 *
 * Every ResourceLocation the game builds checks its path one character at a time: isValidPath (isValidPath) asks
 * validPathChar (validPathChar) for each character. Oculus 1.8.0 (MixinResourceLocation) adds a cancellable HEAD callback to
 * validPathChar that answers true for 'A'..'Z' and does nothing for any other character, so every character of every path
 * allocates a CallbackInfoReturnable and calls a handler. For the characters vanilla accepts ('a'..'z', '0'..'9', '_', '-',
 * '/', '.') the merged validPathChar can only answer true: vanilla's own test is true, and the only merged hook never
 * answers anything but true. So for those characters this switch answers true without the call; every other character
 * takes the full original call, Oculus's A-Z rule and any other hook included. isValidPath itself, and every hook other
 * mods put on it (Oculus's "DUMMY" check at its start, Citresewn's and Entity Texture Features' broken-path hooks at its
 * returns), runs exactly as before; only the per-character call for the accepted characters is left out.
 *
 * Stand-down: the answer relies on validPathChar being vanilla's test plus Oculus's hook. The guard pins vanilla's two
 * methods and Oculus's handler; at the first use this class also reads every method other mods merged into
 * ResourceLocation (their @MixinMerged annotations) and stays off (every character takes the original call, one INFO
 * line) when one of them could change isValidPath or validPathChar and is not in the list below (the hooks checked for
 * this pack on 2026-10-19: Oculus, Citresewn, Entity Texture Features, AllTheLeaks, FerriteCore, and this switch). Such a
 * member is an overwrite of one of the two methods, or an injector handler, whose injection point cannot be seen at
 * runtime (see unknownMember). Other merged members cannot run inside the two methods and do not count: C2ME
 * 0.2.0+alpha.12's Jar-in-Jar module opts-allocs overwrites ResourceLocation.toString (a cached "namespace:path"), and its
 * chunk-serializer module adds getStringBytes; before that rule the first one kept this switch off in the full pack.
 *
 * Runtime flag: -Dbons_and_furious.rlPathCharFast=false sends every character to the original call.
 * SHADOW MODE for rigs: -Dbons_and_furious.rlPathCharFast.shadow=true makes the original call for every character the
 * switch would answer, counts SHADOW_CHECKS / SHADOW_MISMATCHES (an original answer other than true; WARN for the first 20)
 * and returns the original's answer.
 */
public final class PathCharFast {
    /** Runtime switch (the config switch acts when the class is transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.rlPathCharFast", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.rlPathCharFast.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    /**
     * The mixins whose handlers ResourceLocation may carry while this switch acts (see unknownMember: only handlers and
     * overwrites of the two relied-on methods need to be listed; the plain names below stay for the record): mixin class ->
     * the names of the members it merges (a handler's merged name ends with "$" + its own name). Proven with the switch
     * (65,536 characters, real and random paths) on this pack's jars, 2026-10-19:
     * Oculus 1.8.0 (validPathChar A-Z hook, isValidPath DUMMY hook), Citresewn 1.0.2 (isValidPath RETURN), Entity Texture
     * Features 7.1 (isValidPath RETURN + its helper), AllTheLeaks 1.1.3 (constructor RETURN), FerriteCore 6.0.1 (accessor).
     */
    static final Map<String, Set<String>> KNOWN = Map.of(
            "bons.furious.mixin.vanilla_rl_path.ResourceLocationPathCharMixin", Set.of("bons$pathChar"),
            "net.irisshaders.iris.mixin.texture.MixinResourceLocation", Set.of("iris$blockDUMMY", "iris$allowInvalidPaths"),
            "dev.tomwmth.citreforged.mixin.broken_paths.ResourceLocationMixin", Set.of("citresewn$brokenpaths$processBrokenPaths"),
            "traben.entity_texture_features.mixin.mixins.MixinIdentifier", Set.of("etf$illegalPathOverride", "allowPathAndLog"),
            "dev.uncandango.alltheleaks.mixin.core.main.ResourceLocationMixin", Set.of("atl$internStrings"),
            "malte0811.ferritecore.mixin.mrl.ResourceLocationAccess", Set.of("setNamespace", "setPath"));
    /** 0 = not checked yet, 1 = only known mixins (the switch acts), 2 = another mixin is merged (the switch stays off). */
    private static volatile int neighbours;
    private static final java.util.concurrent.atomic.AtomicBoolean ANNOUNCED = new java.util.concurrent.atomic.AtomicBoolean();

    private PathCharFast() {
    }

    /** True for the characters vanilla's validPathChar accepts: [a-z0-9_./-]. */
    public static boolean accepted(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '/' || c == '.';
    }

    /** In place of isValidPath's validPathChar(c) call. */
    public static boolean pathChar(char c, Operation<Boolean> original) {
        if (enabled && accepted(c) && neighboursKnown()) {
            if (SHADOW) return shadow(c, original);
            return true;
        }
        return original.call(c);
    }

    private static boolean shadow(char c, Operation<Boolean> original) {
        boolean answer = original.call(c);
        SHADOW_CHECKS.incrementAndGet();
        if (!answer && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: vanilla_rl_path_char_fast SHADOW MISMATCH {}: validPathChar('{}', U+{}) answered false, the switch would answer true",
                    SHADOW_MISMATCHES.get(), c, Integer.toHexString(c));
        }
        return answer;
    }

    /** Set while a thread runs the neighbour check, so a location built during that check takes the original call. */
    private static final ThreadLocal<Boolean> CHECKING = new ThreadLocal<>();

    static boolean neighboursKnown() {
        int n = neighbours;
        if (n == 0) {
            if (CHECKING.get() != null) return false;
            CHECKING.set(Boolean.TRUE);
            try {
                n = check();
            } finally {
                CHECKING.remove();
            }
        }
        return n == 1;
    }

    /**
     * The neighbour check, without a lock: the first resource locations may be built on several threads at once early in the
     * start-up, while classes load; holding a monitor around reflection there could deadlock with a class-loading lock. Every
     * thread that gets here computes the same answer from the same class, so a race only repeats the work; until an answer is
     * stored, each character takes the original call.
     */
    private static int check() {
        String other;
        try {
            other = unknownMember(ResourceLocation.class);
        } catch (Throwable t) {
            other = "(check failed: " + t + ")";
        }
        int n = other == null ? 1 : 2;
        neighbours = n;
        if (ANNOUNCED.compareAndSet(false, true)) {
            if (other != null) LOGGER.info("Bons and Furious: vanilla_rl_path_char_fast stays off because ResourceLocation also carries {}", other);
            else LOGGER.info("Bons and Furious: vanilla_rl_path_char_fast: ResourceLocation paths check [a-z0-9_./-] without the per-character validPathChar call{}",
                    SHADOW ? " (SHADOW MODE: the original call runs for every character and is compared)" : "");
        }
        return n;
    }

    /** The two methods the switch relies on: isValidPath and validPathChar (SRG names). */
    static final Set<String> RELIED_ON = Set.of("isValidPath", "validPathChar");

    /**
     * The first member merged into {@code type} that could change isValidPath or validPathChar and that KNOWN does not
     * list ("mixin -> member"), or null. A merged member counts when
     *   - it carries the name of one of the two methods (an overwrite of what the switch relies on), or
     *   - its name contains '$': Mixin 0.8.5 renames every injector handler it merges to "prefix$uid$name"
     *     (MethodMapper.remapHandlerMethod / getHandlerName, format "%s$%s%s$%s"), so every handler has one, and where it
     *     was injected cannot be read at runtime (renamed @Unique members and lambdas have a '$' too; they count as well).
     * Any other merged member (an overwrite of another method such as toString, an accessor or invoker, a new method)
     * cannot run inside the two methods: the guard pins their bytecode, isValidPath calls only String.length,
     * String.charAt and validPathChar, and validPathChar calls nothing; code injected into either reaches other members
     * only through a handler, which is checked itself.
     */
    static String unknownMember(Class<?> type) {
        for (Method m : type.getDeclaredMethods()) {
            MixinMerged merged = m.getAnnotation(MixinMerged.class);
            if (merged == null) continue;
            String name = m.getName();
            Set<String> names = KNOWN.get(merged.mixin());
            if (names != null && knownName(name, names)) continue;
            if (RELIED_ON.contains(name) || name.indexOf('$') >= 0) return merged.mixin() + " -> " + name;
        }
        return null;
    }

    private static boolean knownName(String member, Set<String> names) {
        for (String n : names) if (member.equals(n) || member.endsWith("$" + n)) return true;
        return false;
    }
}
