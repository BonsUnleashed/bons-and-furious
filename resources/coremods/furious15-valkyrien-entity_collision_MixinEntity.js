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
var BONS_KEY = "valkyrien_standing_probe"; var BONS_SCRIPT = "furious15-valkyrien-entity_collision_MixinEntity.js"; var BONS_TARGET = "org.valkyrienskies.mod.mixin.feature.entity_collision.MixinEntity";
// --- Bons and Furious release guard (inserted by build.py; ES5 only, Forge coremod sandbox) ---
var BONS_ENGINE_LIST = Java.type('java.util.ArrayList');
var BONS_ENGINE_SET = Java.type('java.util.HashSet');
function bonsLog(level, message) { try { ASMAPI.log(level, message); } catch (e) {} }
function bonsDisabled() {
    if (!ASMAPI.getSystemPropertyFlag('bons_and_furious.config.loaded')) {
        bonsLog('WARN', 'Bons and Furious: configuration was not loaded before ' + BONS_SCRIPT + ' transformed ' + BONS_TARGET + '; applying the optimization by default');
        return false;
    }
    return ASMAPI.getSystemPropertyFlag('bons_and_furious.disabled.' + BONS_KEY);
}
function bonsCheckMembers(c) {
    var seen = new BONS_ENGINE_SET();
    for (var i = 0; i < c.methods.size(); i++) { var m = c.methods.get(i); if (!seen.add('m ' + m.name + m.desc)) throw new Error('duplicate method ' + m.name + m.desc); }
    for (var j = 0; j < c.fields.size(); j++) { var f = c.fields.get(j); if (!seen.add('f ' + f.name + ' ' + f.desc)) throw new Error('duplicate field ' + f.name); }
}
function bonsGuarded(c, body) {
    if (bonsDisabled()) {
        bonsLog('INFO', 'Bons and Furious: ' + BONS_KEY + ' is disabled by config; ' + c.name + ' is left unchanged');
        return c;
    }
    var methods = new BONS_ENGINE_LIST(c.methods), fields = new BONS_ENGINE_LIST(c.fields);
    var interfaces = c.interfaces === null ? null : new BONS_ENGINE_LIST(c.interfaces);
    var access = c.access, superName = c.superName, signature = c.signature, version = c.version;
    try {
        var result = body(c);
        if (result !== c) throw new Error('transformer returned a different class node');
        bonsCheckMembers(c);
        bonsLog('DEBUG', 'Bons and Furious: ' + BONS_KEY + ' applied to ' + c.name);
        return c;
    } catch (e) {
        c.methods.clear(); c.methods.addAll(methods);
        c.fields.clear(); c.fields.addAll(fields);
        if (interfaces !== null) { c.interfaces.clear(); c.interfaces.addAll(interfaces); }
        c.access = access; c.superName = superName; c.signature = signature; c.version = version;
        bonsLog('WARN', 'Bons and Furious: ' + BONS_KEY + ' skipped for ' + c.name + ' because the installed class does not match the supported version (' + e + '); the class is left unchanged');
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.mixin.feature.entity_collision.MixinEntity"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"getPosStandingOnFromShips","(Lorg/joml/Vector3dc;)Lnet/minecraft/core/BlockPos;","18|0.5\n57|2\n187|org/joml/primitives/AABBd\n89\n25|1\n185|org/joml/Vector3dc|x|()D|true\n18|0.5\n103\n25|1\n185|org/joml/Vector3dc|y|()D|true\n18|0.5\n103\n25|1\n185|org/joml/Vector3dc|z|()D|true\n18|0.5\n103\n25|1\n185|org/joml/Vector3dc|x|()D|true\n18|0.5\n99\n25|1\n185|org/joml/Vector3dc|y|()D|true\n18|0.5\n99\n25|1\n185|org/joml/Vector3dc|z|()D|true\n18|0.5\n99\n183|org/joml/primitives/AABBd|<init>|(DDDDDD)V|false\n58|4\n25|0\n180|org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|4\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipsIntersecting|(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;|false\n58|5\n25|5\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|6\n25|6\n185|java/util/Iterator|hasNext|()Z|true\n153|107\n25|6\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|org/valkyrienskies/core/api/ships/Ship\n58|7\n25|7\n185|org/valkyrienskies/core/api/ships/Ship|getTransform|()Lorg/valkyrienskies/core/api/ships/properties/ShipTransform;|true\n185|org/valkyrienskies/core/api/ships/properties/ShipTransform|getWorldToShip|()Lorg/joml/Matrix4dc;|true\n25|1\n187|org/joml/Vector3d\n89\n183|org/joml/Vector3d|<init>|()V|false\n185|org/joml/Matrix4dc|transformPosition|(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;|true\n58|8\n25|8\n185|org/joml/Vector3dc|x|()D|true\n25|8\n185|org/joml/Vector3dc|y|()D|true\n25|8\n185|org/joml/Vector3dc|z|()D|true\n184|net/minecraft/core/BlockPos|m_274561_|(DDD)Lnet/minecraft/core/BlockPos;|false\n58|9\n25|0\n180|org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|9\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n58|10\n25|10\n182|net/minecraft/world/level/block/state/BlockState|m_60795_|()Z|false\n154|72\n25|9\n176\n25|7\n185|org/valkyrienskies/core/api/ships/Ship|getTransform|()Lorg/valkyrienskies/core/api/ships/properties/ShipTransform;|true\n185|org/valkyrienskies/core/api/ships/properties/ShipTransform|getWorldToShip|()Lorg/joml/Matrix4dc;|true\n187|org/joml/Vector3d\n89\n25|1\n185|org/joml/Vector3dc|x|()D|true\n25|1\n185|org/joml/Vector3dc|y|()D|true\n15\n103\n25|1\n185|org/joml/Vector3dc|z|()D|true\n183|org/joml/Vector3d|<init>|(DDD)V|false\n185|org/joml/Matrix4dc|transformPosition|(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;|true\n58|11\n25|11\n185|org/joml/Vector3dc|x|()D|true\n25|11\n185|org/joml/Vector3dc|y|()D|true\n25|11\n185|org/joml/Vector3dc|z|()D|true\n184|net/minecraft/core/BlockPos|m_274561_|(DDD)Lnet/minecraft/core/BlockPos;|false\n58|12\n25|0\n180|org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity|f_19853_|Lnet/minecraft/world/level/Level;\n25|12\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n58|13\n25|13\n182|net/minecraft/world/level/block/state/BlockState|m_60795_|()Z|false\n154|106\n25|12\n176\n167|38\n1\n176");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getPosStandingOnFromShips","(Lorg/joml/Vector3dc;)Lnet/minecraft/core/BlockPos;",109);
methodVisitor = new MethodNode(2,"getPosStandingOnFromShips","(Lorg/joml/Vector3dc;)Lnet/minecraft/core/BlockPos;",null,[]);
methodVisitor.visitParameter("blockPosInGlobal", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "noShipAroundStandingPos", "(Lnet/minecraft/world/level/Level;Lorg/joml/Vector3dc;)Z", false);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(165, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitLdcInsn(number('0.5', NT.DOUBLE));
methodVisitor.visitVarInsn(O.DSTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(166, label1);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/primitives/AABBd");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(167, label2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "x", "()D", true);
methodVisitor.visitLdcInsn(number('0.5', NT.DOUBLE));
methodVisitor.visitInsn(O.DSUB);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "y", "()D", true);
methodVisitor.visitLdcInsn(number('0.5', NT.DOUBLE));
methodVisitor.visitInsn(O.DSUB);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "z", "()D", true);
methodVisitor.visitLdcInsn(number('0.5', NT.DOUBLE));
methodVisitor.visitInsn(O.DSUB);
methodVisitor.visitVarInsn(O.ALOAD, 1);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(168, label3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "x", "()D", true);
methodVisitor.visitLdcInsn(number('0.5', NT.DOUBLE));
methodVisitor.visitInsn(O.DADD);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "y", "()D", true);
methodVisitor.visitLdcInsn(number('0.5', NT.DOUBLE));
methodVisitor.visitInsn(O.DADD);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "z", "()D", true);
methodVisitor.visitLdcInsn(number('0.5', NT.DOUBLE));
methodVisitor.visitInsn(O.DADD);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/primitives/AABBd", "<init>", "(DDDDDD)V", false);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(170, label4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipsIntersecting", "(Lnet/minecraft/world/level/Level;Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;", false);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(171, label5);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_FULL, 6, ["org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity", "org/joml/Vector3dc", O.DOUBLE, "org/joml/primitives/AABBd", "java/lang/Iterable", "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label7);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/Ship");
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(172, label8);
methodVisitor.visitVarInsn(O.ALOAD, 7);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(173, label9);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/Ship", "getTransform", "()Lorg/valkyrienskies/core/api/ships/properties/ShipTransform;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/properties/ShipTransform", "getWorldToShip", "()Lorg/joml/Matrix4dc;", true);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/Vector3d");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/Vector3d", "<init>", "()V", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Matrix4dc", "transformPosition", "(Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", true);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(174, label10);
methodVisitor.visitVarInsn(O.ALOAD, 8);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(175, label11);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "x", "()D", true);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "y", "()D", true);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "z", "()D", true);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(174, label12);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274561_", "(DDD)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 9);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(177, label13);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_8055_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", false);
methodVisitor.visitVarInsn(O.ASTORE, 10);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(178, label14);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/block/state/BlockState", "m_60795_", "()Z", false);
var label15 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label15);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(179, label16);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(182, label15);
methodVisitor.visitFrame(O.F_FULL, 10, ["org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity", "org/joml/Vector3dc", O.DOUBLE, "org/joml/primitives/AABBd", "java/lang/Iterable", "java/util/Iterator", "org/valkyrienskies/core/api/ships/Ship", "org/joml/Vector3d", "net/minecraft/core/BlockPos", "net/minecraft/world/level/block/state/BlockState"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/Ship", "getTransform", "()Lorg/valkyrienskies/core/api/ships/properties/ShipTransform;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/api/ships/properties/ShipTransform", "getWorldToShip", "()Lorg/joml/Matrix4dc;", true);
methodVisitor.visitTypeInsn(O.NEW, "org/joml/Vector3d");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 1);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(184, label17);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "x", "()D", true);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "y", "()D", true);
methodVisitor.visitInsn(O.DCONST_1);
methodVisitor.visitInsn(O.DSUB);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "z", "()D", true);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/joml/Vector3d", "<init>", "(DDD)V", false);
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(183, label18);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Matrix4dc", "transformPosition", "(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", true);
methodVisitor.visitVarInsn(O.ASTORE, 11);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(185, label19);
methodVisitor.visitVarInsn(O.ALOAD, 11);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "x", "()D", true);
methodVisitor.visitVarInsn(O.ALOAD, 11);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "y", "()D", true);
methodVisitor.visitVarInsn(O.ALOAD, 11);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/joml/Vector3dc", "z", "()D", true);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274561_", "(DDD)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 12);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(186, label20);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity", "f_19853_", "Lnet/minecraft/world/level/Level;");
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_8055_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", false);
methodVisitor.visitVarInsn(O.ASTORE, 13);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(187, label21);
methodVisitor.visitVarInsn(O.ALOAD, 13);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/block/state/BlockState", "m_60795_", "()Z", false);
var label22 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label22);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(188, label23);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(191, label22);
methodVisitor.visitFrame(O.F_APPEND,3, ["org/joml/Vector3d", "net/minecraft/core/BlockPos", "net/minecraft/world/level/block/state/BlockState"], 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label6);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(192, label7);
methodVisitor.visitFrame(O.F_FULL, 6, ["org/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity", "org/joml/Vector3dc", O.DOUBLE, "org/joml/primitives/AABBd", "java/lang/Iterable", "java/util/Iterator"], 0, []);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitInsn(O.ARETURN);
var label24 = new Label();
methodVisitor.visitLabel(label24);
methodVisitor.visitLocalVariable("blockPosInLocal2", "Lorg/joml/Vector3dc;", null, label19, label22, 11);
methodVisitor.visitLocalVariable("blockPos2", "Lnet/minecraft/core/BlockPos;", null, label20, label22, 12);
methodVisitor.visitLocalVariable("blockState2", "Lnet/minecraft/world/level/block/state/BlockState;", null, label21, label22, 13);
methodVisitor.visitLocalVariable("blockPosInLocal", "Lorg/joml/Vector3dc;", null, label10, label22, 8);
methodVisitor.visitLocalVariable("blockPos", "Lnet/minecraft/core/BlockPos;", null, label13, label22, 9);
methodVisitor.visitLocalVariable("blockState", "Lnet/minecraft/world/level/block/state/BlockState;", null, label14, label22, 10);
methodVisitor.visitLocalVariable("ship", "Lorg/valkyrienskies/core/api/ships/Ship;", null, label8, label22, 7);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/feature/entity_collision/MixinEntity;", null, label0, label24, 0);
methodVisitor.visitLocalVariable("blockPosInGlobal", "Lorg/joml/Vector3dc;", null, label0, label24, 1);
methodVisitor.visitLocalVariable("radius", "D", null, label1, label24, 2);
methodVisitor.visitLocalVariable("testAABB", "Lorg/joml/primitives/AABBdc;", null, label4, label24, 4);
methodVisitor.visitLocalVariable("intersectingShips", "Ljava/lang/Iterable;", "Ljava/lang/Iterable<Lorg/valkyrienskies/core/api/ships/Ship;>;", label5, label24, 5);
methodVisitor.visitMaxs(16, 14);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
