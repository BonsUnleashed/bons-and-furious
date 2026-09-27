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
var BONS_KEY = "valkyrien_entity_base_tick"; var BONS_SCRIPT = "pure15-valkyrien-MixinEntity.js"; var BONS_TARGET = "org.valkyrienskies.mod.mixin.entity.MixinEntity";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.mixin.entity.MixinEntity"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"onBaseTick","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V","25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n198|141\n25|0\n182|org/valkyrienskies/mod/mixin/entity/MixinEntity|m_213877_|()Z|false\n154|141\n25|0\n192|net/minecraft/world/entity/Entity\n58|2\n178|net/minecraft/world/phys/Vec3|f_82478_|Lnet/minecraft/world/phys/Vec3;\n58|3\n25|0\n182|org/valkyrienskies/mod/mixin/entity/MixinEntity|getDraggingInformation|()Lorg/valkyrienskies/mod/common/util/EntityDraggingInformation;|false\n182|org/valkyrienskies/mod/common/util/EntityDraggingInformation|isEntityBeingDraggedByAShip|()Z|false\n153|20\n178|org/valkyrienskies/mod/common/util/EntityDragger|INSTANCE|Lorg/valkyrienskies/mod/common/util/EntityDragger;\n25|2\n182|org/valkyrienskies/mod/common/util/EntityDragger|serversidePosition|(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;|false\n58|3\n167|40\n25|2\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipMountedTo|(Lnet/minecraft/world/entity/Entity;)Lorg/valkyrienskies/core/api/ships/LoadedShip;|false\n198|40\n25|2\n1\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipMountedToData|(Lnet/minecraft/world/entity/Entity;Ljava/lang/Float;)Lorg/valkyrienskies/mod/common/entity/ShipMountedToData;|false\n182|org/valkyrienskies/mod/common/entity/ShipMountedToData|getMountPosInShip|()Lorg/joml/Vector3dc;|false\n14\n25|2\n25|2\n182|net/minecraft/world/entity/Entity|m_20089_|()Lnet/minecraft/world/entity/Pose;|false\n182|net/minecraft/world/entity/Entity|m_20236_|(Lnet/minecraft/world/entity/Pose;)F|false\n141\n14\n187|org/joml/Vector3d\n89\n183|org/joml/Vector3d|<init>|()V|false\n185|org/joml/Vector3dc|add|(DDDLorg/joml/Vector3d;)Lorg/joml/Vector3d;|true\n184|org/valkyrienskies/mod/common/util/VectorConversionsMCKt|toMinecraft|(Lorg/joml/Vector3dc;)Lnet/minecraft/world/phys/Vec3;|false\n58|3\n3\n54|4\n21|4\n154|129\n25|3\n178|net/minecraft/world/phys/Vec3|f_82478_|Lnet/minecraft/world/phys/Vec3;\n165|74\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|3\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|isBlockInShipyard|(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z|false\n153|74\n25|3\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|vs$lastCheckedSealedPos|Lnet/minecraft/core/BlockPos;\n182|net/minecraft/core/BlockPos|equals|(Ljava/lang/Object;)Z|false\n153|63\n25|0\n182|org/valkyrienskies/mod/mixin/entity/MixinEntity|vs$isInSealedArea|()Z|false\n54|4\n167|129\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|3\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|isPositionSealed|(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z|false\n54|4\n25|0\n25|3\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n181|org/valkyrienskies/mod/mixin/entity/MixinEntity|vs$lastCheckedSealedPos|Lnet/minecraft/core/BlockPos;\n167|129\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|3\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|isBlockInShipyard|(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z|false\n154|129\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;|false\n58|5\n25|5\n185|org/valkyrienskies/core/api/world/ShipWorld|getAllShips|()Lorg/valkyrienskies/core/api/ships/QueryableShipData;|true\n25|2\n182|net/minecraft/world/entity/Entity|m_20191_|()Lnet/minecraft/world/phys/AABB;|false\n15\n182|net/minecraft/world/phys/AABB|m_82400_|(D)Lnet/minecraft/world/phys/AABB;|false\n184|org/valkyrienskies/mod/common/util/VectorConversionsMCKt|toJOML|(Lnet/minecraft/world/phys/AABB;)Lorg/joml/primitives/AABBd;|false\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n185|org/valkyrienskies/core/api/ships/QueryableShipData|getIntersecting|(Lorg/joml/primitives/AABBdc;Ljava/lang/String;)Ljava/lang/Iterable;|true\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|6\n25|6\n185|java/util/Iterator|hasNext|()Z|true\n153|129\n25|6\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|org/valkyrienskies/core/api/ships/Ship\n58|7\n25|7\n185|org/valkyrienskies/core/api/ships/Ship|getWorldToShip|()Lorg/joml/Matrix4dc;|true\n25|2\n182|net/minecraft/world/entity/Entity|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n184|org/valkyrienskies/mod/common/util/VectorConversionsMCKt|toJOML|(Lnet/minecraft/world/phys/Vec3;)Lorg/joml/Vector3d;|false\n187|org/joml/Vector3d\n89\n183|org/joml/Vector3d|<init>|()V|false\n185|org/joml/Matrix4dc|transformPosition|(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;|true\n184|org/valkyrienskies/mod/common/util/VectorConversionsMCKt|toMinecraft|(Lorg/joml/Vector3dc;)Lnet/minecraft/world/phys/Vec3;|false\n58|3\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|3\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|isPositionSealed|(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z|false\n153|128\n25|0\n25|3\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n181|org/valkyrienskies/mod/mixin/entity/MixinEntity|vs$lastCheckedSealedPos|Lnet/minecraft/core/BlockPos;\n4\n54|4\n167|129\n167|97\n25|0\n21|4\n153|139\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n180|net/minecraft/world/level/Level|f_46443_|Z\n184|org/valkyrienskies/mod/api/ValkyrienSkies|isConnectivityEnabled|(Z)Z|false\n153|139\n4\n167|140\n3\n182|org/valkyrienskies/mod/mixin/entity/MixinEntity|vs$setInSealedArea|(Z)V|false\n177");
bonsVerify(c,"afterCheckInside","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V","25|0\n182|org/valkyrienskies/mod/mixin/entity/MixinEntity|m_20191_|()Lnet/minecraft/world/phys/AABB;|false\n184|org/valkyrienskies/mod/common/util/VectorConversionsMCKt|toJOML|(Lnet/minecraft/world/phys/AABB;)Lorg/joml/primitives/AABBd;|false\n58|2\n187|org/joml/primitives/AABBd\n89\n183|org/joml/primitives/AABBd|<init>|()V|false\n58|3\n25|0\n180|org/valkyrienskies/mod/mixin/entity/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|2\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipsIntersecting|(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|4\n25|4\n185|java/util/Iterator|hasNext|()Z|true\n153|32\n25|4\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|org/valkyrienskies/core/api/ships/Ship\n58|5\n25|2\n25|5\n185|org/valkyrienskies/core/api/ships/Ship|getShipTransform|()Lorg/valkyrienskies/core/api/ships/properties/ShipTransform;|true\n185|org/valkyrienskies/core/api/ships/properties/ShipTransform|getWorldToShipMatrix|()Lorg/joml/Matrix4dc;|true\n25|3\n182|org/joml/primitives/AABBd|transform|(Lorg/joml/Matrix4dc;Lorg/joml/primitives/AABBd;)Lorg/joml/primitives/AABBd;|false\n58|6\n25|0\n25|6\n182|org/valkyrienskies/mod/mixin/entity/MixinEntity|originalCheckInside|(Lorg/joml/primitives/AABBd;)V|false\n167|14\n177");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"onBaseTick","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",142);
removeMethod(c,"afterCheckInside","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",33);
methodVisitor = new MethodNode(2,"onBaseTick","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,[]);
methodVisitor.visitParameter("ci", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "baseTick");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "HEAD");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsHotPaths", "skipSealedCheck", "(Lnet/minecraft/world/level/Level;)Z", false);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "vs$setInSealedArea", "(Z)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/core/BlockPos", "f_121853_", "Lnet/minecraft/core/BlockPos;");
methodVisitor.visitFieldInsn(O.PUTFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "vs$lastCheckedSealedPos", "Lnet/minecraft/core/BlockPos;");
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(78, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(79, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "m_213877_", "()Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label1);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(80, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/entity/Entity");
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(81, label4);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/world/phys/Vec3", "f_82478_", "Lnet/minecraft/world/phys/Vec3;");
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(82, label5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "getDraggingInformation", "()Lorg/valkyrienskies/mod/common/util/EntityDraggingInformation;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/common/util/EntityDraggingInformation", "isEntityBeingDraggedByAShip", "()Z", false);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label6);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(83, label7);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/mod/common/util/EntityDragger", "INSTANCE", "Lorg/valkyrienskies/mod/common/util/EntityDragger;");
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/common/util/EntityDragger", "serversidePosition", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label8 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label8);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(84, label6);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/world/entity/Entity", "net/minecraft/world/phys/Vec3"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipMountedTo", "(Lnet/minecraft/world/entity/Entity;)Lorg/valkyrienskies/core/api/ships/LoadedShip;", false);
methodVisitor.visitJumpInsn(O.IFNULL, label8);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(85, label9);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipMountedToData", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Float;)Lorg/valkyrienskies/mod/common/entity/ShipMountedToData;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/common/entity/ShipMountedToData", "getMountPosInShip", "()Lorg/joml/Vector3dc;", false);
methodVisitor.visitInsn(O.DCONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_20089_", "()Lnet/minecraft/world/entity/Pose;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_20236_", "(Lnet/minecraft/world/entity/Pose;)F", false);
methodVisitor.visitInsn(O.F2D);
methodVisitor.visitInsn(O.DCONST_0);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/Vector3d");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/Vector3d", "<init>", "()V", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "add", "(DDDLorg/joml/Vector3d;)Lorg/joml/Vector3d;", true);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/util/VectorConversionsMCKt", "toMinecraft", "(Lorg/joml/Vector3dc;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(87, label8);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(89, label10);
methodVisitor.visitVarInsn(O.ILOAD, 4);
var label11 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label11);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(90, label12);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/world/phys/Vec3", "f_82478_", "Lnet/minecraft/world/phys/Vec3;");
var label13 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label13);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "isBlockInShipyard", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label13);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(91, label14);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "vs$lastCheckedSealedPos", "Lnet/minecraft/core/BlockPos;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "equals", "(Ljava/lang/Object;)Z", false);
var label15 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label15);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(92, label16);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "vs$isInSealedArea", "()Z", false);
methodVisitor.visitVarInsn(O.ISTORE, 4);
methodVisitor.visitJumpInsn(O.GOTO, label11);
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(94, label15);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 3);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(95, label17);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(94, label18);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "isPositionSealed", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z", false);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(96, label19);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitFieldInsn(O.PUTFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "vs$lastCheckedSealedPos", "Lnet/minecraft/core/BlockPos;");
methodVisitor.visitJumpInsn(O.GOTO, label11);
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(99, label13);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "isBlockInShipyard", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label11);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(101, label20);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/world/level/Level;)Lorg/valkyrienskies/core/internal/world/VsiShipWorld;", false);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(102, label21);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/world/ShipWorld", "getAllShips", "()Lorg/valkyrienskies/core/api/ships/QueryableShipData;", true);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_20191_", "()Lnet/minecraft/world/phys/AABB;", false);
methodVisitor.visitInsn(O.DCONST_1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/phys/AABB", "m_82400_", "(D)Lnet/minecraft/world/phys/AABB;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/util/VectorConversionsMCKt", "toJOML", "(Lnet/minecraft/world/phys/AABB;)Lorg/joml/primitives/AABBd;", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/QueryableShipData", "getIntersecting", "(Lorg/joml/primitives/AABBdc;Ljava/lang/String;)Ljava/lang/Iterable;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitFrame(O.F_APPEND,2, ["org/valkyrienskies/core/internal/world/VsiShipWorld", "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
methodVisitor.visitJumpInsn(O.IFEQ, label11);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/Ship");
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(103, label23);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/Ship", "getWorldToShip", "()Lorg/joml/Matrix4dc;", true);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_20182_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/util/VectorConversionsMCKt", "toJOML", "(Lnet/minecraft/world/phys/Vec3;)Lorg/joml/Vector3d;", false);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/Vector3d");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/Vector3d", "<init>", "()V", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Matrix4dc", "transformPosition", "(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", true);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/util/VectorConversionsMCKt", "toMinecraft", "(Lorg/joml/Vector3dc;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label24 = new Label();
methodVisitor.visitLabel(label24);
methodVisitor.visitLineNumber(104, label24);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "isPositionSealed", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z", false);
var label25 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label25);
var label26 = new Label();
methodVisitor.visitLabel(label26);
methodVisitor.visitLineNumber(105, label26);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitFieldInsn(O.PUTFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "vs$lastCheckedSealedPos", "Lnet/minecraft/core/BlockPos;");
var label27 = new Label();
methodVisitor.visitLabel(label27);
methodVisitor.visitLineNumber(106, label27);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label28 = new Label();
methodVisitor.visitLabel(label28);
methodVisitor.visitLineNumber(107, label28);
methodVisitor.visitJumpInsn(O.GOTO, label11);
methodVisitor.visitLabel(label25);
methodVisitor.visitLineNumber(109, label25);
methodVisitor.visitFrame(O.F_APPEND,1, ["org/valkyrienskies/core/api/ships/Ship"], 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label22);
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(114, label11);
methodVisitor.visitFrame(O.F_CHOP,3, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ILOAD, 4);
var label29 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label29);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/level/Level", "f_46443_", "Z");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/api/ValkyrienSkies", "isConnectivityEnabled", "(Z)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label29);
methodVisitor.visitInsn(O.ICONST_1);
var label30 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label30);
methodVisitor.visitLabel(label29);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["org/valkyrienskies/mod/mixin/entity/MixinEntity"]);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLabel(label30);
methodVisitor.visitFrame(O.F_FULL, 5, ["org/valkyrienskies/mod/mixin/entity/MixinEntity", "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "net/minecraft/world/entity/Entity", "net/minecraft/world/phys/Vec3", O.INTEGER], 2, ["org/valkyrienskies/mod/mixin/entity/MixinEntity", O.INTEGER]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "vs$setInSealedArea", "(Z)V", false);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(117, label1);
methodVisitor.visitFrame(O.F_CHOP,3, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
var label31 = new Label();
methodVisitor.visitLabel(label31);
methodVisitor.visitLocalVariable("ship", "Lorg/valkyrienskies/core/api/ships/Ship;", null, label23, label25, 7);
methodVisitor.visitLocalVariable("shipWorld", "Lorg/valkyrienskies/core/api/world/ShipWorld;", null, label21, label11, 5);
methodVisitor.visitLocalVariable("entity", "Lnet/minecraft/world/entity/Entity;", null, label4, label1, 2);
methodVisitor.visitLocalVariable("relativePosition", "Lnet/minecraft/world/phys/Vec3;", null, label5, label1, 3);
methodVisitor.visitLocalVariable("isInSealedArea", "Z", null, label10, label1, 4);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/entity/MixinEntity;", null, label0, label31, 0);
methodVisitor.visitLocalVariable("ci", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;", null, label0, label31, 1);
methodVisitor.visitMaxs(9, 8);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(2,"afterCheckInside","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,[]);
methodVisitor.visitParameter("ci", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "TAIL");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "checkInsideBlocks");
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsHotPaths", "noShips", "(Lnet/minecraft/world/level/Level;)Z", false);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(164, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "m_20191_", "()Lnet/minecraft/world/phys/AABB;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/util/VectorConversionsMCKt", "toJOML", "(Lnet/minecraft/world/phys/AABB;)Lorg/joml/primitives/AABBd;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(165, label1);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/primitives/AABBd");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/primitives/AABBd", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(166, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipsIntersecting", "(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_APPEND,3, ["org/joml/primitives/AABBd", "org/joml/primitives/AABBd", "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label4);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/Ship");
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(167, label5);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/Ship", "getShipTransform", "()Lorg/valkyrienskies/core/api/ships/properties/ShipTransform;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/properties/ShipTransform", "getWorldToShipMatrix", "()Lorg/joml/Matrix4dc;", true);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/primitives/AABBd", "transform", "(Lorg/joml/Matrix4dc;Lorg/joml/primitives/AABBd;)Lorg/joml/primitives/AABBd;", false);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(168, label6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/entity/MixinEntity", "originalCheckInside", "(Lorg/joml/primitives/AABBd;)V", false);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(169, label7);
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(170, label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLocalVariable("inShipBB", "Lorg/joml/primitives/AABBd;", null, label6, label7, 6);
methodVisitor.visitLocalVariable("ship", "Lorg/valkyrienskies/core/api/ships/Ship;", null, label5, label7, 5);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/entity/MixinEntity;", null, label0, label8, 0);
methodVisitor.visitLocalVariable("ci", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;", null, label0, label8, 1);
methodVisitor.visitLocalVariable("boundingBox", "Lorg/joml/primitives/AABBd;", null, label1, label8, 2);
methodVisitor.visitLocalVariable("temp", "Lorg/joml/primitives/AABBd;", null, label2, label8, 3);
methodVisitor.visitMaxs(3, 7);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
