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
var BONS_KEY = "valkyrien_chunk_bookkeeping"; var BONS_SCRIPT = "pure15-valkyrien-MixinServerLevel.js"; var BONS_TARGET = "org.valkyrienskies.mod.mixin.server.world.MixinServerLevel";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.mixin.server.world.MixinServerLevel"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n25|0\n187|java/util/HashMap\n89\n183|java/util/HashMap|<init>|()V|false\n181|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$knownChunks|Ljava/util/Map;\n25|0\n187|it/unimi/dsi/fastutil/longs/Long2LongOpenHashMap\n89\n183|it/unimi/dsi/fastutil/longs/Long2LongOpenHashMap|<init>|()V|false\n181|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$chunksToUnload|Lit/unimi/dsi/fastutil/longs/Long2LongOpenHashMap;\n177");
bonsVerify(c,"postTick","(Ljava/util/function/BooleanSupplier;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V","18|Lnet/minecraft/server/level/ServerLevel;\n25|0\n182|java/lang/Class|cast|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|net/minecraft/server/level/ServerLevel\n58|3\n25|3\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getShipObjectWorld|(Lnet/minecraft/server/level/ServerLevel;)Lorg/valkyrienskies/core/internal/world/VsiServerShipWorld;|false\n58|4\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|f_8547_|Lnet/minecraft/server/level/ServerChunkCache;\n180|net/minecraft/server/level/ServerChunkCache|f_8325_|Lnet/minecraft/server/level/ChunkMap;\n192|org/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor\n58|5\n187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|6\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|f_8547_|Lnet/minecraft/server/level/ServerChunkCache;\n180|net/minecraft/server/level/ServerChunkCache|f_8325_|Lnet/minecraft/server/level/ChunkMap;\n182|net/minecraft/server/level/ChunkMap|m_143145_|()Lnet/minecraft/server/level/DistanceManager;|false\n192|org/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor\n58|7\n25|5\n185|org/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor|callGetChunks|()Ljava/lang/Iterable;|true\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|8\n25|8\n185|java/util/Iterator|hasNext|()Z|true\n153|66\n25|8\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/server/level/ChunkHolder\n58|9\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$knownChunks|Ljava/util/Map;\n25|9\n182|net/minecraft/server/level/ChunkHolder|m_140092_|()Lnet/minecraft/world/level/ChunkPos;|false\n185|java/util/Map|containsKey|(Ljava/lang/Object;)Z|true\n154|65\n25|7\n185|org/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor|getTickets|()Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;|true\n25|9\n182|net/minecraft/server/level/ChunkHolder|m_140092_|()Lnet/minecraft/world/level/ChunkPos;|false\n182|net/minecraft/world/level/ChunkPos|m_45588_|()J|false\n182|it/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap|containsKey|(J)Z|false\n153|65\n25|9\n182|net/minecraft/server/level/ChunkHolder|m_140026_|()Ljava/util/concurrent/CompletableFuture;|false\n178|net/minecraft/server/level/ChunkHolder|f_139997_|Lcom/mojang/datafixers/util/Either;\n182|java/util/concurrent/CompletableFuture|getNow|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|com/mojang/datafixers/util/Either\n182|com/mojang/datafixers/util/Either|left|()Ljava/util/Optional;|false\n58|10\n25|10\n182|java/util/Optional|isPresent|()Z|false\n153|65\n25|10\n182|java/util/Optional|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/level/chunk/LevelChunk\n58|11\n25|0\n25|11\n25|6\n182|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$loadChunk|(Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/List;)V|false\n167|27\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$knownChunks|Ljava/util/Map;\n185|java/util/Map|entrySet|()Ljava/util/Set;|true\n185|java/util/Set|iterator|()Ljava/util/Iterator;|true\n58|8\n25|8\n185|java/util/Iterator|hasNext|()Z|true\n153|145\n25|8\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|java/util/Map$Entry\n58|9\n25|9\n185|java/util/Map$Entry|getKey|()Ljava/lang/Object;|true\n192|net/minecraft/world/level/ChunkPos\n182|net/minecraft/world/level/ChunkPos|m_45588_|()J|false\n55|10\n25|7\n185|org/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor|getTickets|()Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;|true\n22|10\n182|it/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap|containsKey|(J)Z|false\n153|92\n25|5\n22|10\n185|org/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor|callGetVisibleChunkIfPresent|(J)Lnet/minecraft/server/level/ChunkHolder;|true\n199|144\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$chunksToUnload|Lit/unimi/dsi/fastutil/longs/Long2LongOpenHashMap;\n22|10\n9\n182|it/unimi/dsi/fastutil/longs/Long2LongOpenHashMap|getOrDefault|(JJ)J|false\n55|12\n22|12\n18|100\n148\n158|136\n25|9\n185|java/util/Map$Entry|getValue|()Ljava/lang/Object;|true\n192|java/util/List\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|14\n25|14\n185|java/util/Iterator|hasNext|()Z|true\n153|128\n25|14\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|org/joml/Vector3ic\n58|15\n184|org/valkyrienskies/mod/common/ValkyrienSkiesMod|getVsCore|()Lorg/valkyrienskies/core/internal/VsiCore;|false\n25|15\n185|org/joml/Vector3ic|x|()I|true\n25|15\n185|org/joml/Vector3ic|y|()I|true\n25|15\n185|org/joml/Vector3ic|z|()I|true\n185|org/valkyrienskies/core/internal/VsiCore|newDeleteTerrainUpdate|(III)Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate;|true\n58|16\n25|6\n25|16\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n167|107\n25|8\n185|java/util/Iterator|remove|()V|true\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$chunksToUnload|Lit/unimi/dsi/fastutil/longs/Long2LongOpenHashMap;\n22|10\n182|it/unimi/dsi/fastutil/longs/Long2LongOpenHashMap|remove|(J)J|false\n88\n167|144\n25|0\n180|org/valkyrienskies/mod/mixin/server/world/MixinServerLevel|vs$chunksToUnload|Lit/unimi/dsi/fastutil/longs/Long2LongOpenHashMap;\n22|10\n22|12\n10\n97\n182|it/unimi/dsi/fastutil/longs/Long2LongOpenHashMap|put|(JJ)J|false\n88\n167|71\n25|4\n25|3\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|getDimensionId|(Lnet/minecraft/world/level/Level;)Ljava/lang/String;|false\n25|6\n185|org/valkyrienskies/core/internal/world/VsiServerShipWorld|addTerrainUpdates|(Ljava/lang/String;Ljava/util/List;)V|true\n178|org/valkyrienskies/core/impl/config/VSCoreConfig|SERVER|Lorg/valkyrienskies/core/impl/config/VSCoreConfig$Server;\n182|org/valkyrienskies/core/impl/config/VSCoreConfig$Server|getSp|()Lorg/valkyrienskies/core/impl/config/VSCoreConfig$Server$ConnectivitySettings;|false\n182|org/valkyrienskies/core/impl/config/VSCoreConfig$Server$ConnectivitySettings|getEnableSplitting|()Z|false\n153|160\n178|org/valkyrienskies/mod/common/ValkyrienSkiesMod|splitHandler|Lorg/valkyrienskies/mod/common/util/SplitHandler;\n18|Lnet/minecraft/server/level/ServerLevel;\n25|0\n182|java/lang/Class|cast|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|net/minecraft/server/level/ServerLevel\n182|org/valkyrienskies/mod/common/util/SplitHandler|tick|(Lnet/minecraft/server/level/ServerLevel;)V|false\n178|org/valkyrienskies/mod/common/util/DragInfoReporter|INSTANCE|Lorg/valkyrienskies/mod/common/util/DragInfoReporter;\n25|0\n192|net/minecraft/server/level/ServerLevel\n182|org/valkyrienskies/mod/common/util/DragInfoReporter|tick|(Lnet/minecraft/server/level/ServerLevel;)V|false\n177");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"<init>","()V",13);
removeMethod(c,"postTick","(Ljava/util/function/BooleanSupplier;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",165);
methodVisitor = new MethodNode(1,"<init>","()V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(65, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(79, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitTypeInsn(O.NEW, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsKnownChunks");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsKnownChunks", "<init>", "()V", false);
methodVisitor.visitFieldInsn(O.PUTFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "vs$knownChunks", "Ljava/util/Map;");
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(83, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitTypeInsn(O.NEW, "it/unimi/dsi/fastutil/longs/Long2LongOpenHashMap");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "it/unimi/dsi/fastutil/longs/Long2LongOpenHashMap", "<init>", "()V", false);
methodVisitor.visitFieldInsn(O.PUTFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "vs$chunksToUnload", "Lit/unimi/dsi/fastutil/longs/Long2LongOpenHashMap;");
methodVisitor.visitInsn(O.RETURN);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/server/world/MixinServerLevel;", null, label0, label3, 0);
methodVisitor.visitMaxs(3, 1);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(2,"postTick","(Ljava/util/function/BooleanSupplier;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,[]);
methodVisitor.visitParameter("shouldKeepTicking", 0);
methodVisitor.visitParameter("ci", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "tick");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "TAIL");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(241, label0);
methodVisitor.visitLdcInsn(Type.getType("Lnet/minecraft/server/level/ServerLevel;"));
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Class", "cast", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/ServerLevel");
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(242, label1);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getShipObjectWorld", "(Lnet/minecraft/server/level/ServerLevel;)Lorg/valkyrienskies/core/internal/world/VsiServerShipWorld;", false);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(244, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "f_8547_", "Lnet/minecraft/server/level/ServerChunkCache;");
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/server/level/ServerChunkCache", "f_8325_", "Lnet/minecraft/server/level/ChunkMap;");
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor");
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(248, label3);
methodVisitor.visitTypeInsn(O.NEW, "java/util/ArrayList");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(249, label4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "f_8547_", "Lnet/minecraft/server/level/ServerChunkCache;");
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/server/level/ServerChunkCache", "f_8325_", "Lnet/minecraft/server/level/ChunkMap;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ChunkMap", "m_143145_", "()Lnet/minecraft/server/level/DistanceManager;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "org/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor");
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(251, label5);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor", "getTickets", "()Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;", true);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "vs$knownChunks", "Ljava/util/Map;");
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep5", "freshTicketedChunks", "(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Ljava/util/Map;Lorg/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor;)Ljava/util/List;", false);
methodVisitor.visitVarInsn(O.ASTORE, 8);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 9);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_FULL, 10, ["org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "java/util/function/BooleanSupplier", "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "net/minecraft/server/level/ServerLevel", "org/valkyrienskies/core/internal/world/VsiServerShipWorld", "org/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor", "java/util/ArrayList", "org/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor", "java/util/List", O.INTEGER], 0, []);
methodVisitor.visitVarInsn(O.ILOAD, 9);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label7);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ILOAD, 9);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/level/chunk/LevelChunk");
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "vs$loadChunk", "(Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/List;)V", false);
methodVisitor.visitIincInsn(9, 1);
methodVisitor.visitJumpInsn(O.GOTO, label6);
methodVisitor.visitLabel(label7);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor", "getTickets", "()Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;", true);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "vs$knownChunks", "Ljava/util/Map;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/server/world/MixinServerLevel", "vs$chunksToUnload", "Lit/unimi/dsi/fastutil/longs/Long2LongOpenHashMap;");
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep5", "unloadStale", "(Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;Lorg/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor;Ljava/util/Map;Lit/unimi/dsi/fastutil/longs/Long2LongOpenHashMap;Ljava/util/List;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 3);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(288, label8);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/valkyrienskies/mod/common/VSGameUtilsKt", "getDimensionId", "(Lnet/minecraft/world/level/Level;)Ljava/lang/String;", false);
methodVisitor.visitVarInsn(O.ALOAD, 6);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(287, label9);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "org/valkyrienskies/core/internal/world/VsiServerShipWorld", "addTerrainUpdates", "(Ljava/lang/String;Ljava/util/List;)V", true);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(292, label10);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/core/impl/config/VSCoreConfig", "SERVER", "Lorg/valkyrienskies/core/impl/config/VSCoreConfig$Server;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/core/impl/config/VSCoreConfig$Server", "getSp", "()Lorg/valkyrienskies/core/impl/config/VSCoreConfig$Server$ConnectivitySettings;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/core/impl/config/VSCoreConfig$Server$ConnectivitySettings", "getEnableSplitting", "()Z", false);
var label11 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label11);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(293, label12);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/mod/common/ValkyrienSkiesMod", "splitHandler", "Lorg/valkyrienskies/mod/common/util/SplitHandler;");
methodVisitor.visitLdcInsn(Type.getType("Lnet/minecraft/server/level/ServerLevel;"));
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Class", "cast", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/ServerLevel");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/common/util/SplitHandler", "tick", "(Lnet/minecraft/server/level/ServerLevel;)V", false);
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(296, label11);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/mod/common/util/DragInfoReporter", "INSTANCE", "Lorg/valkyrienskies/mod/common/util/DragInfoReporter;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/ServerLevel");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/common/util/DragInfoReporter", "tick", "(Lnet/minecraft/server/level/ServerLevel;)V", false);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(298, label13);
methodVisitor.visitInsn(O.RETURN);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/server/world/MixinServerLevel;", null, label0, label14, 0);
methodVisitor.visitLocalVariable("shouldKeepTicking", "Ljava/util/function/BooleanSupplier;", null, label0, label14, 1);
methodVisitor.visitLocalVariable("ci", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;", null, label0, label14, 2);
methodVisitor.visitLocalVariable("self", "Lnet/minecraft/server/level/ServerLevel;", null, label1, label14, 3);
methodVisitor.visitLocalVariable("shipObjectWorld", "Lorg/valkyrienskies/core/internal/world/VsiServerShipWorld;", null, label2, label14, 4);
methodVisitor.visitLocalVariable("chunkMapAccessor", "Lorg/valkyrienskies/mod/mixin/accessors/server/level/ChunkMapAccessor;", null, label3, label14, 5);
methodVisitor.visitLocalVariable("voxelShapeUpdates", "Ljava/util/List;", "Ljava/util/List<Lorg/valkyrienskies/core/internal/world/chunks/VsiTerrainUpdate;>;", label4, label14, 6);
methodVisitor.visitLocalVariable("distanceManagerAccessor", "Lorg/valkyrienskies/mod/mixin/accessors/server/level/DistanceManagerAccessor;", null, label5, label14, 7);
methodVisitor.visitMaxs(5, 10);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
