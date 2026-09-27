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
var BONS_KEY = "valkyrien_sculk_vibrations"; var BONS_SCRIPT = "pure15-valkyrien-retired.js"; var BONS_TARGET = "org.valkyrienskies.mod.mixin.feature.sculk.MixinVibrationSystemTicker";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.mixin.feature.sculk.MixinVibrationSystemTicker"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"destWorldPos","(Lnet/minecraft/world/level/gameevent/vibrations/VibrationInfo;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/phys/Vec3;","25|2\n25|1\n4\n189|java/lang/Object\n89\n3\n25|0\n83\n185|com/llamalad7/mixinextras/injector/wrapoperation/Operation|call|([Ljava/lang/Object;)Ljava/lang/Object;|true\n192|net/minecraft/world/phys/Vec3\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|toWorldCoordinates|(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;|false\n176");
bonsVerify(c,"destSourcePos","(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;","25|1\n4\n189|java/lang/Object\n89\n3\n25|0\n83\n185|com/llamalad7/mixinextras/injector/wrapoperation/Operation|call|([Ljava/lang/Object;)Ljava/lang/Object;|true\n192|net/minecraft/world/level/gameevent/PositionSource\n58|3\n25|3\n25|2\n185|net/minecraft/world/level/gameevent/PositionSource|m_142502_|(Lnet/minecraft/world/level/Level;)Ljava/util/Optional;|true\n58|4\n25|4\n182|java/util/Optional|isPresent|()Z|false\n153|27\n187|net/minecraft/world/level/gameevent/BlockPositionSource\n89\n25|2\n25|4\n182|java/util/Optional|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/phys/Vec3\n184|org/valkyrienskies/mod/common/VSGameUtilsKt|toWorldCoordinates|(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;|false\n184|net/minecraft/core/BlockPos|m_274446_|(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;|false\n183|net/minecraft/world/level/gameevent/BlockPositionSource|<init>|(Lnet/minecraft/core/BlockPos;)V|false\n176\n25|3\n176");
bonsVerify(c,"destReloadSourcePos","(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;","25|0\n25|1\n25|2\n184|org/valkyrienskies/mod/mixin/feature/sculk/MixinVibrationSystemTicker|destSourcePos|(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;|true\n176");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"destWorldPos","(Lnet/minecraft/world/level/gameevent/vibrations/VibrationInfo;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/phys/Vec3;",12);
removeMethod(c,"destSourcePos","(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;",29);
removeMethod(c,"destReloadSourcePos","(Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/level/gameevent/PositionSource;",5);
return c;
});}}};}
