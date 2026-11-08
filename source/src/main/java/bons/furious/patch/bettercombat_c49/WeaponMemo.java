package bons.furious.patch.bettercombat_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.api.component.BetterCombatDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch bettercombat_weapon_attributes_memo (Better Combat, GPL-3.0 per mods.toml / All Rights Reserved
 * per its repository: injectors only, no Better Combat code carried; 1.21.1 tested build: Better Combat 2.4.0+1.21.1 for
 * NeoForge; both sides).
 *
 * What Better Combat does. WeaponRegistry.getAttributes(ItemStack), asked for the held stacks on every equipment read,
 * every attack and every tooltip, answers a stack that carries a weapon preset (the bettercombat:preset_id data component)
 * from its attribute containers when that preset is known; otherwise it looks the item's id up in the item registry (a
 * reverse lookup) and that id in its registrations map (a hash and an equals of the ResourceLocation).
 *
 * What the switch does. For a stack without the preset component (the case where the answer depends on the item alone),
 * the answer is remembered on the Item object as an immutable (generation, attributes) record, null answers included. The
 * generation is bumped at the head and at the return of WeaponRegistry.register (the only writer of the registrations map
 * outside the sync: put) and of decodeRegistry (the only other writer: it clears the map and fills it again from the
 * server's sync); a record of another generation is never used, it is recomputed by Better Combat's own method. A stack
 * with the preset component always runs Better Combat's method.
 *
 * Why the result is identical: the memo holds exactly what the original returned for this item at this generation; the
 * registrations map changes only through those two methods (bytecode scan of the 1.21.1 jar: no other class names the
 * field); an item's registry id is fixed once the registry is frozen, and a record made before the first register /
 * decodeRegistry call is dropped by that call's bump. Reading the generation before the original's map read means a record
 * stored while a write ran carries a generation the write's closing bump leaves behind.
 *
 * -Dbons_and_furious.bettercombatWeaponAttributesMemo=false switches it off at run time.
 * -Dbons_and_furious.bettercombatWeaponAttributesMemo.shadow=true: every hit also runs the original and compares the answer
 * (identity; SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class WeaponMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bettercombatWeaponAttributesMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.bettercombatWeaponAttributesMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile boolean announced;

    private WeaponMemo() {
    }

    /** One item's remembered answer. */
    public static final class Memo {
        final int generation;
        final WeaponAttributes attributes;

        Memo(int generation, WeaponAttributes attributes) {
            this.generation = generation;
            this.attributes = attributes;
        }
    }

    /** WeaponRegistry.register and decodeRegistry, HEAD and RETURN. */
    public static void bump() {
        GENERATION.incrementAndGet();
    }

    /** For the harness. */
    public static int generation() {
        return GENERATION.get();
    }

    /** In place of WeaponRegistry.getAttributes(ItemStack) (MixinExtras @WrapMethod). */
    public static WeaponAttributes attributes(ItemStack stack, Operation<WeaponAttributes> original) {
        if (!enabled || stack == null || stack.get(BetterCombatDataComponents.WEAPON_PRESET_ID) != null) return original.call(stack);
        Item item = stack.getItem();
        WeaponMemoHolder holder = (WeaponMemoHolder) item;
        int g = GENERATION.get();
        Memo m = holder.bons$bcMemo();
        if (m != null && m.generation == g) {
            if (SHADOW) {
                WeaponAttributes fresh = original.call(stack);
                SHADOW_CHECKS.incrementAndGet();
                if (fresh != m.attributes && GENERATION.get() == g) {
                    long n = SHADOW_MISMATCHES.incrementAndGet();
                    if (n <= 20) LOGGER.warn("Bons and Furious: bettercombat_weapon_attributes_memo shadow mismatch #{} for {}: remembered {} vs {}", n, item, m.attributes, fresh);
                }
            }
            return m.attributes;
        }
        WeaponAttributes a = original.call(stack);
        holder.bons$bcSetMemo(new Memo(g, a));
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: bettercombat_weapon_attributes_memo applies (Better Combat's weapon attributes are remembered per item){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return a;
    }
}
