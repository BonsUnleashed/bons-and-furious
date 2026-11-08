package bons.furious.mixin.mcreator_pv_c49;

import org.spongepowered.asm.mixin.Mixin;

/**
 * mcreator_shared_default_player_variables (since 1.0.49): carrier for goblins_tyranny-1.2.4-neoforge-1.21.1.jar (common
 * classes: the 13 classes with accepted read sites of goblinstyranny.network.GoblinsTyrannyModVariables$PlayerVariables,
 * 19 sites, every one "Optional.of(GoblinsTyrannyModVariables.get(entity)).orElse(new PlayerVariables()).Charge" or
 * ".ChargingJump"). The mixin itself is empty: before it is applied to a class, the group's plugin
 * (bons.furious.patch.mcreator_pv_c49.SharedDefaultsPlugin) re-checks every site of that class and rewrites only the accepted
 * ones (bons.furious.patch.mcreator_pv_c49.PvRewrite). Our own logic only; the mod's code stays in its jar.
 */
@Mixin(targets = {
        "goblinstyranny.procedures.Cooldown0Procedure",
        "goblinstyranny.procedures.Cooldown10Procedure",
        "goblinstyranny.procedures.Cooldown1Procedure",
        "goblinstyranny.procedures.Cooldown2Procedure",
        "goblinstyranny.procedures.Cooldown3Procedure",
        "goblinstyranny.procedures.Cooldown4Procedure",
        "goblinstyranny.procedures.Cooldown5Procedure",
        "goblinstyranny.procedures.Cooldown6Procedure",
        "goblinstyranny.procedures.Cooldown7Procedure",
        "goblinstyranny.procedures.Cooldown8Procedure",
        "goblinstyranny.procedures.Cooldown9Procedure",
        "goblinstyranny.procedures.JumpBoostOnKeyPressedProcedure",
        "goblinstyranny.procedures.JumpBoostOnKeyReleasedProcedure"
}, remap = false)
public abstract class GoblinsTyrannyPvCarrier {
}
