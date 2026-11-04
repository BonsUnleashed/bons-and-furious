package bons.furious.patch.arselemental_coo49;

import alexthw.ars_elemental.ConfigHandler;
import alexthw.ars_elemental.api.item.ISchoolFocus;
import com.hollingsworth.arsnouveau.api.spell.SpellSchools;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch arselemental_heal_boost_focus_only (Ars Elemental 0.6.7.8 / 0.6.8.0, both sides; heals happen on
 * the server).
 *
 * DamageEvents.handleHealing multiplies a heal by 1.5 when "Enable glyph empowering" is on OR the healed entity is a player
 * holding an earth focus. With the option on (Ars Elemental's default, and this pack's server config) every heal of every
 * living entity in the world is 1.5 times larger: mobs, bosses, pets, players without any focus. The earth focus's
 * tooltip and the option's text describe a bonus for the earth focus holder while empowering is on.
 *
 * Gameplay fix (off by default): the 1.5 bonus needs all three: empowering on, a player, an earth focus (in a hand or a
 * Curios slot, Ars Elemental's own lookup). Every other heal keeps its amount; with empowering off nobody gets the bonus.
 * The Frozen (heal cancelled) and Magic Fire (heal x1.25 and no invulnerability frames) parts of the listener are
 * unchanged. Runtime flag -Dbons_and_furious.arselementalHealBoostFocusOnly=false. Ars Elemental is LGPL-3.0; no code of
 * it is carried (the hook decides whether its own setAmount call runs).
 */
public final class HealBoostFix {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.arselementalHealBoostFocusOnly", "true"));
    private static volatile boolean announced;

    private HealBoostFix() {
    }

    /**
     * Called where Ars Elemental is about to apply its 1.5 bonus (it decided "empowering OR player with an earth focus"):
     * true when the bonus stays (switch off, or empowering AND player AND earth focus).
     */
    public static boolean keepBonus(LivingHealEvent event) {
        if (!enabled) return true;
        boolean keep = ConfigHandler.COMMON.EnableGlyphEmpowering.get() && event.getEntity() instanceof Player player
                && ISchoolFocus.hasFocus(player) == SpellSchools.ELEMENTAL_EARTH;
        if (!keep && !announced) announce();
        return keep;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: arselemental_heal_boost_focus_only: Ars Elemental's 1.5 heal bonus now needs glyph empowering and a player with an earth focus");
    }
}
