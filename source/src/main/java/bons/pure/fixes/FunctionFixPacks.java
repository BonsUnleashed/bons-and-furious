package bons.pure.fixes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;

/**
 * Built-in data packs that replace specific data-pack functions of other mods with cheaper equivalents
 * (bons_and_furious_packs/index.json lists them). A fix is registered only when its switch in
 * config/bons_and_furious.properties is on, its target mod is installed, and every function it replaces is
 * byte-identical (SHA-256) to the version it was written for; an updated target keeps its own functions and the
 * log gets one WARN line. The packs are required (always enabled) and sit at the top of the data pack stack.
 * SRG member names: Pack.readMetaAndCreate = readMetaAndCreate, Component.literal = literal, PackSource.BUILT_IN = BUILT_IN.
 */
@EventBusSubscriber(modid = "bons_and_furious", bus = EventBusSubscriber.Bus.MOD)
public final class FunctionFixPacks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODID = "bons_and_furious";
    private static final String ROOT = "bons_and_furious_packs";

    private FunctionFixPacks() {
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        IModFileInfo self = ModList.get().getModFileById(MODID);
        if (self == null) {
            return;
        }
        IModFile file = self.getFile();
        JsonArray fixes;
        try (InputStream in = Files.newInputStream(file.findResource(ROOT, "index.json"))) {
            fixes = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("fixes");
        } catch (Exception e) {
            LOGGER.warn("Bons and Furious: function fix index could not be read ({}); no function fixes applied", e.toString());
            return;
        }
        for (JsonElement element : fixes) {
            JsonObject fix = element.getAsJsonObject();
            String key = fix.get("key").getAsString();
            String target = fix.get("target").getAsString();
            try {
                if (disabled(key)) {
                    LOGGER.info("Bons and Furious: {} is disabled by config; {} functions are left unchanged", key, target);
                    continue;
                }
                IModFileInfo targetInfo = ModList.get().getModFileById(target);
                if (targetInfo == null) {
                    continue; // target mod not installed: nothing to replace
                }
                JsonObject originals = fix.getAsJsonObject("originals");
                String mismatch = mismatch(targetInfo.getFile(), originals);
                if (mismatch != null) {
                    LOGGER.warn("Bons and Furious: {} skipped for {} because the installed {} does not match the supported version; its functions are left unchanged",
                            key, target, mismatch);
                    continue;
                }
                Path root = file.findResource(ROOT, key);
                Map<ResourceLocation, byte[]> generated = new HashMap<>();
                if (key.equals("scorched_sandcrab_burrow_gate")) {
                    for (String sourcePath : originals.keySet()) {
                        String[] parts = sourcePath.split("/", 3);
                        byte[] source = Files.readAllBytes(targetInfo.getFile().findResource(sourcePath.split("/")));
                        generated.put(ResourceLocation.fromNamespaceAndPath(parts[1], parts[2]), InstalledFunctionPack.repair(sourcePath, source));
                    }
                }
                var location = new PackLocationInfo(MODID + ":" + key,
                        Component.literal("Bons and Furious: " + fix.get("title").getAsString()), PackSource.BUILT_IN, java.util.Optional.empty());
                Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                    public PackResources openPrimary(PackLocationInfo info) {
                        return generated.isEmpty() ? new PathPackResources(info, root) : new InstalledFunctionPack(info, root, generated);
                    }
                    public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) { return openPrimary(info); }
                };
                Pack pack = Pack.readMetaAndCreate(location, supplier, PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.TOP, true));
                if (pack == null) {
                    LOGGER.warn("Bons and Furious: {} skipped because its built-in pack could not be read", key);
                    continue;
                }
                event.addRepositorySource(consumer -> consumer.accept(pack));
                LOGGER.info("Bons and Furious: {} applied to {} ({} original functions verified)", key, target, originals.size());
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: {} skipped because it could not be registered ({})", key, t.toString());
            }
        }
    }

    /** Same semantics as the coremod guard: fail open when the configuration was never loaded. */
    static boolean disabled(String key) {
        if (!Boolean.getBoolean("bons_and_furious.config.loaded")) {
            return false;
        }
        return Boolean.getBoolean("bons_and_furious.disabled." + key);
    }

    /** Returns the first original path that is missing or differs, or null when all match. */
    static String mismatch(IModFile target, JsonObject originals) throws Exception {
        for (Map.Entry<String, JsonElement> e : originals.entrySet()) {
            Path p = target.findResource(e.getKey().split("/"));
            if (!Files.isRegularFile(p)) {
                return e.getKey() + " (missing)";
            }
            String got = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
            if (!got.equals(e.getValue().getAsString())) {
                return e.getKey();
            }
        }
        return null;
    }
}
