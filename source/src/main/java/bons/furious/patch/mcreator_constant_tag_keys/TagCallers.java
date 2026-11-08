package bons.furious.patch.mcreator_constant_tag_keys;

/**
 * Bons and Furious 1.0.49: the callers of the mcreator_constant_tag_keys layer in this group (each its own switch with its own
 * guard, so one updated mod steps aside alone). See ConstantTagKeys.
 *
 * cnc_constant_tag_keys (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1; both sides): SasquatchWoodKnockProcedure.execute
 * tests 216 block states per sasquatch per tick against BlockTags.create(ResourceLocation.parse("minecraft:logs")) (the first
 * location factory call of the method; the second, the knock sound's id, is left as it is).
 */
public final class TagCallers {
    public static final ConstantTagKeys.Caller CNC = ConstantTagKeys.caller("cnc_constant_tag_keys", "cncConstantTagKeys",
            "Critters n' Crawlers' sasquatch log search reuses one tag key");

    private TagCallers() {
    }
}
