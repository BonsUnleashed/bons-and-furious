package bons.furious.patch.legendarymonsters_c49;

import net.minecraft.util.RandomSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch legendary_monsters_annihilator_proc_chance (Legendary Monsters 2.2.3 for Minecraft 1.21.1; server;
 * since 1.0.49). Gameplay fix, off by default. Idea: Collections Of Optimizations (legendarymonsters.fixAnnihilatorProcChance),
 * idea text only. Our own logic; no Legendary Monsters code (All Rights Reserved).
 *
 * ForgeEvents.onLivingHurt (NeoForge's LivingIncomingDamageEvent) fires the Annihilator Leggings shockwave when
 * {@code attacker.getRandom().nextInt() * 100 < 25} (bytecode: nextInt(); bipush 100; imul; bipush 25; if_icmpge): nextInt()
 * is any 32-bit value, the product overflows, and the test holds for about half of all draws (every negative product and
 * the small positive ones), not the 25% the constant says.
 *
 * What changes (switch on): that one nextInt() call answers 0 when floorMod(raw, 100) < 25 and 1 otherwise, where raw is
 * the draw stock makes, so "x * 100 < 25" holds for 25 of every 100 values of raw mod 100 (25.000% up to 2^-32 bias) and the
 * random source is advanced by exactly one draw, as before. Nothing else in the handler changes.
 *
 * -Dbons_and_furious.legendaryMonstersAnnihilatorProcChance=false hands the raw draw on (stock) even with the switch on.
 */
public final class AnnihilatorChance {
    public static final String KEY = "legendary_monsters_annihilator_proc_chance";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendaryMonstersAnnihilatorProcChance", "true"));
    private static volatile boolean announced;

    private AnnihilatorChance() {
    }

    /** The draw that decides the shockwave: 0 (fires) for a quarter of the raw values, 1 (does not) otherwise. */
    public static int draw(RandomSource random, int raw) {
        if (!enabled) return raw;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Annihilator Leggings shockwave fires on 25% of hits, as its constant says)", KEY);
        }
        return Math.floorMod(raw, 100) < 25 ? 0 : 1;
    }
}
