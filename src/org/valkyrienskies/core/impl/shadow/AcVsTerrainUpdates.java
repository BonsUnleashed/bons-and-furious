package org.valkyrienskies.core.impl.shadow;

import java.util.ArrayList;
import java.util.Map;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.valkyrienskies.core.util.datastructures.BlockPos2IntOpenHashMap;
import org.valkyrienskies.core.util.datastructures.BlockPos2ObjectOpenHashMap;

/** Physics-thread-owned snapshot ledger. A delta cannot initialize world terrain.
 *
 * The native baker starts an absent chunk from air for sparse updates. That is
 * correct for newly assembled ships, but loses all unchanged world blocks when
 * a ground delta precedes the chunk's initial/reload snapshot. Such a partial
 * chunk defeats the native missing-terrain freeze and lets ships fall through.
 *
 * Track submitted full/air snapshots, not native completion: FN already queues
 * subsequent deltas behind their in-flight bake. Before the first snapshot,
 * deltas are redundant: the authoritative snapshot includes those changes.
 * Delete invalidates readiness immediately, including while older work drains.
 * This state is transient, scoped to one pipeline, and never changes ship data.
 */
public final class AcVsTerrainUpdates {
    private final Long2ObjectMap<BlockPos2IntOpenHashMap> loaded=new Long2ObjectOpenHashMap<>();

    public void filter(Long2ObjectMap<BlockPos2ObjectOpenHashMap<Ip>> updates, Map<String,Long> grounds) {
        // Dimensions can disappear; do not retain their coordinate sets.
        var ids=loaded.keySet().iterator();
        while(ids.hasNext())if(!grounds.containsValue(ids.nextLong()))ids.remove();
        for(var entry:updates.long2ObjectEntrySet()) {
            long id=entry.getLongKey();
            if(!grounds.containsValue(id))continue;
            var known=loaded.computeIfAbsent(id,unused->new BlockPos2IntOpenHashMap());
            var changes=entry.getValue();
            Object[] values=changes.getValues();
            ArrayList<Ip> rejected=null;
            for(int i=0;i<=changes.getN();i++) {
                int x=changes.getKeys()[i*3],y=changes.getKeys()[i*3+1],z=changes.getKeys()[i*3+2];
                if(i==changes.getN()? !changes.getContainsNullKey() : x==0&&y==0&&z==0)continue;
                Ip update=(Ip)values[i];
                if(update instanceof Ir) {
                    if(!known.contains(x,y,z)) {
                        if(rejected==null)rejected=new ArrayList<>();
                        rejected.add(update);
                    }
                }else if(update instanceof In || update instanceof Io)known.put(x,y,z,1);
                else if(update instanceof Im)known.remove(x,y,z);
            }
            // Removal shifts open-addressed entries; defer it until after walking.
            if(rejected!=null)for(var update:rejected)changes.remove(update.a(),update.b(),update.c());
        }
    }
}
