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

var BONS_KEY="scuba_gear_generation_context";var BONS_SCRIPT="bons_and_furious-ScubaEvents.js";var BONS_TARGET="com.legacy.scuba_gear.ScubaEvents";
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
 if(bonsSha256(result.join('\n'))!==expected)throw new Error('Opcode/operand mismatch '+name+desc);
}

// SHA-256 over UTF-8 text, implemented here for Forge's restricted ES5 sandbox.
function bonsSha256(value) {
    var text = unescape(encodeURIComponent(String(value))), bytes = [], i, j;
    for (i = 0; i < text.length; i++) bytes.push(text.charCodeAt(i));
    var length = bytes.length;
    bytes.push(128);
    while (bytes.length % 64 !== 56) bytes.push(0);
    var high = Math.floor(length / 536870912), low = (length * 8) >>> 0;
    for (i = 3; i >= 0; i--) bytes.push((high >>> (i * 8)) & 255);
    for (i = 3; i >= 0; i--) bytes.push((low >>> (i * 8)) & 255);
    var h = [0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
    var k = [0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
    function r(x, n) { return (x >>> n) | (x << (32 - n)); }
    for (i = 0; i < bytes.length; i += 64) {
        var w = [];
        for (j = 0; j < 16; j++) {
            var p = i + j * 4;
            w[j] = (bytes[p] << 24) | (bytes[p+1] << 16) | (bytes[p+2] << 8) | bytes[p+3];
        }
        for (j = 16; j < 64; j++) {
            var x = w[j-15], y = w[j-2];
            w[j] = (w[j-16] + (r(x,7)^r(x,18)^(x>>>3)) + w[j-7] + (r(y,17)^r(y,19)^(y>>>10))) | 0;
        }
        var a=h[0], b=h[1], c=h[2], d=h[3], e=h[4], f=h[5], g=h[6], v=h[7];
        for (j = 0; j < 64; j++) {
            var t1=(v+(r(e,6)^r(e,11)^r(e,25))+((e&f)^((~e)&g))+k[j]+w[j])|0;
            var t2=((r(a,2)^r(a,13)^r(a,22))+((a&b)^(a&c)^(b&c)))|0;
            v=g; g=f; f=e; e=(d+t1)|0; d=c; c=b; b=a; a=(t1+t2)|0;
        }
        var state=[a,b,c,d,e,f,g,v];
        for (j=0;j<8;j++) h[j]=(h[j]+state[j])|0;
    }
    var out='';
    for (i=0;i<8;i++) out+=('00000000'+(h[i]>>>0).toString(16)).slice(-8);
    return out;
}

// Reuse instructions from the actual, fingerprint-checked installed class.
var BONS_MAP = Java.type('java.util.HashMap');
var BONS_LABEL_NODE = Java.type('org.objectweb.asm.tree.LabelNode');
function bonsCapture(c) {
  var result={};
  for(var m=0;m<c.methods.size();m++) {
    var nodes=c.methods.get(m).instructions.toArray(), code=[];
    for(var n=0;n<nodes.length;n++)if(nodes[n].getOpcode()>=0)code.push(nodes[n]);
    var method=c.methods.get(m);result['$'+method.name+method.desc]=code;
  }
  return result;
}
function bonsReuse(original,method,index,visitor,labels) {
  var node=original['$'+method][index], map=new BONS_MAP(), old=[];
  if(node.getType()===7)old.push(node.label);
  else if(node.getType()===11 || node.getType()===12) {
    old.push(node.dflt);
    for(var i=0;i<node.labels.size();i++)old.push(node.labels.get(i));
  }
  for(var j=0;j<old.length;j++)map.put(old[j],new BONS_LABEL_NODE(labels[j]));
  node.clone(map).accept(visitor);
}

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.legacy.scuba_gear.ScubaEvents"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","5942991974f47333822ecb04a032adb3b9163f71c0d69d81b0c174bd3882a02c");
bonsVerify(c,"onDrownedSpawn","(Lnet/minecraft/world/entity/monster/Drowned;)V","a3cb7bd0b5eb5ae8751bbd543f496524eb884bafacfd2f33204873132f1fdb92");
bonsVerify(c,"onLivingAttack","(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;)V","e34d2da7895e262b481d50f0f9b698fe57cbbacbc19d5c622f3c6751b4587fab");
bonsVerify(c,"onLivingTick","(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V","5009ea46e7769081f7de0f6879b8220a3dee66f5a83e40b7173c403807f8c1f3");
bonsVerify(c,"breakSpeed","(Lnet/minecraftforge/event/entity/player/PlayerEvent$BreakSpeed;)V","b4de78593ff89a071e237e508824b45949b89ab6f5f4c30bd68096b16bd7b59d");
bonsVerify(c,"isWearingScuba","(Lnet/minecraft/world/entity/LivingEntity;)Z","a76d4fa3190b55db63db5cd5b73e5301811e59991061c418af0c9c053d144407");
bonsVerify(c,"isWearingFullScuba","(Lnet/minecraft/world/entity/LivingEntity;)Z","60ce8d9ab0762aaca81cb0edf08a6228c8bbdf4cabdd2b18a8343417ab0abeea");
bonsVerify(c,"isWearingScubaOnSlot","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)Z","c507153b1c2818ac077624244755731c8e56b3957a1453e155991f9cc70ac494");
bonsVerify(c,"checkAddAndRemoveModifier","(Lnet/minecraft/world/entity/LivingEntity;ZLnet/minecraft/world/entity/ai/attributes/Attribute;Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V","d4d6fd55588c2524e7fc0fe649ca03e6c287437c57ceaf313f176f2cde992be5");
bonsVerify(c,"removeModifier","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/Attribute;Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V","195707224d4c42c10be41e43723bfefadfdd0b780b7f0615fb72b006dfc46eca");
bonsVerify(c,"isRuinOrWreckNearby","(Lnet/minecraft/world/entity/Entity;)Z","19cf0c96c54797efb4fdf9581dbb66dfc2adf1978de8cc7e2dd4bac05ef98c43");
bonsVerify(c,"lambda$onLivingTick$5","(Lnet/minecraft/world/entity/LivingEntity;)V","6d1d6b271a0e9afc9c1214c8188116f538aa66698419b58b788356300cd79f7f");
bonsVerify(c,"lambda$onLivingTick$4","(Lnet/minecraft/world/entity/LivingEntity;)V","482cc3265c9ced2d56c7177a4b6c37b06a9f27a358e050adc418844b80c9b94f");
bonsVerify(c,"lambda$onLivingTick$3","(Lnet/minecraft/world/entity/LivingEntity;)V","13c1d364974c59c23dc037021ed071aff0ccc4e07f68d4f11344b489ee530d4a");
bonsVerify(c,"lambda$onLivingTick$2","(Lnet/minecraft/world/entity/LivingEntity;)V","16434c9a4461b8a7d79bf6d893eb0c6d554cd8c4893bfb56a670521146ae918b");
bonsVerify(c,"lambda$onLivingAttack$1","(Lnet/minecraftforge/event/entity/living/LivingAttackEvent;Lnet/minecraft/world/item/ItemStack;)V","c63ebd98289884e71c999101444950b846e94ffe00e4e388d1d6c089d11b8200");
bonsVerify(c,"lambda$onLivingAttack$0","(Lnet/minecraft/world/entity/LivingEntity;)V","8cd2510271575d8430c05368315a87b9c4784c7389a47496080c1e615a2a00b6");
bonsVerify(c,"<clinit>","()V","4d394fea2977f4226983640c347d00e35fd53387765302280b3158be4080c98d");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
methodVisitor = new MethodNode(9,"onDrownedSpawn","(Lnet/minecraft/world/entity/monster/Drowned;Lnet/minecraft/world/level/ServerLevelAccessor;)V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(50, label0);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",0,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/legacy/scuba_gear/ScubaEvents", "isRuinOrWreckNearby", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z", false);
var label1 = new Label();
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",2,methodVisitor,[label1]);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(53, label2);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",0,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/legacy/scuba_gear/ScubaEvents", "ac$spawnRandom", "(Lnet/minecraft/world/entity/monster/Drowned;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/util/RandomSource;", false);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",6,methodVisitor,[]);
methodVisitor.visitVarInsn(O.FSTORE, 2);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(55, label3);
methodVisitor.visitVarInsn(O.FLOAD, 2);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",10,methodVisitor,[]);
var label4 = new Label();
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",11,methodVisitor,[label4]);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(56, label5);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",20,methodVisitor,[]);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(57, label4);
methodVisitor.visitFrame(O.F_APPEND,1, [O.FLOAT], 0, null);
methodVisitor.visitVarInsn(O.FLOAD, 2);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",10,methodVisitor,[]);
var label6 = new Label();
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",11,methodVisitor,[label6]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(58, label7);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",29,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",20,methodVisitor,[]);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(59, label6);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.FLOAD, 2);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",35,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",10,methodVisitor,[]);
var label8 = new Label();
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",11,methodVisitor,[label8]);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(60, label9);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",39,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",42,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",20,methodVisitor,[]);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(61, label8);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.FLOAD, 2);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",48,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",10,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",11,methodVisitor,[label1]);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(62, label10);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",55,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",20,methodVisitor,[]);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(64, label1);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"onDrownedSpawn(Lnet/minecraft/world/entity/monster/Drowned;)V",60,methodVisitor,[]);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLocalVariable("chance", "F", null, label3, label1, 2);
methodVisitor.visitLocalVariable("drowned", "Lnet/minecraft/world/entity/monster/Drowned;", null, label0, label11, 0);
methodVisitor.visitMaxs(5, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"isRuinOrWreckNearby","(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(171, label0);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",1,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 3);
methodVisitor.visitVarInsn(O.ALOAD, 3);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",4,methodVisitor,[]);
var label1 = new Label();
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",5,methodVisitor,[label1]);
methodVisitor.visitVarInsn(O.ALOAD, 3);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",2,methodVisitor,[]);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(173, label2);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",10,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(175, label3);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",12,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/legacy/scuba_gear/ScubaEvents", "ac$spawnStructureManager", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ALOAD, 3);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",17,methodVisitor,[]);
var label4 = new Label();
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",18,methodVisitor,[label4]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",12,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/legacy/scuba_gear/ScubaEvents", "ac$spawnStructureManager", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ALOAD, 3);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",17,methodVisitor,[]);
var label5 = new Label();
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",5,methodVisitor,[label5]);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(176, label4);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/server/level/ServerLevel", "net/minecraft/core/BlockPos"], 0, null);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",27,methodVisitor,[]);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(178, label5);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",28,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",29,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(179, label6);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ILOAD, 4);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",34,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ILOAD, 4);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",37,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",44,methodVisitor,[]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(180, label7);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ILOAD, 4);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",41,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ILOAD, 4);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",37,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",54,methodVisitor,[]);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(182, label8);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",46,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",55,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",47,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",48,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",51,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",61,methodVisitor,[]);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitFrame(O.F_FULL, 8, ["net/minecraft/world/entity/Entity", "net/minecraft/world/level/ServerLevelAccessor", "net/minecraft/server/level/ServerLevel", "net/minecraft/core/BlockPos", O.INTEGER, "net/minecraft/core/BlockPos", "net/minecraft/core/BlockPos", "java/util/Iterator"], 0, []);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",64,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",56,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",5,methodVisitor,[label1]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",64,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",59,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",60,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(184, label10);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",12,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/legacy/scuba_gear/ScubaEvents", "ac$spawnStructureManager", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ALOAD, 8);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",17,methodVisitor,[]);
var label11 = new Label();
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",18,methodVisitor,[label11]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",12,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/legacy/scuba_gear/ScubaEvents", "ac$spawnStructureManager", "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ALOAD, 8);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",17,methodVisitor,[]);
var label12 = new Label();
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",5,methodVisitor,[label12]);
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(185, label11);
methodVisitor.visitFrame(O.F_APPEND,1, ["net/minecraft/core/BlockPos"], 0, null);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",27,methodVisitor,[]);
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(186, label12);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",78,methodVisitor,[label9]);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(189, label1);
methodVisitor.visitFrame(O.F_FULL, 4, ["net/minecraft/world/entity/Entity", "net/minecraft/world/level/ServerLevelAccessor", O.TOP, "java/lang/Object"], 0, []);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",79,methodVisitor,[]);
bonsReuse(bonsOriginal,"isRuinOrWreckNearby(Lnet/minecraft/world/entity/Entity;)Z",27,methodVisitor,[]);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLocalVariable("posAround", "Lnet/minecraft/core/BlockPos;", null, label10, label12, 8);
methodVisitor.visitLocalVariable("pos", "Lnet/minecraft/core/BlockPos;", null, label3, label1, 3);
methodVisitor.visitLocalVariable("radius", "I", null, label6, label1, 4);
methodVisitor.visitLocalVariable("min", "Lnet/minecraft/core/BlockPos;", null, label7, label1, 5);
methodVisitor.visitLocalVariable("max", "Lnet/minecraft/core/BlockPos;", null, label8, label1, 6);
methodVisitor.visitLocalVariable("world", "Lnet/minecraft/server/level/ServerLevel;", null, label2, label1, 2);
methodVisitor.visitLocalVariable("entity", "Lnet/minecraft/world/entity/Entity;", null, label0, label13, 0);
methodVisitor.visitMaxs(4, 9);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"ac$spawnStructureManager","(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/world/level/StructureManager;",null,[]);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_215010_", "()Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/WorldGenRegion");
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/WorldGenRegion");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/StructureManager", "m_220468_", "(Lnet/minecraft/server/level/WorldGenRegion;)Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_APPEND,1, ["net/minecraft/world/level/StructureManager"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(2, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"ac$spawnRandom","(Lnet/minecraft/world/entity/monster/Drowned;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/util/RandomSource;",null,[]);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/WorldGenRegion");
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/WorldGenRegion");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/WorldGenRegion", "m_213780_", "()Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/monster/Drowned", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/level/Level", "f_46441_", "Lnet/minecraft/util/RandomSource;");
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(1, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
