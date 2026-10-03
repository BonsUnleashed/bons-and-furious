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

/** Development-only probe. Never included in the release JAR. */
@Mod("bons_port_probe")
public final class PortProbe {
    public PortProbe() {
        NeoForge.EVENT_BUS.addListener(this::started);
    }

    private void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("bons_and_furious.portProbe")) return;
        MinecraftServer server = event.getServer();
        Map<String,Object> result = new LinkedHashMap<>();
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
        } catch(Throwable t) {
            result.put("passed",false);result.put("error",t.toString());t.printStackTrace();
            System.out.println("BONS_PORT_PROBE_FAIL "+t);
        } finally {
            try { Files.writeString(Path.of("probe-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n"); }
            catch(Exception e){throw new RuntimeException(e);}
            server.halt(false);
        }
    }
}
