package com.tacz.guns.entity.sync.core;
import java.util.*;

/** Keep collection order, mutable result lists and the original predicate/send phases. */
public final class AcCollections {
    private AcCollections() {}
    public static List<DataEntry<?,?>> dirty(DataHolder holder) {
        List<DataEntry<?,?>> result=new ArrayList<>();
        for(DataEntry<?,?> entry:holder.dataMap.values())
            if(entry.isDirty() && entry.getKey().syncMode()!=SyncedDataKey.SyncMode.NONE) result.add(entry);
        return result;
    }
    public static List<DataEntry<?,?>> all(DataHolder holder) {
        List<DataEntry<?,?>> result=new ArrayList<>();
        for(DataEntry<?,?> entry:holder.dataMap.values())
            if(entry.getKey().syncMode()!=SyncedDataKey.SyncMode.NONE) result.add(entry);
        return result;
    }
    public static List<DataEntry<?,?>> self(List<DataEntry<?,?>> entries) {
        List<DataEntry<?,?>> result=new ArrayList<>();
        for(DataEntry<?,?> entry:entries) if(entry.getKey().syncMode().isSelf()) result.add(entry);
        return result;
    }
    public static List<DataEntry<?,?>> tracking(List<DataEntry<?,?>> entries) {
        List<DataEntry<?,?>> result=new ArrayList<>();
        for(DataEntry<?,?> entry:entries) if(entry.getKey().syncMode().isTracking()) result.add(entry);
        return result;
    }
}
