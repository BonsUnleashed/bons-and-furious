package bons.furious.patch.mahoutsukai_coo49;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.extensions.IForgeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import stepsword.mahoutsukai.util.EffectUtil;

/**
 * Bons and Furious switch mahoutsukai_projector_enchant_precheck (Mahou Tsukai 1.20.1-v1.34.81, All Rights Reserved;
 * client; exact result, off by default since the 1.0.49 speed gate: every armour piece with NBT pays the test, 0.92-0.95
 * of the shipped speed per entity). Ours; no Mahou Tsukai code is carried. Idea: Collections Of Optimizations
 * (mahoutsukai.leanProjectorEnchantScan), idea text only; COO skips whole entities whose armour has no NBT (and says items
 * that report enchantments without NBT are missed), this answers per stack only where the answer is proven.
 *
 * RenderProjectorEnchant.renderProjectorEnchantment runs for every living entity on every frame (RenderLivingEvent.Pre) and
 * asks EffectUtil.hasEnchantment(stack, PROJECTOR) for each of its armour stacks: EnchantmentHelper.getItemEnchantmentLevel
 * (Forge: stack.getEnchantmentLevel(ench)) -> IForgeItemStack.getEnchantmentLevel (getItem().getEnchantmentLevel(stack,
 * ench)) -> IForgeItem.getEnchantmentLevel (EnchantmentHelper.getTagEnchantmentLevel) -> isEmpty, the enchantment's registry
 * key and getEnchantmentTags(), which for a stack without a tag is a NEW empty ListTag, so the loop finds nothing: 0, false.
 *
 * untaggedDefault(stack) answers "false" without that chain exactly when that chain is the one just described for this
 * stack: (1) the stack has no tag (getTag() == null: getEnchantmentTags() reads the same field); (2) the stack's item class
 * does not change getEnchantmentLevel(ItemStack, Enchantment): no class of its hierarchy declares it and no interface other
 * than Forge's IForgeItem gives it a default (reflection, once per class, ClassValue); (3) the chain's methods are as
 * shipped after every other mod's mixins: EffectUtil.hasEnchantment / getEnchantmentLevel, EnchantmentHelper's
 * getItemEnchantmentLevel / getTagEnchantmentLevel, ItemStack's getTag / getEnchantmentTags / isEmpty / getItem,
 * IForgeItemStack.getEnchantmentLevel and IForgeItem.getEnchantmentLevel (MahouProjectorProbe, five probe mixins at
 * priority 3000 compared with the classes as shipped). Placebo's enchantment-level hook on IForgeItemStack, Immersive
 * Armors' and the Ice and Fire patcher's hooks on getItemEnchantmentLevel make (3) fail, and the stock call then runs for
 * every stack. In any other case the original call runs.
 *
 * Runtime flag -Dbons_and_furious.mahoutsukaiProjectorEnchantPrecheck=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.mahoutsukaiProjectorEnchantPrecheck.shadow=true always makes the call and checks that it answered false
 * wherever the precheck would have (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class MahouProjector {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "mahoutsukai_projector_enchant_precheck";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mahoutsukaiProjectorEnchantPrecheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mahoutsukaiProjectorEnchantPrecheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** For probes and harnesses (a plain field): calls answered without the chain. */
    public static long answered;
    private static volatile boolean announced, warned;

    /** Per item class: does getEnchantmentLevel(ItemStack, Enchantment) resolve to IForgeItem's default? */
    static final ClassValue<Boolean> DEFAULT_LEVEL = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return defaultLevel(type);
        }
    };

    private MahouProjector() {
    }

    /**
     * MahouProjectorPrecheckMixin's redirect of the EffectUtil.hasEnchantment call. Cheapest test first: while a probe has not
     * proven the chain (or refused it), the stock call runs after one field read.
     */
    public static boolean hasEnchantment(ItemStack stack, Enchantment ench) {
        if (MahouProjectorProbe.bits != MahouProjectorProbe.ALL) {
            if (!warned && MahouProjectorProbe.refused != 0) standAside();   // a probe still pending is no refusal
            return EffectUtil.hasEnchantment(stack, ench);
        }
        if (!enabled || !untaggedDefault(stack)) return EffectUtil.hasEnchantment(stack, ench);
        if (SHADOW) {
            boolean asked = EffectUtil.hasEnchantment(stack, ench);
            SHADOW_CHECKS.incrementAndGet();
            if (asked) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: {} has no tag but reports the projector enchantment", KEY, n, stack);
            }
            return asked;
        }
        answered++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Mahou Tsukai's projector renderer no longer walks the enchantments of armour without NBT)", KEY);
        }
        return false;
    }

    private static void standAside() {
        warned = true;
        LOGGER.warn("Bons and Furious: {} stands aside ({}); the projector renderer asks every stack as shipped", KEY, MahouProjectorProbe.why());
    }

    /** True when the chain behind EffectUtil.hasEnchantment certainly answers false for this stack (see the class comment). */
    public static boolean untaggedDefault(ItemStack stack) {
        if (stack == null || stack.m_41783_() != null || MahouProjectorProbe.bits != MahouProjectorProbe.ALL) return false;
        Item item = stack.m_41720_();
        return item != null && DEFAULT_LEVEL.get(item.getClass());
    }

    /** Reflection: no class of the hierarchy declares getEnchantmentLevel(ItemStack, Enchantment), no interface but IForgeItem gives a default. */
    static boolean defaultLevel(Class<?> type) {
        try {
            Set<Class<?>> interfaces = new HashSet<>();
            ArrayDeque<Class<?>> todo = new ArrayDeque<>();
            for (Class<?> k = type; k != null; k = k.getSuperclass()) {
                if (declares(k)) return false;
                for (Class<?> i : k.getInterfaces()) todo.add(i);
            }
            boolean forge = false;
            while (!todo.isEmpty()) {
                Class<?> i = todo.poll();
                if (!interfaces.add(i)) continue;
                if (i == IForgeItem.class) {
                    forge = true;
                } else if (declares(i)) {
                    return false;
                }
                for (Class<?> j : i.getInterfaces()) todo.add(j);
            }
            return forge;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean declares(Class<?> k) {
        for (Method m : k.getDeclaredMethods()) {
            if (!m.getName().equals("getEnchantmentLevel") || Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == ItemStack.class && p[1] == Enchantment.class) return true;
        }
        return false;
    }
}
