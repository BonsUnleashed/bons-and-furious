package agentcraft.pure;
import java.util.*;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
/** Per-selection storage; no references to entities, positions, worlds or configs. */
public final class AcFootstepSet extends AbstractSet<Integer> {
 private IntOpenHashSet keys;
 public int size(){return keys==null?0:keys.size();}
 public boolean add(Integer key){return addInt(key.intValue());}
 public boolean addInt(int key){if(keys==null)keys=new IntOpenHashSet(16);return keys.add(key);}
 public Iterator<Integer> iterator(){return keys==null?Collections.emptyIterator():keys.iterator();}
 public static boolean addPair(Set<Integer> set,Object type,Object pos) {
  int hash=31*(31+Objects.hashCode(type))+Objects.hashCode(pos);
  return set instanceof AcFootstepSet own?own.addInt(hash):set.add(Integer.valueOf(hash));
 }
}
