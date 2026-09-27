package agentcraft.consolidated.bons_pure_optimizations.team.creative.ambientsounds.condition;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/** Pure match memo: weak pattern lifetime, bounded identifiers, no biome/holder/world state. */
public final class AcBiomeMatchCache {
    private static final class Patterns extends LinkedHashMap<Pattern, Map<ResourceLocation, Boolean>> {
        Patterns() { super(128, 0.75f, true); }
        @Override protected boolean removeEldestEntry(Map.Entry<Pattern, Map<ResourceLocation, Boolean>> entry) {
            return size() > 128;
        }
    }

 private static final ThreadLocal<Map<Pattern,Map<ResourceLocation,Boolean>>> CACHE=ThreadLocal.withInitial(WeakHashMap::new);
 public static boolean matches(Pattern pattern,ResourceLocation location){
  var patterns=CACHE.get();var values=patterns.get(pattern);
  if(values==null){values=new LinkedHashMap<>();patterns.put(pattern,values);}
  Boolean result=values.get(location);if(result!=null)return result;
  boolean computed=pattern.matcher(location.toString()).matches();
  if(values.size()<256)values.put(location,computed);
  return computed;
 }
}
