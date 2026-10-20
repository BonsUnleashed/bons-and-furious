package bons.furious.mixin.ars_rewind;

import com.hollingsworth.arsnouveau.common.entity.debug.FixedStack;
import java.util.Arrays;
import java.util.Stack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value=FixedStack.class,remap=false)
public abstract class FixedStackMixin extends Stack<Object> {
    @Shadow private int maxSize;
    /**
     * @author BonsUnleashed
     * @reason Compact a full rewind history once, preserving Vector storage,
     * capacity, iterator invalidation and the public Stack API.
     */
    @Overwrite
    public Object push(Object value) {
        // Subclasses may override size/remove; retain their original virtual calls.
        if(((Object)this).getClass()==FixedStack.class && maxSize>0){
            synchronized(this){
                if(elementCount>=maxSize){
                    int drop=elementCount-maxSize+1;
                    int keep=elementCount-drop;
                    System.arraycopy(elementData,drop,elementData,0,keep);
                    Arrays.fill(elementData,keep+1,elementCount,null);
                    elementData[keep]=value;
                    elementCount=keep+1;
                    modCount+=drop+1;
                    return value;
                }
            }
        }
        while(size()>=maxSize)remove(0);
        return super.push(value);
    }
}
