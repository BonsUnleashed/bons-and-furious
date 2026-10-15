// GENERATED from benssharks-1.3.0-forge-1.20.1.jar (sha1 36c6b054b98b48cfca8e5ec7d827727c784ad035) by the Ben's Sharks
// listener census of the former Bons Sharks Fix 1.0.0 (every listener classified from its bytecode). Do not edit by hand.
package bons.furious.patch.benssharks;

import java.util.Map;
import java.util.Set;

/**
 * benssharks_tick_listeners: Ben's Sharks 1.3.0's LivingTickEvent and LevelTickEvent listeners, classified. EMPTY handlers
 * have no observable effect and are dropped; SPECIAL ones get their own narrow hooks; every other handler is called only for
 * the Ben's entity classes its own instanceof / registry-id guard can accept (a superset of its guard: the handler still
 * decides). The switch's guard fingerprints every one of these handlers, so the table only runs against this build.
 */
public final class Table {
    private Table() {}

    /** Every LivingTickEvent listener class Ben's Sharks 1.3.0 registers (62). */
    public static final int LIVING_LISTENERS = 62;

    public static final Set<String> EMPTY = Set.of(
        "net.mcreator.sharks.procedures.AxodileSlideStopperProcedure",
        "net.mcreator.sharks.procedures.BREACHProcedure",
        "net.mcreator.sharks.procedures.BarracudaEatProcedure",
        "net.mcreator.sharks.procedures.BaskingSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.BlueSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.BonnetheadSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.BullSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.ChaseTargetProcedure",
        "net.mcreator.sharks.procedures.CookiecutterSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.EatDroppedItemProcedure",
        "net.mcreator.sharks.procedures.EatKrillProcedure",
        "net.mcreator.sharks.procedures.EntitySprintDataSaveProcedure",
        "net.mcreator.sharks.procedures.FollowIfTamedProcedure",
        "net.mcreator.sharks.procedures.GreatWhiteSharkEatDroppedItemProcedure",
        "net.mcreator.sharks.procedures.GreenlandSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.LandSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.MakoSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.MakoSprintProcedure",
        "net.mcreator.sharks.procedures.MegalodonOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.MovementRotationProcedure",
        "net.mcreator.sharks.procedures.RemoraOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.SardineSprintProcedure",
        "net.mcreator.sharks.procedures.SaturationProcedure",
        "net.mcreator.sharks.procedures.SeaLionEatDroppedItemProcedure",
        "net.mcreator.sharks.procedures.ShrakOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.TigerSharkOnInitialEntitySpawnProcedure",
        "net.mcreator.sharks.procedures.WhitetipSharkOnInitialEntitySpawnProcedure");

    public static final Map<String, String> SPECIAL = Map.of(
        "net.mcreator.sharks.procedures.BleedingImmunityProcedure", "bleed",
        "net.mcreator.sharks.procedures.DolphinEatSardineProcedure", "dolphin",
        "net.mcreator.sharks.procedures.IfWearingArmorProcedure", "armor",
        "net.mcreator.sharks.procedures.SharkBleedProcedure", "bleed");

    public static void fill(Map<String, Proc> m) {
        m.put("net.mcreator.sharks.init.EntityAnimationFactory", Proc.classes("net.mcreator.sharks.init.EntityAnimationFactory", net.mcreator.sharks.init.EntityAnimationFactory::onEntityTick, net.mcreator.sharks.entity.AxodileEntity.class, net.mcreator.sharks.entity.BarracudaEntity.class, net.mcreator.sharks.entity.BaskingSharkEntity.class, net.mcreator.sharks.entity.BlacktipReefSharkEntity.class, net.mcreator.sharks.entity.BlueSharkEntity.class, net.mcreator.sharks.entity.BonnetheadSharkEntity.class, net.mcreator.sharks.entity.BullSharkEntity.class, net.mcreator.sharks.entity.CookiecutterSharkEntity.class, net.mcreator.sharks.entity.GreaterAxodileEntity.class, net.mcreator.sharks.entity.GreenlandSharkEntity.class, net.mcreator.sharks.entity.KrillEntity.class, net.mcreator.sharks.entity.LandSharkEntity.class, net.mcreator.sharks.entity.LemonSharkEntity.class, net.mcreator.sharks.entity.LeopardSharkEntity.class, net.mcreator.sharks.entity.MakoSharkEntity.class, net.mcreator.sharks.entity.MegalodonEntity.class, net.mcreator.sharks.entity.NurseSharkEntity.class, net.mcreator.sharks.entity.PilotFishEntity.class, net.mcreator.sharks.entity.RemoraEntity.class, net.mcreator.sharks.entity.ShrakEntity.class, net.mcreator.sharks.entity.ThalassogerEntity.class, net.mcreator.sharks.entity.TigerSharkEntity.class, net.mcreator.sharks.entity.WhaleSharkEntity.class, net.mcreator.sharks.entity.WhitetipSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.AngelsharkEatProcedure", Proc.classes("net.mcreator.sharks.procedures.AngelsharkEatProcedure", net.mcreator.sharks.procedures.AngelsharkEatProcedure::onEntityTick, net.mcreator.sharks.entity.AngelsharkEntity.class, net.mcreator.sharks.entity.BarracudaEntity.class, net.mcreator.sharks.entity.SardineEntity.class));
        m.put("net.mcreator.sharks.procedures.BarracudaEatingProcedure", Proc.classes("net.mcreator.sharks.procedures.BarracudaEatingProcedure", net.mcreator.sharks.procedures.BarracudaEatingProcedure::onEntityTick, net.mcreator.sharks.entity.AxodileEntity.class, net.mcreator.sharks.entity.BarracudaEntity.class, net.mcreator.sharks.entity.BlacktipReefSharkEntity.class, net.mcreator.sharks.entity.BlueSharkEntity.class, net.mcreator.sharks.entity.BonnetheadSharkEntity.class, net.mcreator.sharks.entity.BullSharkEntity.class, net.mcreator.sharks.entity.GoblinSharkEntity.class, net.mcreator.sharks.entity.GreaterAxodileEntity.class, net.mcreator.sharks.entity.GreenlandSharkEntity.class, net.mcreator.sharks.entity.LemonSharkEntity.class, net.mcreator.sharks.entity.LeopardSharkEntity.class, net.mcreator.sharks.entity.MakoSharkEntity.class, net.mcreator.sharks.entity.NurseSharkEntity.class, net.mcreator.sharks.entity.SawsharkEntity.class, net.mcreator.sharks.entity.ShrakEntity.class, net.mcreator.sharks.entity.ThresherSharkEntity.class, net.mcreator.sharks.entity.TigerSharkEntity.class, net.mcreator.sharks.entity.WhitetipSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.BarracudaSprintProcedure", Proc.classes("net.mcreator.sharks.procedures.BarracudaSprintProcedure", net.mcreator.sharks.procedures.BarracudaSprintProcedure::onEntityTick, net.mcreator.sharks.entity.BarracudaEntity.class));
        m.put("net.mcreator.sharks.procedures.BaskingSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.BaskingSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.BaskingSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.BaskingSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.BlacktipReefSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.BlacktipReefSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.BlacktipReefSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.BlacktipReefSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.BonnetheadSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.BonnetheadSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.BonnetheadSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.BonnetheadSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.BullSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.BullSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.BullSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.BullSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.EatDroppedItemsProcedure", Proc.classes("net.mcreator.sharks.procedures.EatDroppedItemsProcedure", net.mcreator.sharks.procedures.EatDroppedItemsProcedure::onEntityTick, net.mcreator.sharks.entity.AxodileEntity.class, net.mcreator.sharks.entity.BarracudaEntity.class, net.mcreator.sharks.entity.BlacktipReefSharkEntity.class, net.mcreator.sharks.entity.BlueSharkEntity.class, net.mcreator.sharks.entity.BonnetheadSharkEntity.class, net.mcreator.sharks.entity.BullSharkEntity.class, net.mcreator.sharks.entity.GoblinSharkEntity.class, net.mcreator.sharks.entity.GreaterAxodileEntity.class, net.mcreator.sharks.entity.GreenlandSharkEntity.class, net.mcreator.sharks.entity.LemonSharkEntity.class, net.mcreator.sharks.entity.LeopardSharkEntity.class, net.mcreator.sharks.entity.MakoSharkEntity.class, net.mcreator.sharks.entity.NurseSharkEntity.class, net.mcreator.sharks.entity.SardineEntity.class, net.mcreator.sharks.entity.SawsharkEntity.class, net.mcreator.sharks.entity.ShrakEntity.class, net.mcreator.sharks.entity.ThresherSharkEntity.class, net.mcreator.sharks.entity.TigerSharkEntity.class, net.mcreator.sharks.entity.WhitetipSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.EatingKrillProcedure", Proc.classes("net.mcreator.sharks.procedures.EatingKrillProcedure", net.mcreator.sharks.procedures.EatingKrillProcedure::onEntityTick, net.mcreator.sharks.entity.BaskingSharkEntity.class, net.mcreator.sharks.entity.KrillEntity.class, net.mcreator.sharks.entity.MegamouthSharkEntity.class, net.mcreator.sharks.entity.WhaleSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.EntityTextureVariationsProcedure", Proc.classes("net.mcreator.sharks.procedures.EntityTextureVariationsProcedure", net.mcreator.sharks.procedures.EntityTextureVariationsProcedure::onEntityTick, net.mcreator.sharks.entity.BlacktipReefSharkEntity.class, net.mcreator.sharks.entity.BonnetheadSharkEntity.class, net.mcreator.sharks.entity.LandSharkEntity.class, net.mcreator.sharks.entity.LemonSharkEntity.class, net.mcreator.sharks.entity.LeopardSharkEntity.class, net.mcreator.sharks.entity.NurseSharkEntity.class, net.mcreator.sharks.entity.SeaLionEntity.class, net.mcreator.sharks.entity.SharkMinionEntity.class, net.mcreator.sharks.entity.ShrakEntity.class, net.mcreator.sharks.entity.WhaleSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.GlobalFishFlopperProcedure", Proc.ids("net.mcreator.sharks.procedures.GlobalFishFlopperProcedure", net.mcreator.sharks.procedures.GlobalFishFlopperProcedure::onEntityTick, net.mcreator.sharks.procedures.GlobalFishFlopperProcedure.class, "FLOPPING_MOBS"));
        m.put("net.mcreator.sharks.procedures.GlobalFrenzyFlopperProcedure", Proc.ids("net.mcreator.sharks.procedures.GlobalFrenzyFlopperProcedure", net.mcreator.sharks.procedures.GlobalFrenzyFlopperProcedure::onEntityTick, net.mcreator.sharks.procedures.GlobalFrenzyFlopperProcedure.class, "FRENZY_MOBS"));
        m.put("net.mcreator.sharks.procedures.GlobalSharkFloppingProcedure", Proc.ids("net.mcreator.sharks.procedures.GlobalSharkFloppingProcedure", net.mcreator.sharks.procedures.GlobalSharkFloppingProcedure::onEntityTick, net.mcreator.sharks.procedures.GlobalSharkFloppingProcedure.class, "SHARK_MOBS"));
        m.put("net.mcreator.sharks.procedures.GoblinSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.GoblinSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.GoblinSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.GoblinSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.GreenlandSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.GreenlandSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.GreenlandSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.GreenlandSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.LemonSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.LemonSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.LemonSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.LemonSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.MakoBreachingProcedure", Proc.classes("net.mcreator.sharks.procedures.MakoBreachingProcedure", net.mcreator.sharks.procedures.MakoBreachingProcedure::onEntityTick, net.mcreator.sharks.entity.MakoSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.MakoSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.MakoSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.MakoSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.MakoSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.MegalodonOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.MegalodonOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.MegalodonOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.MegalodonEntity.class));
        m.put("net.mcreator.sharks.procedures.MegamouthSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.MegamouthSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.MegamouthSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.MegamouthSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.MobDeathParticleProcedure", Proc.all("net.mcreator.sharks.procedures.MobDeathParticleProcedure", net.mcreator.sharks.procedures.MobDeathParticleProcedure::onEntityTick));
        m.put("net.mcreator.sharks.procedures.RightClickSpeedProcedure", Proc.classes("net.mcreator.sharks.procedures.RightClickSpeedProcedure", net.mcreator.sharks.procedures.RightClickSpeedProcedure::onEntityTick, net.mcreator.sharks.entity.NurseSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.SHARKMINIONEATDROPPEDITEMProcedure", Proc.classes("net.mcreator.sharks.procedures.SHARKMINIONEATDROPPEDITEMProcedure", net.mcreator.sharks.procedures.SHARKMINIONEATDROPPEDITEMProcedure::onEntityTick, net.mcreator.sharks.entity.SharkMinionEntity.class));
        m.put("net.mcreator.sharks.procedures.SeaLionEatDroppedItemsProcedure", Proc.classes("net.mcreator.sharks.procedures.SeaLionEatDroppedItemsProcedure", net.mcreator.sharks.procedures.SeaLionEatDroppedItemsProcedure::onEntityTick, net.mcreator.sharks.entity.SeaLionEntity.class));
        m.put("net.mcreator.sharks.procedures.SittingSneakingSaveProcedure", Proc.classes("net.mcreator.sharks.procedures.SittingSneakingSaveProcedure", net.mcreator.sharks.procedures.SittingSneakingSaveProcedure::onEntityTick, net.mcreator.sharks.entity.LandSharkEntity.class, net.mcreator.sharks.entity.SharkMinionEntity.class));
        m.put("net.mcreator.sharks.procedures.StopFloatingProcedure", Proc.classes("net.mcreator.sharks.procedures.StopFloatingProcedure", net.mcreator.sharks.procedures.StopFloatingProcedure::onEntityTick, net.mcreator.sharks.entity.MegalodonEntity.class));
        m.put("net.mcreator.sharks.procedures.StopRidingBoatProcedure", Proc.classes("net.mcreator.sharks.procedures.StopRidingBoatProcedure", net.mcreator.sharks.procedures.StopRidingBoatProcedure::onEntityTick, net.mcreator.sharks.entity.AngelsharkEntity.class, net.mcreator.sharks.entity.AxodileEntity.class, net.mcreator.sharks.entity.BarracudaEntity.class, net.mcreator.sharks.entity.BaskingSharkEntity.class, net.mcreator.sharks.entity.BlacktipReefSharkEntity.class, net.mcreator.sharks.entity.BlueSharkEntity.class, net.mcreator.sharks.entity.BonnetheadSharkEntity.class, net.mcreator.sharks.entity.BullSharkEntity.class, net.mcreator.sharks.entity.CookiecutterSharkEntity.class, net.mcreator.sharks.entity.GoblinSharkEntity.class, net.mcreator.sharks.entity.GreaterAxodileEntity.class, net.mcreator.sharks.entity.GreenlandSharkEntity.class, net.mcreator.sharks.entity.KrillEntity.class, net.mcreator.sharks.entity.LemonSharkEntity.class, net.mcreator.sharks.entity.LeopardSharkEntity.class, net.mcreator.sharks.entity.MakoSharkEntity.class, net.mcreator.sharks.entity.MegalodonEntity.class, net.mcreator.sharks.entity.MegamouthSharkEntity.class, net.mcreator.sharks.entity.NurseSharkEntity.class, net.mcreator.sharks.entity.PilotFishEntity.class, net.mcreator.sharks.entity.RemoraEntity.class, net.mcreator.sharks.entity.SawsharkEntity.class, net.mcreator.sharks.entity.ShrakEntity.class, net.mcreator.sharks.entity.ThresherSharkEntity.class, net.mcreator.sharks.entity.TigerSharkEntity.class, net.mcreator.sharks.entity.WhaleSharkEntity.class, net.mcreator.sharks.entity.WhitetipSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.TigerSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.TigerSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.TigerSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.TigerSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.WhaleSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.WhaleSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.WhaleSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.WhaleSharkEntity.class));
        m.put("net.mcreator.sharks.procedures.WhitetipSharkOnEntityTickUpdateProcedure", Proc.classes("net.mcreator.sharks.procedures.WhitetipSharkOnEntityTickUpdateProcedure", net.mcreator.sharks.procedures.WhitetipSharkOnEntityTickUpdateProcedure::onEntityTick, net.mcreator.sharks.entity.WhitetipSharkEntity.class));
    }

    /** ST1: instanceof list of SmoothTurnsProcedure.onLevelTick (isInWater checked: True). */
    public static final Class<?>[] ST1 = {net.mcreator.sharks.entity.ShrakEntity.class, net.mcreator.sharks.entity.MakoSharkEntity.class, net.mcreator.sharks.entity.TigerSharkEntity.class, net.mcreator.sharks.entity.BlueSharkEntity.class, net.mcreator.sharks.entity.BonnetheadSharkEntity.class, net.mcreator.sharks.entity.BlacktipReefSharkEntity.class, net.mcreator.sharks.entity.BaskingSharkEntity.class, net.mcreator.sharks.entity.BullSharkEntity.class, net.mcreator.sharks.entity.MegalodonEntity.class, net.mcreator.sharks.entity.LemonSharkEntity.class, net.mcreator.sharks.entity.WhaleSharkEntity.class, net.mcreator.sharks.entity.GreenlandSharkEntity.class, net.mcreator.sharks.entity.WhitetipSharkEntity.class, net.mcreator.sharks.entity.ThresherSharkEntity.class, net.mcreator.sharks.entity.GoblinSharkEntity.class, net.mcreator.sharks.entity.MegamouthSharkEntity.class};
    public static final boolean ST1_WATER = true;
    /** ST2: instanceof list of SmoothTurns2Procedure.onLevelTick (isInWater checked: True). */
    public static final Class<?>[] ST2 = {net.mcreator.sharks.entity.NurseSharkEntity.class, net.mcreator.sharks.entity.LeopardSharkEntity.class, net.mcreator.sharks.entity.SawsharkEntity.class};
    public static final boolean ST2_WATER = true;
    /** ST3: instanceof list of SmoothTurns3Procedure.onLevelTick (isInWater checked: False). */
    public static final Class<?>[] ST3 = {net.mcreator.sharks.entity.AxodileEntity.class};
    public static final boolean ST3_WATER = false;
    /** ST5: instanceof list of SmoothTurns5Procedure.onLevelTick (isInWater checked: False). */
    public static final Class<?>[] ST5 = {net.mcreator.sharks.entity.GreaterAxodileEntity.class};
    public static final boolean ST5_WATER = false;
    public static final Map<String, String> LEVEL_KEYS = Map.of(
        "net.mcreator.sharks.procedures.SmoothTurnsProcedure", "ST1",
        "net.mcreator.sharks.procedures.SmoothTurns2Procedure", "ST2",
        "net.mcreator.sharks.procedures.SmoothTurns3Procedure", "ST3",
        "net.mcreator.sharks.procedures.SmoothTurns5Procedure", "ST5",
        "net.mcreator.sharks.procedures.SmoothTurns6Procedure", "ST6",
        "net.mcreator.sharks.procedures.SeekingHandlerProcedure", "SEEK");
}
