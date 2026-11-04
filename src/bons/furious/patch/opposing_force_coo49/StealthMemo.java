package bons.furious.patch.opposing_force_coo49;

import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch opposing_force_stealth_tick_memo (Opposing Force 2.0.3, MIT; both sides; trade-off, off by
 * default). Ours; no Opposing Force code is carried. Idea: Collections Of Optimizations (opposingforce.cacheStealthVisibility),
 * idea text only.
 *
 * Opposing Force listens to Forge's LivingVisibilityEvent, which fires every time a mob's targeting test measures how
 * visible an entity is (every TargetingConditions test with a range, so every target scan of every hostile mob that
 * considers a player or mob). For each of the six equipment slots the handler asks the slot's stack for its attribute
 * modifiers (ItemStack.getAttributeModifiers, which builds the map and posts Forge's ItemAttributeModifierEvent to every
 * listener of the pack), sums its stealth modifiers and lowers the visibility; then it compares the looker's type with a
 * few mob heads. Twenty mobs scanning one player in a tick ask the same six stacks the same question twenty times.
 *
 * With the switch on the six answers are kept per entity for the game tick: the first visibility test of an entity in a
 * tick asks as before; later tests in the same tick (same level, same game time) get the kept modifier map of a slot while
 * that slot still holds the same stack object. A stack swapped in (equipping, a different item) is asked at once. The sum,
 * the visibility change and the head checks still run per test.
 * Trade-off (off by default): a stack changed in place during the tick (its NBT, damage or count edited without being
 * replaced), or an ItemAttributeModifierEvent listener whose answer for the same stack changes during the tick, is seen
 * from the next tick on (at most one tick, 50 ms, later). Nothing is saved or sent differently.
 *
 * The memo lives on the entity (LivingEntityStealthMemoMixin) and belongs to the thread that made it; another thread asks
 * as shipped. The handler's entity reaches the per-slot wrapper through a per-thread box: set at the handler's HEAD
 * (begin), cleared at its RETURN (end). A kept map is used only for the very stack object it was asked for, and its answer
 * depends on that stack, the slot and the listeners only, so even a box left over from an interrupted or nested handler
 * call cannot hand out a wrong answer (at worst it keeps an answer in another entity's memo, where that entity's own stacks
 * never match it). Flag: -Dbons_and_furious.opposingForceStealthTickMemo=false (the config key decides; this is the
 * emergency off). SHADOW MODE: -Dbons_and_furious.opposingForceStealthTickMemo.shadow=true always asks as shipped and,
 * where a kept map would have answered, compares its modifiers (attribute, id, amount, operation) with the fresh ones
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20; mismatches there are the documented in-tick changes, not
 * errors).
 */
public final class StealthMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "opposing_force_stealth_tick_memo";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.opposingForceStealthTickMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.opposingForceStealthTickMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): answers from a kept map, maps asked and kept. */
    public static long kept, asked;
    private static final int SLOTS = EquipmentSlot.values().length;
    private static volatile boolean announced;

    /** LivingEntity, through LivingEntityStealthMemoMixin. */
    public interface Holder {
        Object bons$opposingForceCoo49Memo();

        void bons$opposingForceCoo49SetMemo(Object memo);
    }

    /** One entity's answers for one game tick of one level. */
    static final class Memo {
        final Thread owner = Thread.currentThread();
        Level level;
        long tick;
        final ItemStack[] stacks = new ItemStack[SLOTS];
        @SuppressWarnings("rawtypes")
        final Multimap[] maps = new Multimap[SLOTS];
    }

    /** The memo of the entity whose visibility the handler on this thread is computing (null: ask as shipped). */
    static final class Box {
        Memo memo;
    }

    private static final ThreadLocal<Box> BOX = ThreadLocal.withInitial(Box::new);

    private StealthMemo() {
    }

    /** StealthVisibilityMemoMixin, at the HEAD of ForgeEvents.onLivingVisibility: the event's entity's memo for this tick. */
    public static void begin(LivingEvent.LivingVisibilityEvent event) {
        Box box = BOX.get();
        box.memo = null;
        if (!enabled || event == null) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Holder holder)) return;
        Level level = entity.m_9236_();
        if (level == null) return;
        Memo m;
        Object o = holder.bons$opposingForceCoo49Memo();
        if (o instanceof Memo mm) {
            if (mm.owner != Thread.currentThread()) return;
            m = mm;
        } else {
            m = new Memo();
            m.tick = Long.MIN_VALUE;
            holder.bons$opposingForceCoo49SetMemo(m);
        }
        long tick = level.m_46467_();
        if (m.level != level || m.tick != tick) {
            m.level = level;
            m.tick = tick;
            Arrays.fill(m.stacks, null);
            Arrays.fill(m.maps, null);
        }
        box.memo = m;
    }

    /** StealthVisibilityMemoMixin, at the RETURN of the handler: the box no longer names an entity. */
    public static void end() {
        BOX.get().memo = null;
    }

    /** StealthVisibilityMemoMixin: the handler's stack.getAttributeModifiers(slot) for the event's entity. */
    @SuppressWarnings("rawtypes")
    public static Multimap modifiers(ItemStack stack, EquipmentSlot slot, Operation<Multimap> original) {
        if (!enabled || stack == null || slot == null) return original.call(stack, slot);
        Memo m = BOX.get().memo;
        if (m == null) return original.call(stack, slot);
        int i = slot.ordinal();
        Multimap keptMap = m.maps[i];
        if (keptMap != null && m.stacks[i] == stack) {
            if (SHADOW) {
                Multimap fresh = original.call(stack, slot);
                SHADOW_CHECKS.incrementAndGet();
                if (!describe(keptMap).equals(describe(fresh))) {
                    long n = SHADOW_MISMATCHES.incrementAndGet();
                    if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: the {} stack {} changed within the tick: kept {} / now {}", KEY, n, slot, stack,
                            describe(keptMap), describe(fresh));
                }
                return fresh;
            }
            kept++;
            return keptMap;
        }
        Multimap fresh = original.call(stack, slot);
        asked++;
        m.stacks[i] = stack;
        m.maps[i] = fresh;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (stealth gear is read once per entity per tick; off by default){}", KEY,
                    SHADOW ? " (SHADOW MODE: always asked, kept answers compared)" : "");
        }
        return fresh;
    }

    /** The modifiers of a map as a sorted list of (attribute, id, amount, operation) lines (shadow comparison). */
    @SuppressWarnings("rawtypes")
    public static List<String> describe(Multimap map) {
        List<String> out = new ArrayList<>();
        if (map == null) return out;
        for (Object o : map.entries()) {
            Map.Entry e = (Map.Entry) o;
            AttributeModifier mod = (AttributeModifier) e.getValue();
            out.add(System.identityHashCode(e.getKey()) + "|" + mod.m_22209_() + "|" + mod.m_22218_() + "|" + mod.m_22217_());
        }
        out.sort(null);
        return out;
    }
}
