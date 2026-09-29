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
var BONS_KEY = "fowlplay_air_targets_loaded_only"; var BONS_SCRIPT = "furious13-fowlplay.js"; var BONS_TARGET = "aqario.fowlplay.common.util.TargetingUtil";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"aqario.fowlplay.common.util.TargetingUtil"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n177");
bonsVerify(c,"tryFindAir","(Laqario/fowlplay/common/entity/FlyingBirdEntity;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;","25|0\n25|1\n182|aqario/fowlplay/common/util/CylindricalRadius|horizontal|()I|false\n25|0\n182|aqario/fowlplay/common/entity/FlyingBirdEntity|m_217043_|()Lnet/minecraft/util/RandomSource;|false\n25|2\n184|net/minecraft/world/entity/ai/util/RandomPos|m_217863_|(Lnet/minecraft/world/entity/PathfinderMob;ILnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|0\n25|3\n184|aqario/fowlplay/common/util/TargetingUtil|shiftPosTowardsFlyHeightRange|(Laqario/fowlplay/common/entity/FlyingBirdEntity;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|0\n182|aqario/fowlplay/common/entity/FlyingBirdEntity|m_9236_|()Lnet/minecraft/world/level/Level;|false\n178|net/minecraft/world/level/levelgen/Heightmap$Types|MOTION_BLOCKING_NO_LEAVES|Lnet/minecraft/world/level/levelgen/Heightmap$Types;\n25|3\n182|net/minecraft/core/BlockPos|m_123341_|()I|false\n25|3\n182|net/minecraft/core/BlockPos|m_123343_|()I|false\n182|net/minecraft/world/level/Level|m_6924_|(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I|false\n54|4\n25|3\n182|net/minecraft/core/BlockPos|m_123342_|()I|false\n21|4\n162|38\n25|0\n182|aqario/fowlplay/common/entity/FlyingBirdEntity|m_20186_|()D|false\n21|4\n135\n151\n155|38\n25|3\n21|4\n16|12\n96\n182|net/minecraft/core/BlockPos|m_175288_|(I)Lnet/minecraft/core/BlockPos;|false\n58|3\n167|52\n25|0\n182|aqario/fowlplay/common/entity/FlyingBirdEntity|m_20186_|()D|false\n21|4\n135\n152\n156|52\n25|3\n25|0\n182|aqario/fowlplay/common/entity/FlyingBirdEntity|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/level/Level|m_151558_|()I|false\n25|0\n186|test|(Laqario/fowlplay/common/entity/FlyingBirdEntity;)Ljava/util/function/Predicate;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)Z|aqario/fowlplay/common/util/TargetingUtil.lambda$tryFindAir$0(Laqario/fowlplay/common/entity/FlyingBirdEntity;Lnet/minecraft/core/BlockPos;)Z (6)|(Lnet/minecraft/core/BlockPos;)Z\n184|net/minecraft/world/entity/ai/util/RandomPos|m_148545_|(Lnet/minecraft/core/BlockPos;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148461_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|64\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|64\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148458_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|66\n1\n176\n25|3\n176");
bonsVerify(c,"tryFindWater","(Lnet/minecraft/world/entity/PathfinderMob;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;","25|0\n25|2\n25|1\n178|net/minecraft/world/level/levelgen/Heightmap$Types|MOTION_BLOCKING_NO_LEAVES|Lnet/minecraft/world/level/levelgen/Heightmap$Types;\n3\n25|0\n186|test|(Lnet/minecraft/world/entity/PathfinderMob;)Ljava/util/function/Predicate;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)Z|aqario/fowlplay/common/util/TargetingUtil.lambda$tryFindWater$1(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z (6)|(Lnet/minecraft/core/BlockPos;)Z\n184|aqario/fowlplay/common/util/TargetingUtil|findSurfacePosition|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/world/level/levelgen/Heightmap$Types;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|15\n1\n176\n25|3\n176");
bonsVerify(c,"tryFindNonAir","(Lnet/minecraft/world/entity/PathfinderMob;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;","25|0\n25|2\n25|1\n178|net/minecraft/world/level/levelgen/Heightmap$Types|MOTION_BLOCKING_NO_LEAVES|Lnet/minecraft/world/level/levelgen/Heightmap$Types;\n3\n25|0\n186|test|(Lnet/minecraft/world/entity/PathfinderMob;)Ljava/util/function/Predicate;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)Z|aqario/fowlplay/common/util/TargetingUtil.lambda$tryFindNonAir$2(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z (6)|(Lnet/minecraft/core/BlockPos;)Z\n184|aqario/fowlplay/common/util/TargetingUtil|findSurfacePosition|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/world/level/levelgen/Heightmap$Types;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148458_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|17\n25|0\n25|3\n184|aqario/fowlplay/common/util/TargetingUtil|isPositionNonAir|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|19\n1\n176\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|3\n182|net/minecraft/world/level/Level|m_46801_|(Lnet/minecraft/core/BlockPos;)Z|false\n153|26\n25|3\n167|28\n25|3\n182|net/minecraft/core/BlockPos|m_7494_|()Lnet/minecraft/core/BlockPos;|false\n176");
bonsVerify(c,"tryFindGround","(Lnet/minecraft/world/entity/PathfinderMob;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;","25|0\n25|2\n25|1\n178|net/minecraft/world/level/levelgen/Heightmap$Types|MOTION_BLOCKING_NO_LEAVES|Lnet/minecraft/world/level/levelgen/Heightmap$Types;\n4\n25|0\n186|test|(Lnet/minecraft/world/entity/PathfinderMob;)Ljava/util/function/Predicate;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)Z|aqario/fowlplay/common/util/TargetingUtil.lambda$tryFindGround$3(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z (6)|(Lnet/minecraft/core/BlockPos;)Z\n184|aqario/fowlplay/common/util/TargetingUtil|findSurfacePosition|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/world/level/levelgen/Heightmap$Types;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|22\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148458_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|22\n25|0\n25|3\n182|net/minecraft/core/BlockPos|m_7495_|()Lnet/minecraft/core/BlockPos;|false\n184|aqario/fowlplay/common/util/TargetingUtil|isPositionGrounded|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|24\n1\n176\n25|3\n176");
bonsVerify(c,"tryFindPerch","(Lnet/minecraft/world/entity/PathfinderMob;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;","25|0\n25|2\n25|1\n178|net/minecraft/world/level/levelgen/Heightmap$Types|MOTION_BLOCKING|Lnet/minecraft/world/level/levelgen/Heightmap$Types;\n3\n25|0\n186|test|(Lnet/minecraft/world/entity/PathfinderMob;)Ljava/util/function/Predicate;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)Z|aqario/fowlplay/common/util/TargetingUtil.lambda$tryFindPerch$4(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z (6)|(Lnet/minecraft/core/BlockPos;)Z\n184|aqario/fowlplay/common/util/TargetingUtil|findSurfacePosition|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/world/level/levelgen/Heightmap$Types;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|0\n25|3\n184|aqario/fowlplay/common/util/TargetingUtil|isPerch|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|21\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|21\n25|0\n25|3\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148458_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|23\n1\n176\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|3\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n182|net/minecraft/world/level/block/state/BlockState|m_60734_|()Lnet/minecraft/world/level/block/Block;|false\n193|net/minecraft/world/level/block/LeavesBlock\n153|32\n25|3\n167|34\n25|3\n182|net/minecraft/core/BlockPos|m_7494_|()Lnet/minecraft/core/BlockPos;|false\n176");
bonsVerify(c,"findSurfacePosition","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/world/level/levelgen/Heightmap$Types;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;","25|0\n25|2\n182|aqario/fowlplay/common/util/CylindricalRadius|horizontal|()I|false\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_217043_|()Lnet/minecraft/util/RandomSource;|false\n25|1\n184|net/minecraft/world/entity/ai/util/RandomPos|m_217863_|(Lnet/minecraft/world/entity/PathfinderMob;ILnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;|false\n58|6\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|3\n25|6\n182|net/minecraft/core/BlockPos|m_123341_|()I|false\n25|6\n182|net/minecraft/core/BlockPos|m_123343_|()I|false\n182|net/minecraft/world/level/Level|m_6924_|(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I|false\n54|7\n25|6\n182|net/minecraft/core/BlockPos|m_123342_|()I|false\n21|7\n161|30\n25|6\n21|7\n21|4\n96\n4\n100\n182|net/minecraft/core/BlockPos|m_175288_|(I)Lnet/minecraft/core/BlockPos;|false\n58|6\n167|40\n25|6\n21|7\n25|5\n184|net/minecraft/world/entity/ai/util/RandomPos|m_148545_|(Lnet/minecraft/core/BlockPos;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;|false\n178|net/minecraft/core/Direction$Axis|Y|Lnet/minecraft/core/Direction$Axis;\n21|4\n4\n100\n182|net/minecraft/core/BlockPos|m_5487_|(Lnet/minecraft/core/Direction$Axis;I)Lnet/minecraft/core/BlockPos;|false\n58|6\n25|6\n176");
bonsVerify(c,"validateBlockPos","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;)Lnet/minecraft/core/BlockPos;","25|1\n199|4\n1\n176\n25|1\n25|0\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148451_|(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/PathfinderMob;)Z|false\n154|16\n25|0\n25|2\n182|aqario/fowlplay/common/util/CylindricalRadius|horizontal|()I|false\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148442_|(Lnet/minecraft/world/entity/PathfinderMob;I)Z|false\n25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148454_|(ZLnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|18\n1\n176\n25|1\n176");
bonsVerify(c,"validatePos","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;)Lnet/minecraft/world/phys/Vec3;","25|0\n25|1\n25|2\n184|aqario/fowlplay/common/util/TargetingUtil|validateBlockPos|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;Laqario/fowlplay/common/util/CylindricalRadius;)Lnet/minecraft/core/BlockPos;|false\n58|3\n25|3\n198|10\n25|3\n184|net/minecraft/world/phys/Vec3|m_82539_|(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;|false\n167|11\n1\n176");
bonsVerify(c,"shiftPosTowardsFlyHeightRange","(Laqario/fowlplay/common/entity/FlyingBirdEntity;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;","25|1\n182|net/minecraft/core/BlockPos|m_123342_|()I|false\n54|2\n25|0\n182|aqario/fowlplay/common/entity/FlyingBirdEntity|m_217043_|()Lnet/minecraft/util/RandomSource;|false\n58|3\n25|0\n182|aqario/fowlplay/common/entity/FlyingBirdEntity|getFlyHeightRange|()Lcom/mojang/datafixers/util/Pair;|false\n58|4\n25|4\n182|com/mojang/datafixers/util/Pair|getFirst|()Ljava/lang/Object;|false\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n54|5\n25|4\n182|com/mojang/datafixers/util/Pair|getSecond|()Ljava/lang/Object;|false\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n54|6\n21|2\n21|5\n162|33\n25|1\n25|3\n8\n16|10\n185|net/minecraft/util/RandomSource|m_216332_|(II)I|true\n21|5\n21|2\n100\n184|java/lang/Math|min|(II)I|false\n182|net/minecraft/core/BlockPos|m_6630_|(I)Lnet/minecraft/core/BlockPos;|false\n176\n21|2\n21|6\n164|47\n25|1\n25|3\n8\n16|10\n185|net/minecraft/util/RandomSource|m_216332_|(II)I|true\n21|2\n21|6\n100\n184|java/lang/Math|min|(II)I|false\n182|net/minecraft/core/BlockPos|m_6625_|(I)Lnet/minecraft/core/BlockPos;|false\n176\n25|1\n176");
bonsVerify(c,"isPerch","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|1\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n178|aqario/fowlplay/core/tags/FowlPlayBlockTags|PERCHES|Lnet/minecraft/tags/TagKey;\n182|net/minecraft/world/level/block/state/BlockState|m_204336_|(Lnet/minecraft/tags/TagKey;)Z|false\n172");
bonsVerify(c,"isPositionNonAir","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n25|1\n184|aqario/fowlplay/common/util/TargetingUtil|isFullBlockAt|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|8\n25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|10\n4\n167|11\n3\n172");
bonsVerify(c,"isPositionGrounded","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n25|1\n184|aqario/fowlplay/common/util/TargetingUtil|isFullBlockAt|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n172");
bonsVerify(c,"isFullBlockAt","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|1\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|1\n182|net/minecraft/world/level/block/state/BlockState|m_60804_|(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z|false\n172");
bonsVerify(c,"isWithinAngle","(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;D)Z","25|0\n182|net/minecraft/world/phys/Vec3|m_82541_|()Lnet/minecraft/world/phys/Vec3;|false\n58|0\n25|1\n182|net/minecraft/world/phys/Vec3|m_82541_|()Lnet/minecraft/world/phys/Vec3;|false\n58|1\n25|0\n25|1\n182|net/minecraft/world/phys/Vec3|m_82526_|(Lnet/minecraft/world/phys/Vec3;)D|false\n144\n56|4\n24|2\n144\n184|net/minecraft/util/Mth|m_14089_|(F)F|false\n56|5\n23|4\n23|5\n149\n155|21\n4\n167|22\n3\n172");
bonsVerify(c,"isPosWithinViewAngle","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;D)Z","25|0\n12\n182|net/minecraft/world/entity/PathfinderMob|m_20252_|(F)Lnet/minecraft/world/phys/Vec3;|false\n58|4\n25|1\n184|net/minecraft/world/phys/Vec3|m_82512_|(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;|false\n58|5\n25|5\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_82546_|(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;|false\n58|6\n25|4\n25|6\n24|2\n184|aqario/fowlplay/common/util/TargetingUtil|isWithinAngle|(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;D)Z|false\n172");
bonsVerify(c,"lambda$tryFindPerch$4","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|1\n182|net/minecraft/core/BlockPos|m_7495_|()Lnet/minecraft/core/BlockPos;|false\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n182|net/minecraft/world/level/block/state/BlockState|m_60734_|()Lnet/minecraft/world/level/block/Block;|false\n193|net/minecraft/world/level/block/LeavesBlock\n54|2\n21|2\n153|20\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|1\n5\n182|net/minecraft/core/BlockPos|m_6625_|(I)Lnet/minecraft/core/BlockPos;|false\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n182|net/minecraft/world/level/block/state/BlockState|m_60795_|()Z|false\n154|20\n4\n167|21\n3\n54|3\n25|0\n25|1\n182|net/minecraft/core/BlockPos|m_7495_|()Lnet/minecraft/core/BlockPos;|false\n184|aqario/fowlplay/common/util/TargetingUtil|isPerch|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|35\n25|0\n182|net/minecraft/world/entity/PathfinderMob|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|1\n182|net/minecraft/world/level/Level|m_8055_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;|false\n182|net/minecraft/world/level/block/state/BlockState|m_60795_|()Z|false\n153|35\n4\n167|36\n3\n54|4\n21|2\n153|43\n21|3\n154|43\n4\n172\n21|3\n153|47\n3\n172\n21|4\n154|51\n4\n167|52\n3\n172");
bonsVerify(c,"lambda$tryFindGround$3","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148461_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n172");
bonsVerify(c,"lambda$tryFindNonAir$2","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148461_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|8\n25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|10\n4\n167|11\n3\n172");
bonsVerify(c,"lambda$tryFindWater$1","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z","25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148461_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|8\n25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|10\n4\n167|11\n3\n172");
bonsVerify(c,"lambda$tryFindAir$0","(Laqario/fowlplay/common/entity/FlyingBirdEntity;Lnet/minecraft/core/BlockPos;)Z","25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148461_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n154|8\n25|0\n25|1\n184|net/minecraft/world/entity/ai/util/GoalUtils|m_148445_|(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z|false\n153|10\n4\n167|11\n3\n172");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"tryFindAir","(Laqario/fowlplay/common/entity/FlyingBirdEntity;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",68);
methodVisitor = new MethodNode(9,"tryFindAir","(Laqario/fowlplay/common/entity/FlyingBirdEntity;Laqario/fowlplay/common/util/CylindricalRadius;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",null,[]);
methodVisitor.visitParameter("entity", 0);
methodVisitor.visitParameter("range", 0);
methodVisitor.visitParameter("pos", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/Nullable;", false);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitTypeAnnotation(335544320, null, "Lorg/jetbrains/annotations/Nullable;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(23, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(24, label1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "aqario/fowlplay/common/util/CylindricalRadius", "horizontal", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "aqario/fowlplay/common/entity/FlyingBirdEntity", "m_217043_", "()Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitVarInsn(O.ALOAD, 2);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(23, label2);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/entity/ai/util/RandomPos", "m_217863_", "(Lnet/minecraft/world/entity/PathfinderMob;ILnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "aqario/fowlplay/common/util/TargetingUtil", "bons$loaded", "(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z", false);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label3);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(26, label3);
methodVisitor.visitFrame(O.F_APPEND,1, ["net/minecraft/core/BlockPos"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "aqario/fowlplay/common/util/TargetingUtil", "shiftPosTowardsFlyHeightRange", "(Laqario/fowlplay/common/entity/FlyingBirdEntity;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(27, label4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "aqario/fowlplay/common/entity/FlyingBirdEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/world/level/levelgen/Heightmap$Types", "MOTION_BLOCKING_NO_LEAVES", "Lnet/minecraft/world/level/levelgen/Heightmap$Types;");
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123341_", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123343_", "()I", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_6924_", "(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I", false);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(28, label5);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123342_", "()I", false);
methodVisitor.visitVarInsn(O.ILOAD, 4);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "aqario/fowlplay/common/entity/FlyingBirdEntity", "m_20186_", "()D", false);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitInsn(O.I2D);
methodVisitor.visitInsn(O.DCMPL);
methodVisitor.visitJumpInsn(O.IFLT, label6);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(29, label7);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitIntInsn(O.BIPUSH, 12);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_175288_", "(I)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label8 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label8);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(33, label6);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "aqario/fowlplay/common/entity/FlyingBirdEntity", "m_20186_", "()D", false);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitInsn(O.I2D);
methodVisitor.visitInsn(O.DCMPG);
methodVisitor.visitJumpInsn(O.IFGE, label8);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(34, label9);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "aqario/fowlplay/common/entity/FlyingBirdEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_151558_", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInvokeDynamicInsn("test", "(Laqario/fowlplay/common/entity/FlyingBirdEntity;)Ljava/util/function/Predicate;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Z"), new Handle(O.H_INVOKESTATIC, "aqario/fowlplay/common/util/TargetingUtil", "lambda$tryFindAir$0", "(Laqario/fowlplay/common/entity/FlyingBirdEntity;Lnet/minecraft/core/BlockPos;)Z", false), Type.getType("(Lnet/minecraft/core/BlockPos;)Z")]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/entity/ai/util/RandomPos", "m_148545_", "(Lnet/minecraft/core/BlockPos;ILjava/util/function/Predicate;)Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(38, label8);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/entity/ai/util/GoalUtils", "m_148461_", "(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z", false);
var label10 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label10);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(39, label11);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/entity/ai/util/GoalUtils", "m_148445_", "(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label10);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(40, label12);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/entity/ai/util/GoalUtils", "m_148458_", "(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z", false);
var label13 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label13);
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(42, label10);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(44, label13);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitInsn(O.ARETURN);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLocalVariable("entity", "Laqario/fowlplay/common/entity/FlyingBirdEntity;", null, label0, label14, 0);
methodVisitor.visitLocalVariable("range", "Laqario/fowlplay/common/util/CylindricalRadius;", null, label0, label14, 1);
methodVisitor.visitLocalVariable("pos", "Lnet/minecraft/core/BlockPos;", null, label0, label14, 2);
methodVisitor.visitLocalVariable("adjustedPos", "Lnet/minecraft/core/BlockPos;", null, label3, label14, 3);
methodVisitor.visitLocalVariable("surfaceY", "I", null, label5, label14, 4);
methodVisitor.visitMaxs(4, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"bons$loaded","(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/core/BlockPos;)Z",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(20, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/PathfinderMob", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(21, label1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/ServerLevel");
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label2);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(22, label3);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(24, label2);
methodVisitor.visitFrame(O.F_APPEND,1, ["net/minecraft/world/level/Level"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/ServerLevel");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_7726_", "()Lnet/minecraft/server/level/ServerChunkCache;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123341_", "()I", false);
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitInsn(O.ISHR);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123343_", "()I", false);
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitInsn(O.ISHR);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerChunkCache", "m_7131_", "(II)Lnet/minecraft/world/level/chunk/LevelChunk;", false);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label4);
methodVisitor.visitInsn(O.ICONST_1);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label5);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(4, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
