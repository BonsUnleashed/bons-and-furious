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
var BONS_KEY = "valkyrien_spawn_distance"; var BONS_SCRIPT = "furious15-valkyrien-MixinChunkMap.js"; var BONS_TARGET = "org.valkyrienskies.mod.mixin.server.world.MixinChunkMap";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.mixin.server.world.MixinChunkMap"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"onHasPlayersNearby","(Lnet/minecraft/server/level/ChunkMap$DistanceManager;JLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/world/level/ChunkPos;)Z","25|4\n5\n189|java/lang/Object\n89\n3\n25|1\n83\n89\n4\n187|net/minecraft/world/level/ChunkPos\n89\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinChunkMap|f_140133_|Lnet/minecraft/server/level/ServerLevel;\n25|5\n16|63\n182|net/minecraft/world/level/ChunkPos|m_151394_|(I)Lnet/minecraft/core/BlockPos;|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|toWorldCoordinates|(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;|false\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n183|net/minecraft/world/level/ChunkPos|<init>|(Lnet/minecraft/core/BlockPos;)V|false\n182|net/minecraft/world/level/ChunkPos|m_45588_|()J|false\n184|java/lang/Long|valueOf|(J)Ljava/lang/Long;|false\n83\n185|com/llamalad7/mixinextras/injector/wrapoperation/Operation|call|([Ljava/lang/Object;)Ljava/lang/Object;|true\n192|java/lang/Boolean\n182|java/lang/Boolean|booleanValue|()Z|false\n172");
bonsVerify(c,"onEuclideanDistanceSquared","(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/Entity;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)D","25|3\n5\n189|java/lang/Object\n89\n3\n187|net/minecraft/world/level/ChunkPos\n89\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinChunkMap|f_140133_|Lnet/minecraft/server/level/ServerLevel;\n25|1\n16|63\n182|net/minecraft/world/level/ChunkPos|m_151394_|(I)Lnet/minecraft/core/BlockPos;|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|toWorldCoordinates|(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;|false\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n183|net/minecraft/world/level/ChunkPos|<init>|(Lnet/minecraft/core/BlockPos;)V|false\n83\n89\n4\n25|2\n83\n185|com/llamalad7/mixinextras/injector/wrapoperation/Operation|call|([Ljava/lang/Object;)Ljava/lang/Object;|true\n192|java/lang/Double\n182|java/lang/Double|doubleValue|()D|false\n175");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"onHasPlayersNearby","(Lnet/minecraft/server/level/ChunkMap$DistanceManager;JLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/world/level/ChunkPos;)Z",26);
removeMethod(c,"onEuclideanDistanceSquared","(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/Entity;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)D",24);
methodVisitor = new MethodNode(2,"onHasPlayersNearby","(Lnet/minecraft/server/level/ChunkMap$DistanceManager;JLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/world/level/ChunkPos;)Z","(Lnet/minecraft/server/level/ChunkMap$DistanceManager;JLcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Ljava/lang/Boolean;>;Lnet/minecraft/world/level/ChunkPos;)Z",[]);
methodVisitor.visitParameter("instance", 0);
methodVisitor.visitParameter("l", 0);
methodVisitor.visitParameter("original", 0);
methodVisitor.visitParameter("arg", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "anyPlayerCloseEnoughForSpawning");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "INVOKE");
annotationVisitor2.visit("target", "Lnet/minecraft/server/level/ChunkMap$DistanceManager;hasPlayersNearby(J)Z");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(4, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(3, "Lcom/llamalad7/mixinextras/sugar/Local;", false);
annotationVisitor0.visit("argsOnly", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinChunkMap", "f_140133_", "Lnet/minecraft/server/level/ServerLevel;");
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep5", "spawnChunkOnShip", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)Z", false);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label0);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "m_45588_", "()J", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Boolean");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(117, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitTypeInsn(O.NEW, "net/minecraft/world/level/ChunkPos");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinChunkMap", "f_140133_", "Lnet/minecraft/server/level/ServerLevel;");
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitIntInsn(O.BIPUSH, 63);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "m_151394_", "(I)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "toWorldCoordinates", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/world/level/ChunkPos", "<init>", "(Lnet/minecraft/core/BlockPos;)V", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "m_45588_", "()J", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Boolean");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
methodVisitor.visitInsn(O.IRETURN);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/server/world/MixinChunkMap;", null, label0, label1, 0);
methodVisitor.visitLocalVariable("instance", "Lnet/minecraft/server/level/ChunkMap$DistanceManager;", null, label0, label1, 1);
methodVisitor.visitLocalVariable("l", "J", null, label0, label1, 2);
methodVisitor.visitLocalVariable("original", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Ljava/lang/Boolean;>;", label0, label1, 4);
methodVisitor.visitLocalVariable("arg", "Lnet/minecraft/world/level/ChunkPos;", null, label0, label1, 5);
methodVisitor.visitMaxs(9, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(2,"onEuclideanDistanceSquared","(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/Entity;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)D","(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/Entity;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Ljava/lang/Double;>;)D",[]);
methodVisitor.visitParameter("d0", 0);
methodVisitor.visitParameter("d1", 0);
methodVisitor.visitParameter("original", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "playerIsCloseEnoughForSpawning");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "INVOKE");
annotationVisitor2.visit("target", "Lnet/minecraft/server/level/ChunkMap;euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/Entity;)D");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinChunkMap", "f_140133_", "Lnet/minecraft/server/level/ServerLevel;");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep5", "spawnChunkOnShip", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)Z", false);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Double");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
methodVisitor.visitInsn(O.DRETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(122, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitTypeInsn(O.NEW, "net/minecraft/world/level/ChunkPos");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinChunkMap", "f_140133_", "Lnet/minecraft/server/level/ServerLevel;");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitIntInsn(O.BIPUSH, 63);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/ChunkPos", "m_151394_", "(I)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "toWorldCoordinates", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/world/level/ChunkPos", "<init>", "(Lnet/minecraft/core/BlockPos;)V", false);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Double");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
methodVisitor.visitInsn(O.DRETURN);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/server/world/MixinChunkMap;", null, label0, label1, 0);
methodVisitor.visitLocalVariable("d0", "Lnet/minecraft/world/level/ChunkPos;", null, label0, label1, 1);
methodVisitor.visitLocalVariable("d1", "Lnet/minecraft/world/entity/Entity;", null, label0, label1, 2);
methodVisitor.visitLocalVariable("original", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Ljava/lang/Double;>;", label0, label1, 3);
methodVisitor.visitMaxs(9, 4);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
