package bons.portprobe;

import java.util.*;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;

/** Deterministic outputs for separate enabled/disabled runs; excluded from the release. */
final class BehaviorChecks {
    static Map<String,Object> run() throws Exception {
        Map<String,Object> out=new TreeMap<>();
        if(net.neoforged.fml.ModList.get().isLoaded("geckolib")) {
            MessageDigest md=MessageDigest.getInstance("SHA-256");int count=0;
            var fields=Arrays.stream(EasingType.class.getFields()).filter(f->f.getType()==EasingType.class)
                    .sorted(Comparator.comparing(java.lang.reflect.Field::getName)).toList();
            for(var field:fields) {
                var easing=(EasingType)field.get(null);
                var frame=new software.bernie.geckolib.animation.keyframe.Keyframe<>(256,
                        new software.bernie.geckolib.loading.math.value.Constant(-3.25),
                        new software.bernie.geckolib.loading.math.value.Constant(19.875),easing);
                for(Double parameter:new Double[]{null,2.0,4.0,8.0}) for(int i=0;i<=256;i++) {
                    double t=i/256.0;
                    double value=easing.apply(new AnimationPoint(frame,i,256,-3.25,19.875),parameter,t);
                    md.update(ByteBuffer.allocate(8).putLong(Double.doubleToLongBits(value)).array());count++;
                }
            }
            out.put("gecko_samples",count);out.put("gecko_sha256",HexFormat.of().formatHex(md.digest()));
        }
        if(net.neoforged.fml.ModList.get().isLoaded("iris")) iris(out);
        return out;
    }
    private static void iris(Map<String,Object> out) throws Exception {
        // Reflection prevents the Embeddium test arm from resolving Iris classes.
        var type=Class.forName("net.irisshaders.iris.vertices.sodium.ModelToEntityVertexSerializer");
        var serializer=type.getConstructor().newInstance();var call=type.getMethod("serialize",long.class,long.class,int.class);
        var formats=Class.forName("net.irisshaders.iris.vertices.IrisVertexFormats");
        var format=formats.getField("ENTITY").get(null);
        int stride=(Integer)format.getClass().getMethod("getVertexSize").invoke(format);
        var stateType=Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
        Object state=stateType.getField("INSTANCE").get(null);
        String[] getters={"getCurrentRenderedEntity","getCurrentRenderedBlockEntity","getCurrentRenderedItem"};
        short[] ids=new short[3];for(int i=0;i<3;i++)ids[i]=((Number)stateType.getMethod(getters[i]).invoke(state)).shortValue();
        int vertices=4096;long src=MemoryUtil.nmemAlloc(vertices*36L),dst=MemoryUtil.nmemAlloc(vertices*(long)stride+64);
        try {
            MemoryUtil.memSet(dst,0x5a,vertices*(long)stride+64);
            Random random=new Random(70128481);
            for(int v=0;v<vertices;v++) {
                long p=src+v*36L;
                for(int j=0;j<36;j+=4)MemoryUtil.memPutInt(p+j,random.nextInt());
                for(int j:new int[]{0,4,8,16,20})MemoryUtil.memPutFloat(p+j,random.nextFloat()*16-8);
                MemoryUtil.memPutInt(p+32,0x007f00);
            }
            call.invoke(serializer,src,dst,vertices);
            for(int v=0;v<vertices;v++) for(int i=0;i<3;i++) {
                long p=dst+v*(long)stride+36+i*2;
                if(MemoryUtil.memGetShort(p)!=ids[i])throw new AssertionError("Iris captured ID mismatch");
                MemoryUtil.memPutShort(p,(short)0); // Normalize render-frame IDs after checking them.
            }
            for(int i=0;i<64;i++)if(MemoryUtil.memGetByte(dst+vertices*(long)stride+i)!=(byte)0x5a)
                throw new AssertionError("Iris wrote past destination");
            byte[] bytes=new byte[vertices*stride];MemoryUtil.memByteBuffer(dst,bytes.length).get(bytes);
            out.put("iris_vertices",vertices);out.put("iris_stride",stride);
            out.put("iris_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } finally {MemoryUtil.nmemFree(src);MemoryUtil.nmemFree(dst);}
    }
}
