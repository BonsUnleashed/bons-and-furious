package bons.furious.patch.enigmaticaddons_coo49;

import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch enigmaticaddons_annihilating_sword_bonus_memo (Enigmatic Addons 1.2.7, server side). Ours; no
 * Enigmatic Addons code is carried. Idea: Collections Of Optimizations (enigmaticaddons.skipUnsetPersistentData, its part
 * on AnnihilatingSword), idea text only.
 *
 * AnnihilatingSword.onTick runs for every living entity on every tick. On the server, for every entity outside the
 * sword's short after-kill window, it removes the sword's attack-speed bonus with
 * removeAttributeModifiers(getAttackSpeedBonus()). getAttackSpeedBonus is a private method that reads nothing and builds a
 * new one-entry ImmutableMultimap per call (UUID.fromString of a constant, a new AttributeModifier with a constant id,
 * name, amount and operation). The first result is kept and handed to that call again: an immutable multimap of an
 * immutable modifier, equal to every new one in attribute, id, name, amount and operation (removal uses the attribute,
 * the operation and the id). Flag: -Dbons_and_furious.enigmaticaddonsAnnihilatingSwordBonusMemo=false; shadow (builds the
 * multimap as shipped too and compares every entry): -Dbons_and_furious.enigmaticaddonsAnnihilatingSwordBonusMemo.shadow=true.
 */
public final class SwordBonus {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticaddonsAnnihilatingSwordBonusMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.enigmaticaddonsAnnihilatingSwordBonusMemo.shadow");
    /** Removals answered from the kept multimap. */
    public static final AtomicLong REUSES = new AtomicLong();
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static volatile Object kept;
    private static volatile boolean announced;

    private SwordBonus() {
    }

    /** The wrapped getAttackSpeedBonus() call of the sword's removal branch: its first result, kept. */
    public static Object removal(Object sword, Operation<Object> original) {
        if (!enabled) return original.call(sword);
        Object k = kept;
        if (k == null) {
            k = original.call(sword);
            kept = k;
            return k;
        }
        REUSES.incrementAndGet();
        if (!announced) announce();
        if (SHADOW) shadow(k, original.call(sword));
        return k;
    }

    private static void shadow(Object k, Object fresh) {
        SHADOW_CHECKS.incrementAndGet();
        if (!same(k, fresh) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: enigmaticaddons_annihilating_sword_bonus_memo shadow mismatch: kept {}, built {}", k, fresh);
        }
    }

    /** The two multimaps hold the same attributes with modifiers of the same id, name, amount and operation, in order. */
    public static boolean same(Object a, Object b) {
        if (!(a instanceof Multimap<?, ?> x) || !(b instanceof Multimap<?, ?> y)) return false;
        List<Map.Entry<?, ?>> ex = new ArrayList<>(x.entries()), ey = new ArrayList<>(y.entries());
        if (ex.size() != ey.size()) return false;
        for (int i = 0; i < ex.size(); i++) {
            if (ex.get(i).getKey() != ey.get(i).getKey()) return false;
            if (!(ex.get(i).getValue() instanceof AttributeModifier m) || !(ey.get(i).getValue() instanceof AttributeModifier n)) return false;
            if (!m.m_22209_().equals(n.m_22209_()) || !m.m_22214_().equals(n.m_22214_()) || m.m_22218_() != n.m_22218_() || m.m_22217_() != n.m_22217_()) return false;
        }
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: enigmaticaddons_annihilating_sword_bonus_memo applies (the Annihilating Sword's per-tick bonus removal reuses one bonus map)");
    }

    /** For the offline proof: forget the kept multimap. */
    public static void resetForProof() {
        kept = null;
    }
}
