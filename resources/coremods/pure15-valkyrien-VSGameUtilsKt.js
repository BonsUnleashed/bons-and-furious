var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var NT = ASMAPI.NumberType;
var O = Java.type('org.objectweb.asm.Opcodes');
var MethodNode = Java.type('org.objectweb.asm.tree.MethodNode');
var Label = Java.type('org.objectweb.asm.Label');
var Type = Java.type('org.objectweb.asm.Type');
var Handle = Java.type('org.objectweb.asm.Handle');
var TypePath = Java.type('org.objectweb.asm.TypePath');
var Scanner = Java.type('java.util.Scanner');
function number(s, type) {
    var scan = new Scanner(s); scan.useLocale(Java.type('java.util.Locale').ROOT);
    var n = type === NT.LONG ? scan.nextLong() : scan.nextDouble();
    scan.close(); return ASMAPI.castNumber(n, type);
}
function removeMethod(c, name, desc, count) {
    for (var i=0; i<c.methods.size(); i++) {
        var m=c.methods.get(i);
        if (m.name !== name || m.desc !== desc) continue;
        var found=0, nodes=m.instructions.toArray();
        for(var j=0;j<nodes.length;j++) if(nodes[j].getOpcode()>=0) found++;
        if(found!==count) throw new Error('Consolidation adapter mismatch '+c.name+'.'+name+': '+found+' expected '+count);
        c.methods.remove(i);return;
    }
    throw new Error('Consolidation adapter missing method '+c.name+'.'+name+desc);
}
function removeField(c, name, desc) {
    for(var i=0;i<c.fields.size();i++){var f=c.fields.get(i);if(f.name===name&&f.desc===desc){c.fields.remove(i);return;}}
    throw new Error('Consolidation adapter missing field '+c.name+'.'+name);
}
var BONS_KEY = "valkyrien_ship_lookups"; var BONS_SCRIPT = "pure15-valkyrien-VSGameUtilsKt.js"; var BONS_TARGET = "org.valkyrienskies.mod.common.VSGameUtilsKt";
// --- Bons Pure Optimizations release guard (inserted by build.py; ES5 only, Forge coremod sandbox) ---
var BONS_ENGINE_LIST = Java.type('java.util.ArrayList');
var BONS_ENGINE_SET = Java.type('java.util.HashSet');
function bonsLog(level, message) { try { ASMAPI.log(level, message); } catch (e) {} }
function bonsDisabled() {
    if (!ASMAPI.getSystemPropertyFlag('bons_pure.config.loaded')) {
        bonsLog('WARN', 'Bons Pure Optimizations: configuration was not loaded before ' + BONS_SCRIPT + ' transformed ' + BONS_TARGET + '; applying the optimization by default');
        return false;
    }
    return ASMAPI.getSystemPropertyFlag('bons_pure.disabled.' + BONS_KEY);
}
function bonsCheckMembers(c) {
    var seen = new BONS_ENGINE_SET();
    for (var i = 0; i < c.methods.size(); i++) { var m = c.methods.get(i); if (!seen.add('m ' + m.name + m.desc)) throw new Error('duplicate method ' + m.name + m.desc); }
    for (var j = 0; j < c.fields.size(); j++) { var f = c.fields.get(j); if (!seen.add('f ' + f.name + ' ' + f.desc)) throw new Error('duplicate field ' + f.name); }
}
function bonsGuarded(c, body) {
    if (bonsDisabled()) {
        bonsLog('INFO', 'Bons Pure Optimizations: ' + BONS_KEY + ' is disabled by config; ' + c.name + ' is left unchanged');
        return c;
    }
    var methods = new BONS_ENGINE_LIST(c.methods), fields = new BONS_ENGINE_LIST(c.fields);
    var interfaces = c.interfaces === null ? null : new BONS_ENGINE_LIST(c.interfaces);
    var access = c.access, superName = c.superName, signature = c.signature, version = c.version;
    try {
        var result = body(c);
        if (result !== c) throw new Error('transformer returned a different class node');
        bonsCheckMembers(c);
        bonsLog('DEBUG', 'Bons Pure Optimizations: ' + BONS_KEY + ' applied to ' + c.name);
        return c;
    } catch (e) {
        c.methods.clear(); c.methods.addAll(methods);
        c.fields.clear(); c.fields.addAll(fields);
        if (interfaces !== null) { c.interfaces.clear(); c.interfaces.addAll(interfaces); }
        c.access = access; c.superName = superName; c.signature = signature; c.version = version;
        bonsLog('WARN', 'Bons Pure Optimizations: ' + BONS_KEY + ' skipped for ' + c.name + ' because the installed class does not match the supported version (' + e + '); the class is left unchanged');
        return c;
    }
}
// --- end of release guard ---
function bonsVerify(c,name,desc,expected) {
 var m=null;for(var i=0;i<c.methods.size();i++){var v=c.methods.get(i);if(v.name===name&&v.desc===desc){m=v;break;}}
 if(m===null)throw new Error('Missing guarded method '+name+desc);
 var nodes=m.instructions.toArray();
 function label(l){var p=0;for(var k=0;k<nodes.length;k++){if(nodes[k]===l)return p;if(nodes[k].getOpcode()>=0)p++;}throw new Error('Unknown label');}
 var result=[];
 for(var j=0;j<nodes.length;j++) {
  var n=nodes[j],op=n.getOpcode();if(op<0)continue;var s=''+op;
  switch(n.getType()) {
   case 1:s+='|'+n.operand;break;
   case 2:s+='|'+n['var'];break;
   case 3:s+='|'+n.desc;break;
   case 4:s+='|'+n.owner+'|'+n.name+'|'+n.desc;break;
   case 5:s+='|'+n.owner+'|'+n.name+'|'+n.desc+'|'+n.itf;break;
   case 6:s+='|'+n.name+'|'+n.desc+'|'+n.bsm;for(var a=0;a<n.bsmArgs.length;a++)s+='|'+n.bsmArgs[a];break;
   case 7:s+='|'+label(n.label);break;
   case 9:s+='|'+n.cst;break;
   case 10:s+='|'+n['var']+'|'+n.incr;break;
   case 11:s+='|'+n.min+'|'+n.max+'|'+label(n.dflt);for(var a=0;a<n.labels.size();a++)s+='|'+label(n.labels.get(a));break;
   case 12:s+='|'+label(n.dflt);for(var a=0;a<n.keys.size();a++)s+='|'+n.keys.get(a)+':'+label(n.labels.get(a));break;
   case 13:s+='|'+n.desc+'|'+n.dims;break;
  }
  result.push(s);
 }
 for(var i=0;i<m.tryCatchBlocks.size();i++){var t=m.tryCatchBlocks.get(i);result.push('catch|'+label(t.start)+'|'+label(t.end)+'|'+label(t.handler)+'|'+t.type);}
 if(result.join('\n')!==expected)throw new Error('Opcode/operand mismatch '+name+desc);
}

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.common.VSGameUtilsKt"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"getShipObjectManagingPosImpl","(Lnet/minecraft/world/level/Level;II)Lorg/valkyrienskies/core/api/ships/LoadedShip;","25|0\n198|29\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;|false\n21|1\n21|2\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n185|org/valkyrienskies/core/internal/world/VsiShipWorld|isChunkInShipyard|(IILjava/lang/String;)Z|true\n153|29\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;|false\n185|org/valkyrienskies/core/internal/world/VsiShipWorld|getAllShips|()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;|true\n21|1\n21|2\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n185|org/valkyrienskies/core/internal/ships/VsiQueryableShipData|getByChunkPos|(IILjava/lang/String;)Lorg/valkyrienskies/core/api/ships/Ship;|true\n58|3\n25|3\n198|29\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;|false\n185|org/valkyrienskies/core/internal/world/VsiShipWorld|getLoadedShips|()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;|true\n25|3\n185|org/valkyrienskies/core/api/ships/Ship|getId|()J|true\n185|org/valkyrienskies/core/internal/ships/VsiQueryableShipData|getById|(J)Lorg/valkyrienskies/core/api/ships/Ship;|true\n192|org/valkyrienskies/core/api/ships/LoadedShip\n176\n1\n176");
bonsVerify(c,"transformToNearbyShipsAndWorld","(Lnet/minecraft/world/level/Level;DDDDLorg/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer;)V","25|9\n18|cb\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|0\n89\n198|110\n58|10\n3\n54|11\n25|10\n24|1\n24|3\n24|5\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipManagingPos|(Lnet/minecraft/world/level/Level;DDD)Lorg/valkyrienskies/core/api/ships/Ship;|false\n58|12\n187|org/joml/primitives/AABBd\n89\n24|1\n24|3\n24|5\n24|1\n24|3\n24|5\n183|org/joml/primitives/AABBd|<init>|(DDDDDD)V|false\n24|7\n184|org/valkyrienskies/core/util/AABBdUtilKt|expand|(Lorg/joml/primitives/AABBd;D)Lorg/joml/primitives/AABBd;|false\n58|13\n187|org/joml/Vector3d\n89\n24|1\n24|3\n24|5\n183|org/joml/Vector3d|<init>|(DDD)V|false\n58|14\n187|org/joml/Vector3d\n89\n183|org/joml/Vector3d|<init>|()V|false\n58|15\n25|12\n198|62\n25|12\n185|org/valkyrienskies/core/api/ships/Ship|getShipToWorld|()Lorg/joml/Matrix4dc;|true\n25|14\n185|org/joml/Matrix4dc|transformPosition|(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;|true\n87\n25|14\n182|org/joml/Vector3d|x|()D|false\n25|14\n182|org/joml/Vector3d|y|()D|false\n25|14\n182|org/joml/Vector3d|z|()D|false\n57|16\n57|18\n57|20\n3\n54|22\n25|9\n24|20\n24|18\n24|16\n185|org/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer|accept|(DDD)V|true\n0\n25|10\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;|false\n185|org/valkyrienskies/core/internal/world/VsiShipWorld|getAllShips|()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;|true\n25|13\n192|org/joml/primitives/AABBdc\n25|10\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n185|org/valkyrienskies/core/internal/ships/VsiQueryableShipData|getIntersecting|(Lorg/joml/primitives/AABBdc;Ljava/lang/String;)Ljava/lang/Iterable;|true\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|23\n25|23\n185|java/util/Iterator|hasNext|()Z|true\n153|108\n25|23\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|org/valkyrienskies/core/api/ships/Ship\n58|24\n25|24\n25|12\n184|kotlin/jvm/internal/Intrinsics|areEqual|(Ljava/lang/Object;Ljava/lang/Object;)Z|false\n154|72\n25|24\n185|org/valkyrienskies/core/api/ships/Ship|getWorldToShip|()Lorg/joml/Matrix4dc;|true\n25|14\n192|org/joml/Vector3dc\n25|15\n185|org/joml/Matrix4dc|transformPosition|(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;|true\n58|25\n25|25\n182|org/joml/Vector3d|x|()D|false\n25|25\n182|org/joml/Vector3d|y|()D|false\n25|25\n182|org/joml/Vector3d|z|()D|false\n57|26\n57|28\n57|30\n3\n54|32\n25|9\n24|30\n24|28\n24|26\n185|org/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer|accept|(DDD)V|true\n0\n167|72\n0\n167|112\n87\n0\n177");
bonsVerify(c,"isChunkInShipyard","(Lnet/minecraft/world/level/Level;II)Z","25|0\n18|<this>\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;|false\n21|1\n21|2\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n185|org/valkyrienskies/core/internal/world/VsiShipWorld|isChunkInShipyard|(IILjava/lang/String;)Z|true\n172");
bonsVerify(c,"getShipManagingPosImpl","(Lnet/minecraft/world/level/Level;II)Lorg/valkyrienskies/core/api/ships/Ship;","25|0\n198|16\n25|0\n21|1\n21|2\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|isChunkInShipyard|(Lnet/minecraft/world/level/Level;II)Z|false\n153|16\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;|false\n185|org/valkyrienskies/core/internal/world/VsiShipWorld|getAllShips|()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;|true\n21|1\n21|2\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n185|org/valkyrienskies/core/internal/ships/VsiQueryableShipData|getByChunkPos|(IILjava/lang/String;)Lorg/valkyrienskies/core/api/ships/Ship;|true\n167|17\n1\n176");
bonsVerify(c,"toWorldCoordinates","(Lnet/minecraft/world/level/Level;DDDLorg/joml/Vector3d;)Lorg/joml/Vector3d;","25|7\n18|dest\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|0\n24|1\n24|3\n24|5\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipManagingPos|(Lnet/minecraft/world/level/Level;DDD)Lorg/valkyrienskies/core/api/ships/Ship;|false\n89\n198|19\n24|1\n24|3\n24|5\n1\n16|8\n1\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|toWorldCoordinates$default|(Lorg/valkyrienskies/core/api/ships/Ship;DDDLorg/joml/Vector3d;ILjava/lang/Object;)Lorg/joml/Vector3d;|false\n89\n199|28\n87\n25|7\n24|1\n24|3\n24|5\n182|org/joml/Vector3d|set|(DDD)Lorg/joml/Vector3d;|false\n89\n18|set(...)\n184|kotlin/jvm/internal/Intrinsics|checkNotNullExpressionValue|(Ljava/lang/Object;Ljava/lang/String;)V|false\n176");
bonsVerify(c,"toDenseVoxelUpdate","(Lnet/minecraft/world/level/chunk/LevelChunkSection;Lorg/joml/Vector3ic;)Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate;","25|0\n18|<this>\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|1\n18|chunkPos\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getVsCore|()Lorg/valkyrienskies/core/internal/VsiCore;|false\n25|1\n185|org/joml/Vector3ic|x|()I|true\n25|1\n185|org/joml/Vector3ic|y|()I|true\n25|1\n185|org/joml/Vector3ic|z|()I|true\n185|org/valkyrienskies/core/internal/VsiCore|newDenseTerrainUpdateBuilder|(III)Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate$Builder;|true\n58|2\n178|org/valkyrienskies/mod/common/BlockStateInfo|INSTANCE|Lorg/valkyrienskies/mod/common/BlockStateInfo;\n182|org/valkyrienskies/mod/common/BlockStateInfo|getCache|()Lorg/valkyrienskies/mod/common/BlockStateInfo$Cache;|false\n58|3\n3\n54|4\n21|4\n16|16\n162|64\n3\n54|5\n21|5\n16|16\n162|62\n3\n54|6\n21|6\n16|16\n162|60\n25|2\n21|4\n21|5\n21|6\n25|3\n25|0\n21|4\n21|5\n21|6\n182|net/minecraft/world/level/chunk/LevelChunkSection|m_62982_|(III)Lnet/minecraft/world/level/block/state/BlockState;|false\n89\n18|getBlockState(...)\n184|kotlin/jvm/internal/Intrinsics|checkNotNullExpressionValue|(Ljava/lang/Object;Ljava/lang/String;)V|false\n182|org/valkyrienskies/mod/common/BlockStateInfo$Cache|get|(Lnet/minecraft/world/level/block/state/BlockState;)Lkotlin/Pair;|false\n89\n198|53\n182|kotlin/Pair|getSecond|()Ljava/lang/Object;|false\n192|org/valkyrienskies/core/internal/world/chunks/VsiBlockType\n89\n199|57\n87\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getVsCore|()Lorg/valkyrienskies/core/internal/VsiCore;|false\n185|org/valkyrienskies/core/internal/VsiCore|getBlockTypes|()Lorg/valkyrienskies/core/internal/world/chunks/VsiBlockTypes;|true\n185|org/valkyrienskies/core/internal/world/chunks/VsiBlockTypes|getAir|()Lorg/valkyrienskies/core/internal/world/chunks/VsiBlockType;|true\n185|org/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate$Builder|addBlock|(IIILorg/valkyrienskies/core/internal/world/chunks/VsiBlockType;)V|true\n132|6|1\n167|30\n132|5|1\n167|25\n132|4|1\n167|20\n25|2\n185|org/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate$Builder|build|()Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate;|true\n176");
bonsVerify(c,"getShipsIntersecting","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;","25|0\n18|<this>\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|1\n18|aabb\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|0\n25|1\n184|org/valkyrienskies/mod/common/util/VectorConversionsMCKt|toJOML|(Lnet/minecraft/world/phys/AABB;)Lorg/joml/primitives/AABBd;|false\n192|org/joml/primitives/AABBdc\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipsIntersecting|(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;|false\n176");
bonsVerify(c,"getShipsIntersecting","(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;","25|0\n18|<this>\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|1\n18|aabb\n184|kotlin/jvm/internal/Intrinsics|checkNotNullParameter|(Ljava/lang/Object;Ljava/lang/String;)V|false\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getAllShips|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;|false\n25|1\n25|0\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n185|org/valkyrienskies/core/internal/ships/VsiQueryableShipData|getIntersecting|(Lorg/joml/primitives/AABBdc;Ljava/lang/String;)Ljava/lang/Iterable;|true\n176");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getShipObjectManagingPosImpl","(Lnet/minecraft/world/level/Level;II)Lorg/valkyrienskies/core/api/ships/LoadedShip;",31);
removeMethod(c,"transformToNearbyShipsAndWorld","(Lnet/minecraft/world/level/Level;DDDDLorg/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer;)V",113);
removeMethod(c,"isChunkInShipyard","(Lnet/minecraft/world/level/Level;II)Z",11);
removeMethod(c,"getShipManagingPosImpl","(Lnet/minecraft/world/level/Level;II)Lorg/valkyrienskies/core/api/ships/Ship;",18);
removeMethod(c,"toWorldCoordinates","(Lnet/minecraft/world/level/Level;DDDLorg/joml/Vector3d;)Lorg/joml/Vector3d;",29);
removeMethod(c,"toDenseVoxelUpdate","(Lnet/minecraft/world/level/chunk/LevelChunkSection;Lorg/joml/Vector3ic;)Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate;",67);
removeMethod(c,"getShipsIntersecting","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;",12);
removeMethod(c,"getShipsIntersecting","(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;",13);
methodVisitor = new MethodNode(26,"getShipObjectManagingPosImpl","(Lnet/minecraft/world/level/Level;II)Lorg/valkyrienskies/core/api/ships/LoadedShip;",null,[]);
methodVisitor.visitParameter("world", 0);
methodVisitor.visitParameter("chunkX", 0);
methodVisitor.visitParameter("chunkZ", 0);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "inShipyardRange", "(II)Z", false);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(194, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;", false);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/world/VsiShipWorld", "isChunkInShipyard", "(IILjava/lang/String;)Z", true);
methodVisitor.visitJumpInsn(O.IFEQ, label1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(195, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/world/VsiShipWorld", "getAllShips", "()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;", true);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/ships/VsiQueryableShipData", "getByChunkPos", "(IILjava/lang/String;)Lorg/valkyrienskies/core/api/ships/Ship;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(196, label3);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitJumpInsn(O.IFNULL, label1);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(197, label4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/world/VsiShipWorld", "getLoadedShips", "()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;", true);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/Ship", "getId", "()J", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/ships/VsiQueryableShipData", "getById", "(J)Lorg/valkyrienskies/core/api/ships/Ship;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/LoadedShip");
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(200, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitInsn(O.ARETURN);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLocalVariable("ship", "Lorg/valkyrienskies/core/api/ships/Ship;", null, label3, label1, 3);
methodVisitor.visitLocalVariable("world", "Lnet/minecraft/world/level/Level;", null, label0, label5, 0);
methodVisitor.visitLocalVariable("chunkX", "I", null, label0, label5, 1);
methodVisitor.visitLocalVariable("chunkZ", "I", null, label0, label5, 2);
methodVisitor.visitMaxs(4, 4);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(25,"transformToNearbyShipsAndWorld","(Lnet/minecraft/world/level/Level;DDDDLorg/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer;)V",null,[]);
methodVisitor.visitParameter("$this$transformToNearbyShipsAndWorld", 0);
methodVisitor.visitParameter("x", 0);
methodVisitor.visitParameter("y", 0);
methodVisitor.visitParameter("z", 0);
methodVisitor.visitParameter("aabbRadius", 0);
methodVisitor.visitParameter("cb", 0);
methodVisitor.visitAnnotableParameterCount(6, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/Nullable;", false);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(5, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitLdcInsn("cb");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitVarInsn(O.DLOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "noShipNearPoint", "(Lnet/minecraft/world/level/Level;DDDD)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label1);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(239, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.DUP);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label2);
methodVisitor.visitVarInsn(O.ASTORE, 10);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 11);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(613, label4);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipManagingPos", "(Lnet/minecraft/world/level/Level;DDD)Lorg/valkyrienskies/core/api/ships/Ship;", false);
methodVisitor.visitVarInsn(O.ASTORE, 12);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(614, label5);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/primitives/AABBd");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/primitives/AABBd", "<init>", "(DDDDDD)V", false);
methodVisitor.visitVarInsn(O.DLOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/core/util/AABBdUtilKt", "expand", "(Lorg/joml/primitives/AABBd;D)Lorg/joml/primitives/AABBd;", false);
methodVisitor.visitVarInsn(O.ASTORE, 13);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(616, label6);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/Vector3d");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/Vector3d", "<init>", "(DDD)V", false);
methodVisitor.visitVarInsn(O.ASTORE, 14);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(617, label7);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/Vector3d");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/Vector3d", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 15);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(619, label8);
methodVisitor.visitVarInsn(O.ALOAD, 12);
var label9 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label9);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(620, label10);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/Ship", "getShipToWorld", "()Lorg/joml/Matrix4dc;", true);
methodVisitor.visitVarInsn(O.ALOAD, 14);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Matrix4dc", "transformPosition", "(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", true);
methodVisitor.visitInsn(O.POP);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(622, label11);
methodVisitor.visitVarInsn(O.ALOAD, 14);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3d", "x", "()D", false);
methodVisitor.visitVarInsn(O.ALOAD, 14);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3d", "y", "()D", false);
methodVisitor.visitVarInsn(O.ALOAD, 14);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3d", "z", "()D", false);
methodVisitor.visitVarInsn(O.DSTORE, 16);
methodVisitor.visitVarInsn(O.DSTORE, 18);
methodVisitor.visitVarInsn(O.DSTORE, 20);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 22);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(239, label13);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitVarInsn(O.DLOAD, 20);
methodVisitor.visitVarInsn(O.DLOAD, 18);
methodVisitor.visitVarInsn(O.DLOAD, 16);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer", "accept", "(DDD)V", true);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(622, label14);
methodVisitor.visitInsn(O.NOP);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(625, label9);
methodVisitor.visitFrame(O.F_FULL, 12, ["net/minecraft/world/level/Level", O.DOUBLE, O.DOUBLE, O.DOUBLE, O.DOUBLE, "org/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer", "net/minecraft/world/level/Level", O.INTEGER, "org/valkyrienskies/core/api/ships/Ship", "org/joml/primitives/AABBd", "org/joml/Vector3d", "org/joml/Vector3d"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/world/VsiShipWorld", "getAllShips", "()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;", true);
methodVisitor.visitVarInsn(O.ALOAD, 13);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/joml/primitives/AABBdc");
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/ships/VsiQueryableShipData", "getIntersecting", "(Lorg/joml/primitives/AABBdc;Ljava/lang/String;)Ljava/lang/Iterable;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 23);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitFrame(O.F_FULL, 20, ["net/minecraft/world/level/Level", O.DOUBLE, O.DOUBLE, O.DOUBLE, O.DOUBLE, "org/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer", "net/minecraft/world/level/Level", O.INTEGER, "org/valkyrienskies/core/api/ships/Ship", "org/joml/primitives/AABBd", "org/joml/Vector3d", "org/joml/Vector3d", O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 23);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label16 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label16);
methodVisitor.visitVarInsn(O.ALOAD, 23);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/Ship");
methodVisitor.visitVarInsn(O.ASTORE, 24);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(626, label17);
methodVisitor.visitVarInsn(O.ALOAD, 24);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "areEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label15);
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(627, label18);
methodVisitor.visitVarInsn(O.ALOAD, 24);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/Ship", "getWorldToShip", "()Lorg/joml/Matrix4dc;", true);
methodVisitor.visitVarInsn(O.ALOAD, 14);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/joml/Vector3dc");
methodVisitor.visitVarInsn(O.ALOAD, 15);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Matrix4dc", "transformPosition", "(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", true);
methodVisitor.visitVarInsn(O.ASTORE, 25);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(628, label19);
methodVisitor.visitVarInsn(O.ALOAD, 25);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3d", "x", "()D", false);
methodVisitor.visitVarInsn(O.ALOAD, 25);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3d", "y", "()D", false);
methodVisitor.visitVarInsn(O.ALOAD, 25);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3d", "z", "()D", false);
methodVisitor.visitVarInsn(O.DSTORE, 26);
methodVisitor.visitVarInsn(O.DSTORE, 28);
methodVisitor.visitVarInsn(O.DSTORE, 30);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 32);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(239, label21);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitVarInsn(O.DLOAD, 30);
methodVisitor.visitVarInsn(O.DLOAD, 28);
methodVisitor.visitVarInsn(O.DLOAD, 26);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer", "accept", "(DDD)V", true);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(628, label22);
methodVisitor.visitInsn(O.NOP);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitJumpInsn(O.GOTO, label15);
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(630, label16);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.NOP);
var label24 = new Label();
methodVisitor.visitLabel(label24);
var label25 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label25);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(239, label2);
methodVisitor.visitFrame(O.F_FULL, 6, ["net/minecraft/world/level/Level", O.DOUBLE, O.DOUBLE, O.DOUBLE, O.DOUBLE, "org/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer"], 1, ["net/minecraft/world/level/Level"]);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitInsn(O.NOP);
methodVisitor.visitLabel(label25);
methodVisitor.visitLineNumber(240, label25);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
var label26 = new Label();
methodVisitor.visitLabel(label26);
methodVisitor.visitLocalVariable("$i$a$-transformToNearbyShipsAndWorld-VSGameUtilsKt$transformToNearbyShipsAndWorld$2", "I", null, label13, label14, 22);
methodVisitor.visitLocalVariable("p0", "D", null, label12, label14, 20);
methodVisitor.visitLocalVariable("p1", "D", null, label12, label14, 18);
methodVisitor.visitLocalVariable("p2", "D", null, label12, label14, 16);
methodVisitor.visitLocalVariable("$i$a$-transformToNearbyShipsAndWorld-VSGameUtilsKt$transformToNearbyShipsAndWorld$2", "I", null, label21, label22, 32);
methodVisitor.visitLocalVariable("p0", "D", null, label20, label22, 30);
methodVisitor.visitLocalVariable("p1", "D", null, label20, label22, 28);
methodVisitor.visitLocalVariable("p2", "D", null, label20, label22, 26);
methodVisitor.visitLocalVariable("posInShip$iv", "Lorg/joml/Vector3d;", null, label19, label23, 25);
methodVisitor.visitLocalVariable("nearbyShip$iv", "Lorg/valkyrienskies/core/api/ships/Ship;", null, label17, label23, 24);
methodVisitor.visitLocalVariable("$i$f$transformToNearbyShipsAndWorld", "I", null, label4, label24, 11);
methodVisitor.visitLocalVariable("currentShip$iv", "Lorg/valkyrienskies/core/api/ships/Ship;", null, label5, label24, 12);
methodVisitor.visitLocalVariable("aabb$iv", "Lorg/joml/primitives/AABBd;", null, label6, label24, 13);
methodVisitor.visitLocalVariable("posInWorld$iv", "Lorg/joml/Vector3d;", null, label7, label24, 14);
methodVisitor.visitLocalVariable("temp0$iv", "Lorg/joml/Vector3d;", null, label8, label24, 15);
methodVisitor.visitLocalVariable("$this$transformToNearbyShipsAndWorld$iv", "Lnet/minecraft/world/level/Level;", null, label3, label24, 10);
methodVisitor.visitLocalVariable("$this$transformToNearbyShipsAndWorld", "Lnet/minecraft/world/level/Level;", null, label0, label26, 0);
methodVisitor.visitLocalVariable("x", "D", null, label0, label26, 1);
methodVisitor.visitLocalVariable("y", "D", null, label0, label26, 3);
methodVisitor.visitLocalVariable("z", "D", null, label0, label26, 5);
methodVisitor.visitLocalVariable("aabbRadius", "D", null, label0, label26, 7);
methodVisitor.visitLocalVariable("cb", "Lorg/valkyrienskies/core/api/util/functions/DoubleTernaryConsumer;", null, label0, label26, 9);
methodVisitor.visitMaxs(14, 33);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(25,"isChunkInShipyard","(Lnet/minecraft/world/level/Level;II)Z",null,[]);
methodVisitor.visitParameter("$this$isChunkInShipyard", 0);
methodVisitor.visitParameter("chunkX", 0);
methodVisitor.visitParameter("chunkZ", 0);
methodVisitor.visitAnnotableParameterCount(3, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitLdcInsn("<this>");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "inShipyardRange", "(II)Z", false);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label1);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(266, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;", false);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/world/VsiShipWorld", "isChunkInShipyard", "(IILjava/lang/String;)Z", true);
methodVisitor.visitInsn(O.IRETURN);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLocalVariable("$this$isChunkInShipyard", "Lnet/minecraft/world/level/Level;", null, label0, label2, 0);
methodVisitor.visitLocalVariable("chunkX", "I", null, label0, label2, 1);
methodVisitor.visitLocalVariable("chunkZ", "I", null, label0, label2, 2);
methodVisitor.visitMaxs(4, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(26,"getShipManagingPosImpl","(Lnet/minecraft/world/level/Level;II)Lorg/valkyrienskies/core/api/ships/Ship;",null,[]);
methodVisitor.visitParameter("world", 0);
methodVisitor.visitParameter("x", 0);
methodVisitor.visitParameter("z", 0);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "inShipyardRange", "(II)Z", false);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(379, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "isChunkInShipyard", "(Lnet/minecraft/world/level/Level;II)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(380, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/world/VsiShipWorld", "getAllShips", "()Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;", true);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/ships/VsiQueryableShipData", "getByChunkPos", "(IILjava/lang/String;)Lorg/valkyrienskies/core/api/ships/Ship;", true);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(382, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(379, label3);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["org/valkyrienskies/core/api/ships/Ship"]);
methodVisitor.visitInsn(O.ARETURN);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLocalVariable("world", "Lnet/minecraft/world/level/Level;", null, label0, label4, 0);
methodVisitor.visitLocalVariable("x", "I", null, label0, label4, 1);
methodVisitor.visitLocalVariable("z", "I", null, label0, label4, 2);
methodVisitor.visitMaxs(4, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(25,"toWorldCoordinates","(Lnet/minecraft/world/level/Level;DDDLorg/joml/Vector3d;)Lorg/joml/Vector3d;",null,[]);
methodVisitor.visitParameter("$this$toWorldCoordinates", 0);
methodVisitor.visitParameter("x", 0);
methodVisitor.visitParameter("y", 0);
methodVisitor.visitParameter("z", 0);
methodVisitor.visitParameter("dest", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lkotlin/jvm/JvmOverloads;", false);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(5, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/Nullable;", false);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(4, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitLdcInsn("dest");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(461, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipManagingPos", "(Lnet/minecraft/world/level/Level;DDD)Lorg/valkyrienskies/core/api/ships/Ship;", false);
methodVisitor.visitInsn(O.DUP);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label2);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "toWorldCoordinates", "(Lorg/valkyrienskies/core/api/ships/Ship;DDDLorg/joml/Vector3d;)Lorg/joml/Vector3d;", false);
methodVisitor.visitInsn(O.DUP);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label3);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["java/lang/Object"]);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.DLOAD, 3);
methodVisitor.visitVarInsn(O.DLOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3d", "set", "(DDD)Lorg/joml/Vector3d;", false);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitLdcInsn("set(...)");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullExpressionValue", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["org/joml/Vector3d"]);
methodVisitor.visitInsn(O.ARETURN);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLocalVariable("$this$toWorldCoordinates", "Lnet/minecraft/world/level/Level;", null, label0, label4, 0);
methodVisitor.visitLocalVariable("x", "D", null, label0, label4, 1);
methodVisitor.visitLocalVariable("y", "D", null, label0, label4, 3);
methodVisitor.visitLocalVariable("z", "D", null, label0, label4, 5);
methodVisitor.visitLocalVariable("dest", "Lorg/joml/Vector3d;", null, label0, label4, 7);
methodVisitor.visitMaxs(8, 8);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(25,"toDenseVoxelUpdate","(Lnet/minecraft/world/level/chunk/LevelChunkSection;Lorg/joml/Vector3ic;)Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate;",null,[]);
methodVisitor.visitParameter("$this$toDenseVoxelUpdate", 0);
methodVisitor.visitParameter("chunkPos", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(2, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(1, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitLdcInsn("<this>");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("chunkPos");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep7", "denseVoxelUpdate", "(Lnet/minecraft/world/level/chunk/LevelChunkSection;Lorg/joml/Vector3ic;)Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate;", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(2, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(25,"getShipsIntersecting","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable<Lorg/valkyrienskies/core/api/ships/Ship;>;",[]);
methodVisitor.visitParameter("$this$getShipsIntersecting", 0);
methodVisitor.visitParameter("aabb", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(2, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(1, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitLdcInsn("<this>");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("aabb");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "shipsIntersecting", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/AABB;)Ljava/util/ArrayList;", false);
methodVisitor.visitInsn(O.DUP);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["java/util/ArrayList"]);
methodVisitor.visitInsn(O.POP);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(490, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/util/VectorConversionsMCKt", "toJOML", "(Lnet/minecraft/world/phys/AABB;)Lorg/joml/primitives/AABBd;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/joml/primitives/AABBdc");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipsIntersecting", "(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;", false);
methodVisitor.visitInsn(O.ARETURN);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLocalVariable("$this$getShipsIntersecting", "Lnet/minecraft/world/level/Level;", null, label0, label3, 0);
methodVisitor.visitLocalVariable("aabb", "Lnet/minecraft/world/phys/AABB;", null, label0, label3, 1);
methodVisitor.visitMaxs(2, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(25,"getShipsIntersecting","(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;","(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable<Lorg/valkyrienskies/core/api/ships/Ship;>;",[]);
methodVisitor.visitParameter("$this$getShipsIntersecting", 0);
methodVisitor.visitParameter("aabb", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(2, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(1, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitLdcInsn("<this>");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("aabb");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "checkNotNullParameter", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "shipsIntersecting", "(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/util/ArrayList;", false);
methodVisitor.visitInsn(O.DUP);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["java/util/ArrayList"]);
methodVisitor.visitInsn(O.POP);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(491, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getAllShips", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/ships/VsiQueryableShipData", "getIntersecting", "(Lorg/joml/primitives/AABBdc;Ljava/lang/String;)Ljava/lang/Iterable;", true);
methodVisitor.visitInsn(O.ARETURN);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLocalVariable("$this$getShipsIntersecting", "Lnet/minecraft/world/level/Level;", null, label0, label3, 0);
methodVisitor.visitLocalVariable("aabb", "Lorg/joml/primitives/AABBdc;", null, label0, label3, 1);
methodVisitor.visitMaxs(3, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
