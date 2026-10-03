package agentcraft.pure;
import net.minecraft.nbt.*;
/** Only exact scalar equality is optimized. Compound/list/array/custom tags fall back. */
public final class UnchangedMerge {
    private UnchangedMerge() {}
    public static boolean matches(CompoundTag current, CompoundTag update) {
        if(current.getClass()!=CompoundTag.class || update.getClass()!=CompoundTag.class) return false;
        if(update.size()>8) return false;
        for(String key:update.getAllKeys()) {
            Tag value=update.get(key);
            if(value==null) return false;
            Class<?> kind=value.getClass();
            if(kind!=ByteTag.class && kind!=ShortTag.class && kind!=IntTag.class && kind!=LongTag.class
                    && kind!=FloatTag.class && kind!=DoubleTag.class && kind!=StringTag.class) return false;
            Tag old=current.get(key);
            if(old==null || old.getClass()!=kind || !old.equals(value)) return false;
        }
        return true;
    }
}
