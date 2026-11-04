package bons.furious.patch.mcreator_constant_tag_keys;

/**
 * Bons and Furious 1.0.49: the callers of the mcreator_constant_tag_keys layer in this group (each its own switch with its own
 * guard, so one updated mod steps aside alone). See ConstantTagKeys.
 *
 * skarriermobs_constant_tag_keys (Skarrier Mobs 1.1.2, COO's pin 1.0.6 an accepted alternative build; both sides):
 * MobsBurningInSunProcedure.execute (entity type tag skarrier_mobs:burns_in_daylight) and ResisteelToolsEffectProcedure.execute
 * (item tag skarrier_mobs:resisteel_tools), each run for every living entity (1.0.6: every tick; 1.1.2: every 20 / 10 ticks).
 *
 * cnc_constant_tag_keys (Critters n' Crawlers 2.2.5; both sides): SasquatchWoodKnockProcedure.execute tests 216 block states
 * per sasquatch per tick against BlockTags.create(new ResourceLocation("minecraft:logs")) (the first location constructor of the
 * method; the second, the knock sound's id, is left as it is).
 */
public final class TagCallers {
    public static final ConstantTagKeys.Caller SKARRIER = ConstantTagKeys.caller("skarriermobs_constant_tag_keys", "skarriermobsConstantTagKeys",
            "Skarrier Mobs' daylight-burn and Resisteel-tool tag tests reuse one tag key each");
    public static final ConstantTagKeys.Caller CNC = ConstantTagKeys.caller("cnc_constant_tag_keys", "cncConstantTagKeys",
            "Critters n' Crawlers' sasquatch log search reuses one tag key");

    private TagCallers() {
    }
}
