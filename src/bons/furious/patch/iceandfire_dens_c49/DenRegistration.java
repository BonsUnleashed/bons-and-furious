package bons.furious.patch.iceandfire_dens_c49;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.registries.RegisterEvent;

/**
 * iceandfire_structure_dens (since 1.0.49): while the switch is active, registers the dragon cave structure type and piece
 * type and adds the built-in data pack bons_and_furious.iceandfire_dens_c49.pack (required, on top: three structures and one
 * structure set). Off, nothing is registered, so the game's registries and data packs are exactly as without this group.
 */
@Mod.EventBusSubscriber(modid = "bons_and_furious", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DenRegistration {
    static final String ID = "iaf_dragon_den";
    static final String PACK = "bons_and_furious.iceandfire_dens_c49.pack";
    /** Its own logger: Dens is never initialised without Ice and Fire (its static fields name Ice and Fire's classes). */
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("Bons and Furious");

    private DenRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (!ModList.get().isLoaded("iceandfire")) return;     // before Dens / DenStructure are touched
        try {
            if (event.getRegistryKey().equals(Registries.f_256938_)) {
                if (!Dens.active()) return;
                event.register(Registries.f_256938_, helper -> helper.register(new ResourceLocation("bons_and_furious", ID), DenStructure.TYPE));
                Dens.structureTypeRegistered = true;
            } else if (event.getRegistryKey().equals(Registries.f_256786_)) {
                if (!Dens.active()) return;
                event.register(Registries.f_256786_, helper -> helper.register(new ResourceLocation("bons_and_furious", ID), DenPiece.TYPE));
                Dens.pieceTypeRegistered = true;
            }
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not register its structure ({}); Ice and Fire's dens are left as they are", Dens.KEY, t.toString());
        }
    }

    @SubscribeEvent
    public static void packs(AddPackFindersEvent event) {
        if (!ModList.get().isLoaded("iceandfire")) return;     // before Dens / DenStructure are touched
        if (event.getPackType() != PackType.SERVER_DATA) return;
        try {
            if (!Dens.active()) return;
            if (!Dens.structureTypeRegistered || !Dens.pieceTypeRegistered) {
                Dens.LOGGER.warn("Bons and Furious: {}: its structure types are not registered; Ice and Fire's dens are left as they are", Dens.KEY);
                return;
            }
            IModFileInfo self = ModList.get().getModFileById("bons_and_furious");
            if (self == null) return;
            Path root = self.getFile().findResource(PACK);
            if (!Files.isDirectory(root)) {
                Dens.LOGGER.warn("Bons and Furious: {}: its data pack {} is missing from the jar; no structure dens are placed", Dens.KEY, PACK);
                return;
            }
            Pack pack = Pack.m_245429_("bons_and_furious:iceandfire_structure_dens", Component.m_237113_("Bons and Furious: Ice and Fire structure dens"), true,
                    id -> new PathPackResources(id, root, true), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.f_10528_);
            if (pack == null) return;
            event.addRepositorySource(consumer -> consumer.accept(pack));
            Dens.packAdded = true;
            Dens.LOGGER.info("Bons and Furious: {} applies (Ice and Fire's dragon caves generate as whole structures)", Dens.KEY);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not add its data pack ({}); no structure dens are placed", Dens.KEY, t.toString());
        }
    }
}
