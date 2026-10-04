package bons.portprobe;

import bons.furious.guard.Guards;
import bons.pure.config.PureConfig;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only probe. Never included in the release JAR.
 *
 * <p>1.0.29 port addition: with {@code -Dbons_and_furious.probeSoakTicks=N} the server keeps running N ticks after the
 * terrain hashes, runs the scheduled commands of {@code -Dbons_and_furious.probeCommands=<file>} ("tick command" per
 * line, relative to the game directory) and then harvests every shadow helper's counters (ShadowHarvest). Without the
 * property the probe behaves exactly as in the 1.0.27 port.
 */
@Mod("bons_port_probe")
public final class PortProbe {
    private MinecraftServer server;
    private Map<String, Object> pending;
    private int soakTotal, soakDone;
    private final NavigableMap<Integer, List<String>> commands = new TreeMap<>();
    private final List<Map<String, Object>> commandLog = new ArrayList<>();

    public PortProbe() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("bons_and_furious.portProbe")) return;
        server = event.getServer();
        Map<String,Object> result = new LinkedHashMap<>();
        boolean ok = initial(server, result);
        int soak = Integer.getInteger("bons_and_furious.probeSoakTicks", 0);
        if (!ok || soak <= 0) { finish(result); return; }
        try { loadCommands(); }
        catch (Exception e) { result.put("passed", false); result.put("error", "probe commands: " + e); finish(result); return; }
        soakTotal = soak;
        pending = result;
        System.out.println("BONS_PORT_PROBE_SOAK " + soak + " ticks, " + commands.values().stream().mapToInt(List::size).sum() + " commands");
    }

    private boolean initial(MinecraftServer server, Map<String,Object> result) {
        try {
            if(net.neoforged.fml.loading.FMLLoader.isProduction() && net.neoforged.fml.ModList.get().isLoaded("radium")) {
                Class.forName("me.jellysquid.mods.lithium.common.entity.EntityClassGroup",true,getClass().getClassLoader());
                Class.forName("me.jellysquid.mods.lithium.common.entity.EntityClassGroup$NoDragonClassGroup",true,getClass().getClassLoader());
                result.put("radium_production_collision_groups",true);
            }
            var targets=ProbeChecks.forceTargets(false);
            result.put("target_checks",targets);
            if(!Boolean.TRUE.equals(targets.get("passed")))throw new AssertionError(targets.get("failures"));
            Map<String,Object> decisions = new TreeMap<>();
            try(var in=PortProbe.class.getResourceAsStream("/probe-server-keys.txt")) {
                for (String key:new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).split("\\R"))
                    if(!key.isBlank()) decisions.put(key, Guards.decide(key).toString());
            }
            result.put("decisions",decisions);
            Map<String,Object> dimensions = new LinkedHashMap<>();
            Map<BlockState,byte[]> stateNames=new IdentityHashMap<>();
            for (var dimension:List.of(Level.OVERWORLD,Level.NETHER,Level.END)) {
                var level=Objects.requireNonNull(server.getLevel(dimension));
                MessageDigest blocks=MessageDigest.getInstance("SHA-256");
                MessageDigest biomes=MessageDigest.getInstance("SHA-256");
                BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
                long count=0;
                // Finish neighbouring decoration before reading any interior block. Features can cross chunk borders.
                for(int x=18;x<26;x++) for(int z=-26;z<-18;z++) level.getChunk(x,z);
                try(var dump=new GZIPOutputStream(Files.newOutputStream(Path.of(dimension.location().getPath()+"-blocks.txt.gz")))) {
                for (int cx=20;cx<24;cx++) for(int cz=-24;cz<-20;cz++) {
                    var chunk=level.getChunk(cx,cz,Boolean.getBoolean("bons_and_furious.probeNoiseOnly")?ChunkStatus.NOISE:ChunkStatus.FULL);
                    for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++) {
                        for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
                            BlockState state=chunk.getBlockState(pos.set((cx<<4)+x,y,(cz<<4)+z));
                            // Numeric IDs are stable within this identical registry, but names make receipts portable.
                            byte[] canonical=stateNames.computeIfAbsent(state, s -> {
                                Map<String,String> properties=new TreeMap<>();
                                s.getValues().forEach((property,value)->properties.put(property.getName(),value.toString()));
                                return (BuiltInRegistries.BLOCK.getKey(s.getBlock())+properties.toString()+"\n").getBytes(StandardCharsets.UTF_8);
                            });
                            blocks.update(canonical);dump.write(canonical);
                            count++;
                            if((x&3)==0 && (z&3)==0 && (y&3)==0) {
                                var biome=chunk.getNoiseBiome((cx<<2)+(x>>2),y>>2,(cz<<2)+(z>>2));
                                biomes.update((biome.unwrapKey().orElseThrow().location()+"\n").getBytes(StandardCharsets.UTF_8));
                            }
                        }
                    }
                }
                }
                dimensions.put(dimension.location().toString(),Map.of("blocks",count,"block_sha256",HexFormat.of().formatHex(blocks.digest()),"biome_sha256",HexFormat.of().formatHex(biomes.digest())));
            }
            result.put("dimensions",dimensions);
            result.put("density_passes",bons.pure.terrain.FinalDensityReuse.PASSES.sum());
            result.put("density_reused",bons.pure.terrain.FinalDensityReuse.REUSED.sum());
            result.put("surface_hits",bons.pure.terrain.SurfaceEstimateShare.HITS.sum());
            result.put("surface_misses",bons.pure.terrain.SurfaceEstimateShare.MISSES.sum());
            result.put("passed",true);
            System.out.println("BONS_PORT_PROBE_PASS "+dimensions);
            return true;
        } catch(Throwable t) {
            result.put("passed",false);result.put("error",t.toString());t.printStackTrace();
            System.out.println("BONS_PORT_PROBE_FAIL "+t);
            return false;
        }
    }

    private void loadCommands() throws Exception {
        String file = System.getProperty("bons_and_furious.probeCommands");
        if (file == null || file.isBlank()) return;
        for (String line : Files.readAllLines(Path.of(file), StandardCharsets.UTF_8)) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int space = line.indexOf(' ');
            commands.computeIfAbsent(Integer.parseInt(line.substring(0, space)), k -> new ArrayList<>()).add(line.substring(space + 1).strip());
        }
    }

    private void tick(ServerTickEvent.Post event) {
        if (pending == null) return;
        int t = soakDone++;
        for (String command : commands.getOrDefault(t, List.of())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tick", t);
            row.put("command", command);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                row.put("ok", true);
            } catch (Throwable e) {
                row.put("ok", false);
                row.put("error", e.toString());
            }
            commandLog.add(row);
        }
        if (t < soakTotal) return;
        Map<String, Object> result = pending;
        pending = null;
        result.put("soak_ticks", soakDone);
        result.put("commands", commandLog);
        Map<String, Object> shadow = ShadowHarvest.harvest(false);
        result.put("shadow", shadow);
        if (!ShadowHarvest.clean(shadow)) result.put("passed", false);
        finish(result);
    }

    private void finish(Map<String,Object> result) {
        try { Files.writeString(Path.of("probe-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n"); }
        catch(Exception e){throw new RuntimeException(e);}
        finally { server.halt(false); }
    }
}
