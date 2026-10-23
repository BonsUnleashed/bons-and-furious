package bons.portprobe;

import bons.furious.patch.noise.NoiseCompatibility;
import bons.furious.patch.vanilla_zip_index.ZipListings;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/** Native production regression checks, excluded from the public mod. */
final class PrimitiveProof47 {
 static Map<String,Object> run() throws Exception {
  Map<String,Object> result=new LinkedHashMap<>();
  GuardAlternativesProof.main(new String[0]);
  result.put("guard_helper_checks",GuardAlternativesProof.checks);
  bons.furious.guard.Guards.load();
  var guardsField=bons.furious.guard.Guards.class.getDeclaredField("GUARD");guardsField.setAccessible(true);
  if(((Map<?,?>)guardsField.get(null)).size()!=256)throw new AssertionError("Incomplete production guard table");
  var auditsField=bons.furious.guard.Guards.class.getDeclaredField("AUDIT");auditsField.setAccessible(true);
  if(!((Set<?>)auditsField.get(null)).contains("awcapi_collision_direct|com/cerbon/cerbons_api/mixin/multipart_entities/ShapesMixin|*|"))
   throw new AssertionError("Class-shape audit with empty descriptor was lost");
  boolean noiseAllowed=NoiseCompatibility.allowed;
  if(!noiseAllowed)throw new AssertionError("Core noise comparison requires an active gradient patch");
  Random random=new Random(1471211);long samples=0;
  try {
   for(int seed=0;seed<16;seed++) {
    ImprovedNoise noise=new ImprovedNoise(RandomSource.create(seed*135794839L));
    for(int i=0;i<8192;i++) {
     double x=(random.nextDouble()-.5)*60000000,y=(random.nextDouble()-.5)*4096,z=(random.nextDouble()-.5)*60000000;
     double ys=i%4==0?0:random.nextDouble(),ym=i%3==0?0:random.nextDouble();
     NoiseCompatibility.allowed=false;double original=noise.noise(x,y,z,ys,ym);
     NoiseCompatibility.allowed=true;double optimized=noise.noise(x,y,z,ys,ym);
     if(Double.doubleToRawLongBits(original)!=Double.doubleToRawLongBits(optimized))throw new AssertionError("Noise mismatch at "+seed+":"+i);
     samples++;
    }
    boolean table=false;
    for(var field:noise.getClass().getDeclaredFields())if(field.getName().contains("gradientTable")) {field.setAccessible(true);table|=field.get(noise)!=null;}
    if(!table)throw new AssertionError("Gradient mixin did not execute");
   }
  } finally {NoiseCompatibility.allowed=noiseAllowed;}
  result.put("noise_exact_samples",samples);
  Path dir=Path.of("proof47");Files.createDirectories(dir);Path zip=dir.resolve("listing.zip");
  try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))) {
   for(String overlay:List.of("","overlay","nested/overlay"))for(String type:List.of("assets","data"))for(String ns:List.of("minecraft","test","other"))
    for(String path:List.of("a.txt","models/a.json","models/sub/z.json","models/sub/a.json","textures/block/a.png","empty/","models/INVALID.json")) {
     String name=(overlay.isEmpty()?"":overlay+"/")+type+"/"+ns+"/"+path;
     out.putNextEntry(new ZipEntry(name));if(!name.endsWith("/"))out.write(name.getBytes(StandardCharsets.UTF_8));out.closeEntry();
    }
  }
  var location=new PackLocationInfo("proof47",Component.literal("Proof"),PackSource.BUILT_IN,Optional.empty());
  long indexed=ZipListings.INDEXED.sum(),built=ZipListings.BUILDS.sum();int listings=0;
  boolean enabled=ZipListings.enabled;
  try {
   for(String overlay:List.of("","overlay","nested/overlay"))try(var access=new FilePackResources.SharedZipFileAccess(zip.toFile())) {
    var pack=new FilePackResources(location,access,overlay);
    for(int reopen=0;reopen<2;reopen++) {
     for(PackType type:PackType.values())for(String ns:List.of("minecraft","test","absent"))for(String path:List.of("","models","models/sub","textures","textures/block","empty","absent")) {
      ZipListings.enabled=false;var expected=list(pack,type,ns,path);
      ZipListings.enabled=true;var actual=list(pack,type,ns,path);
      if(!expected.equals(actual))throw new AssertionError("ZIP listing mismatch "+overlay+":"+type+":"+ns+":"+path);
      listings++;
     }
     pack.close(); // Reusing the pack must rebuild against the newly opened ZIP.
    }
   }
  } finally {ZipListings.enabled=enabled;}
  if(ZipListings.INDEXED.sum()-indexed!=listings || ZipListings.BUILDS.sum()-built!=6)throw new AssertionError("ZIP index did not execute/rebuild as expected");
  result.put("zip_exact_listings",listings);result.put("zip_reopened_indexes",6);return result;
 }
 static List<String> list(PackResources pack,PackType type,String namespace,String path) {
  List<String> result=new ArrayList<>();
  pack.listResources(type,namespace,path,(id,supplier)->{
   try(var in=supplier.get()){result.add(id+"="+new String(in.readAllBytes(),StandardCharsets.UTF_8));}
   catch(IOException e){throw new UncheckedIOException(e);}
  });
  return result;
 }
}
