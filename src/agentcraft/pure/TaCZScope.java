package agentcraft.pure;
import com.tacz.guns.entity.sync.core.DataHolder;
import com.tacz.guns.entity.sync.core.DataHolderCapabilityProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
/** A reusable stack for the eight adjacent sync updates, cleared even on exceptions. */
public final class TaCZScope {
    private static final ThreadLocal<Stack> LOCAL=ThreadLocal.withInitial(Stack::new);
    private static final class Stack { Frame current; Frame spare; }
    private static final class Frame {
        Entity entity;
        LazyOptional<DataHolder> capability;
        DataHolder holder;
        Frame parent, next;
    }
    private TaCZScope() {}
    public static void begin(Entity entity) {
        Stack stack=LOCAL.get();
        Frame frame=stack.spare;
        if(frame==null) frame=new Frame(); else stack.spare=frame.next;
        frame.next=null;frame.parent=stack.current;frame.entity=entity;
        stack.current=frame;
    }
    public static void end() {
        Stack stack=LOCAL.get();Frame frame=stack.current;
        if(frame==null) throw new IllegalStateException("Unbalanced TaCZ update scope");
        stack.current=frame.parent;
        frame.parent=null;frame.entity=null;frame.capability=null;frame.holder=null;
        frame.next=stack.spare;stack.spare=frame;
    }
    public static DataHolder resolve(Entity entity) {
        Frame frame=LOCAL.get().current;
        if(frame==null || frame.entity!=entity)
            return ((ICapabilityProvider)(Object)entity).getCapability(DataHolderCapabilityProvider.CAPABILITY,null).resolve().orElse(null);
        if(frame.capability!=null && frame.capability.isPresent()) return frame.holder;
        // Empty/invalid handles are never cached across updates: another callback can revive them.
        LazyOptional<DataHolder> capability=((ICapabilityProvider)(Object)entity).getCapability(DataHolderCapabilityProvider.CAPABILITY,null);
        DataHolder holder=capability.resolve().orElse(null);
        if(holder!=null) {frame.capability=capability;frame.holder=holder;}
        else {frame.capability=null;frame.holder=null;}
        return holder;
    }
}
