package agentcraft.pure;
import net.minecraft.nbt.*;
/** Only exact scalar equality is optimized. Compound/list/array/custom tags fall back. */
public final class UnchangedMerge {
    private UnchangedMerge() {}
    public static boolean matches(CompoundTag current, CompoundTag update) {
        if(current.getClass()!=CompoundTag.class || update.getClass()!=CompoundTag.class) return false;
        if(update.m_128440_()>8) return false;
        for(String key:update.m_128431_()) {
            Tag value=update.m_128423_(key);
            if(value==null) return false;
            Class<?> kind=value.getClass();
            if(kind!=ByteTag.class && kind!=ShortTag.class && kind!=IntTag.class && kind!=LongTag.class
                    && kind!=FloatTag.class && kind!=DoubleTag.class && kind!=StringTag.class) return false;
            Tag old=current.m_128423_(key);
            if(old==null || old.getClass()!=kind || !old.equals(value)) return false;
        }
        return true;
    }
}
