package org.valkyrienskies.mod.common.util;

import net.minecraft.world.phys.AABB;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.internal.collision.VsiConvexPolygonc;
import org.valkyrienskies.core.util.AABBdUtilKt;
import java.util.*;

/** Local temporaries only: no shared state, retained ship data or stale bounds. */
public final class AcVsSweep2 {
    private AcVsSweep2() {}
    public static List<VsiConvexPolygonc> sortByDistance(Iterable<? extends VsiConvexPolygonc> polygons,AABBdc box) {
        var result=new ArrayList<VsiConvexPolygonc>(polygons instanceof Collection<?> c?c.size():10);
        for(var polygon:polygons)result.add(polygon);
        int size=result.size();if(size<2)return result;
        double[] keys=new double[size];
        for(int i=0;i<size;i++)keys[i]=AABBdUtilKt.signedDistanceTo(box,result.get(i).computeCenterPos(new Vector3d()));
        // Primitive keys avoid allocating one wrapper object per polygon. Both
        // branches preserve Double.compare order and the input order of ties.
        if(size<=24){
            for(int i=1;i<size;i++){double key=keys[i];var polygon=result.get(i);int j=i;
                while(j>0&&Double.compare(keys[j-1],key)>0){keys[j]=keys[j-1];result.set(j,result.get(j-1));j--;}
                keys[j]=key;result.set(j,polygon);
            }
        }else{
            double[] temporaryKeys=new double[size];var temporaryPolygons=new VsiConvexPolygonc[size];
            for(int width=1;width<size;width=width>size/2?size:width*2){
                for(int start=0;start<size;start+=width*2){int mid=Math.min(start+width,size),end=Math.min(start+width*2,size),left=start,right=mid;
                    for(int at=start;at<end;at++){int from=left<mid&&(right>=end||Double.compare(keys[left],keys[right])<=0)?left++:right++;temporaryKeys[at]=keys[from];temporaryPolygons[at]=result.get(from);}
                }
                System.arraycopy(temporaryKeys,0,keys,0,size);for(int i=0;i<size;i++)result.set(i,temporaryPolygons[i]);
            }
        }
        return result;
    }
    public static AABBd transformedBounds(AABB box, Matrix4dc transform) {
        AABBd out = new AABBd();
        Vector3d corner = new Vector3d();
        // Same corner order, transformPosition call and Math.min/max operations
        // as the existing polygon factory + VsiConvexPolygonc.getEnclosingAABB.
        for (int i=0;i<8;i++) {
            corner.set((i&4)==0?box.f_82288_:box.f_82291_,
                       (i&2)==0?box.f_82289_:box.f_82292_,
                       (i&1)==0?box.f_82290_:box.f_82293_);
            if (transform!=null) transform.transformPosition(corner);
            out.minX=Math.min(out.minX,corner.x);out.minY=Math.min(out.minY,corner.y);out.minZ=Math.min(out.minZ,corner.z);
            out.maxX=Math.max(out.maxX,corner.x);out.maxY=Math.max(out.maxY,corner.y);out.maxZ=Math.max(out.maxZ,corner.z);
        }
        return out;
    }
}
