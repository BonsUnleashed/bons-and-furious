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
var BONS_KEY = "valkyrien_entity_handlers"; var BONS_SCRIPT = "pure15-valkyrien-VSEntityManager.js"; var BONS_TARGET = "org.valkyrienskies.mod.common.entity.handling.VSEntityManager";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.common.entity.handling.VSEntityManager"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"getDefaultHandler","(Lnet/minecraft/world/entity/Entity;)Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;","178|org/valkyrienskies/mod/common/entity/handling/VSEntityManager|defaultHandlersCache|Lcom/google/common/cache/Cache;\n25|1\n182|net/minecraft/world/entity/Entity|m_6095_|()Lnet/minecraft/world/entity/EntityType;|false\n25|1\n186|call|(Lnet/minecraft/world/entity/Entity;)Ljava/util/concurrent/Callable;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|()Ljava/lang/Object;|org/valkyrienskies/mod/common/entity/handling/VSEntityManager.getDefaultHandler$lambda$0(Lnet/minecraft/world/entity/Entity;)Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler; (6)|()Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;\n185|com/google/common/cache/Cache|get|(Ljava/lang/Object;Ljava/util/concurrent/Callable;)Ljava/lang/Object;|true\n89\n18|get(...)\n184|kotlin/jvm/internal/Intrinsics|checkNotNullExpressionValue|(Ljava/lang/Object;Ljava/lang/String;)V|false\n192|org/valkyrienskies/mod/common/entity/handling/VSEntityHandler\n176");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getDefaultHandler","(Lnet/minecraft/world/entity/Entity;)Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;",11);
methodVisitor = new MethodNode(18,"getDefaultHandler","(Lnet/minecraft/world/entity/Entity;)Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;",null,[]);
methodVisitor.visitParameter("entity", 0);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_6095_", "()Lnet/minecraft/world/entity/EntityType;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep5", "cachedDefaultHandler", "(Lnet/minecraft/world/entity/EntityType;)Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;", false);
methodVisitor.visitInsn(O.DUP);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label0);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "org/valkyrienskies/mod/common/entity/handling/VSEntityManager", "determineDefaultHandler", "(Lnet/minecraft/world/entity/Entity;)Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep5", "storeDefaultHandler", "(Lnet/minecraft/world/entity/EntityType;Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;)Lorg/valkyrienskies/mod/common/entity/handling/VSEntityHandler;", false);
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_FULL, 3, ["org/valkyrienskies/mod/common/entity/handling/VSEntityManager", "net/minecraft/world/entity/Entity", "net/minecraft/world/entity/EntityType"], 1, ["org/valkyrienskies/mod/common/entity/handling/VSEntityHandler"]);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(3, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
