package bons.furious.patch.mcreator_pv_c49;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * mcreator_shared_default_player_variables (since 1.0.49): which mod and which PlayerVariables class each carrier mixin
 * serves. On Minecraft 1.21.1 only Goblin's Tyranny 1.2.4 of the eleven MCreator mods the Forge census covers both has a
 * NeoForge build and still builds a blank PlayerVariables at its reads; keep the carriers and this table equal.
 */
public final class PvTargets {
    /** One carrier: its mixin class, the mod id that ships its targets and the PlayerVariables class (internal name). */
    public record Target(String mixin, String modId, String pv) {}

    static final String PKG = "bons.furious.mixin.mcreator_pv_c49.";
    public static final Map<String, Target> BY_MIXIN = new LinkedHashMap<>();

    static {
        add("GoblinsTyrannyPvCarrier", "goblins_tyranny", "goblinstyranny/network/GoblinsTyrannyModVariables$PlayerVariables");
    }

    private PvTargets() {
    }

    private static void add(String mixin, String modId, String pv) {
        BY_MIXIN.put(PKG + mixin, new Target(PKG + mixin, modId, pv));
    }
}
