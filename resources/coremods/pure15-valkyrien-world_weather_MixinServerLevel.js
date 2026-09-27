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
var BONS_KEY = "valkyrien_weather_occlusion"; var BONS_SCRIPT = "pure15-valkyrien-world_weather_MixinServerLevel.js"; var BONS_TARGET = "org.valkyrienskies.mod.mixin.feature.world_weather.MixinServerLevel";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.mixin.feature.world_weather.MixinServerLevel"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"occlude","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/world/level/chunk/LevelChunk;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Lnet/minecraft/core/BlockPos;","25|4\n6\n189|java/lang/Object\n89\n3\n25|1\n83\n89\n4\n25|2\n83\n89\n5\n25|3\n83\n185|com/llamalad7/mixinextras/injector/wrapoperation/Operation|call|([Ljava/lang/Object;)Ljava/lang/Object;|true\n192|net/minecraft/core/BlockPos\n58|7\n178|net/minecraft/core/BlockPos|f_121853_|Lnet/minecraft/core/BlockPos;\n25|1\n182|net/minecraft/server/level/ServerLevel|m_141937_|()I|false\n4\n100\n182|net/minecraft/core/BlockPos|m_6630_|(I)Lnet/minecraft/core/BlockPos;|false\n58|8\n25|1\n25|5\n182|net/minecraft/world/level/chunk/LevelChunk|m_7697_|()Lnet/minecraft/world/level/ChunkPos;|false\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipManagingPos|(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ChunkPos;)Lorg/valkyrienskies/core/api/ships/ServerShip;|false\n58|9\n25|6\n25|9\n185|com/llamalad7/mixinextras/sugar/ref/LocalRef|set|(Ljava/lang/Object;)V|true\n25|9\n198|70\n178|org/valkyrienskies/mod/common/CompatUtil|INSTANCE|Lorg/valkyrienskies/mod/common/CompatUtil;\n25|1\n25|7\n182|net/minecraft/core/BlockPos|m_252807_|()Lnet/minecraft/world/phys/Vec3;|false\n1\n192|org/valkyrienskies/core/api/ships/Ship\n25|9\n182|org/valkyrienskies/mod/common/CompatUtil|toSameSpaceAs|(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lorg/valkyrienskies/core/api/ships/Ship;Lorg/valkyrienskies/core/api/ships/Ship;)Lnet/minecraft/world/phys/Vec3;|false\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n58|10\n25|4\n6\n189|java/lang/Object\n89\n3\n25|1\n83\n89\n4\n25|2\n83\n89\n5\n25|10\n83\n185|com/llamalad7/mixinextras/injector/wrapoperation/Operation|call|([Ljava/lang/Object;)Ljava/lang/Object;|true\n192|net/minecraft/core/BlockPos\n58|11\n25|11\n182|net/minecraft/core/BlockPos|m_123342_|()I|false\n25|10\n182|net/minecraft/core/BlockPos|m_123342_|()I|false\n164|70\n25|8\n176\n25|7\n176");
bonsVerify(c,"useBiomeAtWorldPos","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Lnet/minecraft/core/Holder;","25|3\n5\n189|java/lang/Object\n89\n3\n25|1\n83\n89\n4\n178|org/valkyrienskies/mod/common/CompatUtil|INSTANCE|Lorg/valkyrienskies/mod/common/CompatUtil;\n25|1\n25|2\n182|net/minecraft/core/BlockPos|m_252807_|()Lnet/minecraft/world/phys/Vec3;|false\n1\n192|org/valkyrienskies/core/api/ships/Ship\n25|4\n185|com/llamalad7/mixinextras/sugar/ref/LocalRef|get|()Ljava/lang/Object;|true\n192|org/valkyrienskies/core/api/ships/Ship\n182|org/valkyrienskies/mod/common/CompatUtil|toSameSpaceAs|(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lorg/valkyrienskies/core/api/ships/Ship;Lorg/valkyrienskies/core/api/ships/Ship;)Lnet/minecraft/world/phys/Vec3;|false\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n83\n185|com/llamalad7/mixinextras/injector/wrapoperation/Operation|call|([Ljava/lang/Object;)Ljava/lang/Object;|true\n192|net/minecraft/core/Holder\n176");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"occlude","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/world/level/chunk/LevelChunk;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Lnet/minecraft/core/BlockPos;",72);
removeMethod(c,"useBiomeAtWorldPos","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Lnet/minecraft/core/Holder;",24);
methodVisitor = new MethodNode(2,"occlude","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/world/level/chunk/LevelChunk;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Lnet/minecraft/core/BlockPos;","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Lnet/minecraft/core/BlockPos;>;Lnet/minecraft/world/level/chunk/LevelChunk;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef<Lorg/valkyrienskies/core/api/ships/Ship;>;)Lnet/minecraft/core/BlockPos;",[]);
methodVisitor.visitParameter("level", 0);
methodVisitor.visitParameter("types", 0);
methodVisitor.visitParameter("pos", 0);
methodVisitor.visitParameter("original", 0);
methodVisitor.visitParameter("chunk", 0);
methodVisitor.visitParameter("shipRef", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "tickChunk");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "INVOKE");
annotationVisitor2.visit("target", "Lnet/minecraft/server/level/ServerLevel;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(6, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(4, "Lcom/llamalad7/mixinextras/sugar/Local;", false);
annotationVisitor0.visit("argsOnly", true);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(5, "Lcom/llamalad7/mixinextras/sugar/Share;", false);
annotationVisitor0.visit("value", "ship");
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(29, label0);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInsn(O.ICONST_3);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/core/BlockPos");
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(30, label1);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/core/BlockPos", "f_121853_", "Lnet/minecraft/core/BlockPos;");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_141937_", "()I", false);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.ISUB);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_6630_", "(I)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(33, label2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "m_7697_", "()Lnet/minecraft/world/level/ChunkPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipManagingPos", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ChunkPos;)Lorg/valkyrienskies/core/api/ships/ServerShip;", false);
methodVisitor.visitVarInsn(O.ASTORE, 9);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(34, label3);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "set", "(Ljava/lang/Object;)V", true);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(35, label4);
methodVisitor.visitVarInsn(O.ALOAD, 9);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label5);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(36, label6);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/mod/common/CompatUtil", "INSTANCE", "Lorg/valkyrienskies/mod/common/CompatUtil;");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_252807_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/Ship");
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/common/CompatUtil", "toSameSpaceAs", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lorg/valkyrienskies/core/api/ships/Ship;Lorg/valkyrienskies/core/api/ships/Ship;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 10);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(37, label7);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInsn(O.ICONST_3);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/core/BlockPos");
methodVisitor.visitVarInsn(O.ASTORE, 11);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(38, label8);
methodVisitor.visitVarInsn(O.ALOAD, 11);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123342_", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123342_", "()I", false);
methodVisitor.visitJumpInsn(O.IF_ICMPLE, label5);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(40, label9);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "markOccluded", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(43, label5);
methodVisitor.visitFrame(O.F_APPEND,3, ["net/minecraft/core/BlockPos", "net/minecraft/core/BlockPos", "org/valkyrienskies/core/api/ships/ServerShip"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitInsn(O.ARETURN);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLocalVariable("worldPos", "Lnet/minecraft/core/BlockPos;", null, label7, label5, 10);
methodVisitor.visitLocalVariable("worldHeight", "Lnet/minecraft/core/BlockPos;", null, label8, label5, 11);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/feature/world_weather/MixinServerLevel;", null, label0, label10, 0);
methodVisitor.visitLocalVariable("level", "Lnet/minecraft/server/level/ServerLevel;", null, label0, label10, 1);
methodVisitor.visitLocalVariable("types", "Lnet/minecraft/world/level/levelgen/Heightmap$Types;", null, label0, label10, 2);
methodVisitor.visitLocalVariable("pos", "Lnet/minecraft/core/BlockPos;", null, label0, label10, 3);
methodVisitor.visitLocalVariable("original", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Lnet/minecraft/core/BlockPos;>;", label0, label10, 4);
methodVisitor.visitLocalVariable("chunk", "Lnet/minecraft/world/level/chunk/LevelChunk;", null, label0, label10, 5);
methodVisitor.visitLocalVariable("shipRef", "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;", "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef<Lorg/valkyrienskies/core/api/ships/Ship;>;", label0, label10, 6);
methodVisitor.visitLocalVariable("result", "Lnet/minecraft/core/BlockPos;", null, label1, label10, 7);
methodVisitor.visitLocalVariable("failure", "Lnet/minecraft/core/BlockPos;", null, label2, label10, 8);
methodVisitor.visitLocalVariable("ship", "Lorg/valkyrienskies/core/api/ships/Ship;", null, label3, label10, 9);
methodVisitor.visitMaxs(5, 12);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(2,"useBiomeAtWorldPos","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;)Lnet/minecraft/core/Holder;","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Lnet/minecraft/core/Holder<Lnet/minecraft/world/level/biome/Biome;>;>;Lcom/llamalad7/mixinextras/sugar/ref/LocalRef<Lorg/valkyrienskies/core/api/ships/Ship;>;)Lnet/minecraft/core/Holder<Lnet/minecraft/world/level/biome/Biome;>;",[]);
methodVisitor.visitParameter("level", 0);
methodVisitor.visitParameter("pos", 0);
methodVisitor.visitParameter("original", 0);
methodVisitor.visitParameter("shipRef", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "tickChunk");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "INVOKE");
annotationVisitor2.visit("target", "Lnet/minecraft/server/level/ServerLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(4, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(3, "Lcom/llamalad7/mixinextras/sugar/Share;", false);
annotationVisitor0.visit("value", "ship");
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep6", "occludedProbe", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 5);
methodVisitor.visitVarInsn(O.ALOAD, 5);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label0);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "get", "()Ljava/lang/Object;", true);
methodVisitor.visitJumpInsn(O.IFNULL, label0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/core/Holder");
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(48, label0);
methodVisitor.visitFrame(O.F_APPEND,1, ["net/minecraft/core/BlockPos"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/Object");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/mod/common/CompatUtil", "INSTANCE", "Lorg/valkyrienskies/mod/common/CompatUtil;");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_252807_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/Ship");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "get", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/core/api/ships/Ship");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/common/CompatUtil", "toSameSpaceAs", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lorg/valkyrienskies/core/api/ships/Ship;Lorg/valkyrienskies/core/api/ships/Ship;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/core/BlockPos", "m_274446_", "(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/core/Holder");
methodVisitor.visitInsn(O.ARETURN);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/feature/world_weather/MixinServerLevel;", null, label0, label1, 0);
methodVisitor.visitLocalVariable("level", "Lnet/minecraft/server/level/ServerLevel;", null, label0, label1, 1);
methodVisitor.visitLocalVariable("pos", "Lnet/minecraft/core/BlockPos;", null, label0, label1, 2);
methodVisitor.visitLocalVariable("original", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation<Lnet/minecraft/core/Holder<Lnet/minecraft/world/level/biome/Biome;>;>;", label0, label1, 3);
methodVisitor.visitLocalVariable("shipRef", "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;", "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef<Lorg/valkyrienskies/core/api/ships/Ship;>;", label0, label1, 4);
methodVisitor.visitMaxs(9, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
