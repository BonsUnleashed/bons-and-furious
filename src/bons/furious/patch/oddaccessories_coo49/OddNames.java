package bons.furious.patch.oddaccessories_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch oddaccessories_name_update_memo (Odd Accessories 1.1.6, All Rights Reserved; server side: both
 * methods run from curio ticks and inventory ticks on server levels only). Ours; no Odd Accessories code is carried. Idea:
 * Collections Of Optimizations (oddaccessories.nameUpdateInterval), idea text only.
 *
 * GutsItem.updateName and ClayVesselItem.updateName run every tick for every carried or worn Guts / Clay Vessel stack: they
 * build the wanted name (Guts with String.format of a decimal), then compare it with stack.getHoverName().getString(), which
 * parses the stack's display.Name JSON every call, and write setHoverName(Component.literal(name)) only when they differ.
 *
 * hoverName answers that comparison's getHoverName() without parsing when the raw display.Name string equals
 * Component.Serializer.toJson(Component.literal(name)), exactly what setHoverName stores for this name: parsing that string
 * gives a literal component whose getString() is name, so the method compares equal and writes nothing either way; the
 * answer is a literal component of name (kept per site with its JSON) and is only asked for getString(). Every other case
 * (no display tag, no Name, a different or broken JSON) runs getHoverName as shipped, including its removal of a broken
 * Name. format answers String.format(pattern, one Double) from the last call when the pattern, the double's bits and the
 * default FORMAT locale are the same (String.format is a pure function of them).
 *
 * Flag: -Dbons_and_furious.oddaccessoriesNameUpdateMemo=false; shadow: -Dbons_and_furious.oddaccessoriesNameUpdateMemo.shadow=true
 * (getHoverName also runs and its string must equal name; it then supplies the answer; at most 20 mismatches are logged).
 */
public final class OddNames {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.oddaccessoriesNameUpdateMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.oddaccessoriesNameUpdateMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    public static final int GUTS = 0, CLAY_VESSEL = 1;
    private static final Name[] NAMES = new Name[2];
    private static volatile Formatted lastFormat;
    private static volatile boolean announced;

    private OddNames() {
    }

    private record Name(String name, String json, Component component) {
    }

    private record Formatted(String pattern, long bits, Locale locale, String text) {
    }

    private static Name name(int site, String name) {
        Name n = NAMES[site];
        if (n == null || !n.name.equals(name)) {
            Component c = Component.m_237113_(name);
            n = new Name(name, Component.Serializer.m_130703_(c), c);
            NAMES[site] = n;   // a racing writer stores an equal record: either one is right
        }
        return n;
    }

    /** The wrapped ItemStack.getHoverName() of updateName; {@code name} is the name the method wants. */
    public static Component hoverName(ItemStack stack, String name, int site, Operation<Component> original) {
        if (!enabled || name == null || stack == null) return original.call(stack);
        CompoundTag display = stack.m_41737_("display");
        if (display == null || !display.m_128425_("Name", 8)) return original.call(stack);
        Name n = name(site, name);
        if (!display.m_128461_("Name").equals(n.json)) return original.call(stack);
        if (SHADOW) {
            Component shipped = original.call(stack);
            SHADOW_CHECKS.incrementAndGet();
            if (!name.equals(shipped.getString()) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: oddaccessories_name_update_memo shadow mismatch: wanted '{}', getHoverName gives '{}'", name, shipped.getString());
            }
            return shipped;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: oddaccessories_name_update_memo applies (Odd Accessories' name updates compare the stored name without parsing it)");
        }
        return n.component;
    }

    /** The wrapped String.format(pattern, args) of GutsItem.updateName. */
    public static String format(String pattern, Object[] args, Operation<String> original) {
        if (!enabled || pattern == null || args == null || args.length != 1 || !(args[0] instanceof Double d)) return original.call(pattern, args);
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        long bits = Double.doubleToRawLongBits(d);
        Formatted f = lastFormat;
        if (f != null && f.bits == bits && f.pattern.equals(pattern) && f.locale.equals(locale)) return f.text;
        String text = original.call(pattern, args);
        lastFormat = new Formatted(pattern, bits, locale, text);
        return text;
    }
}
