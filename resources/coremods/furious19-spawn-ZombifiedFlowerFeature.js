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

var BONS_KEY="spawn_zombified_flower_floor";var BONS_SCRIPT="furious19-spawn-ZombifiedFlowerFeature.js";var BONS_TARGET="com.ninni.spawn.server.level.feature.ZombifiedFlowerFeature";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.ninni.spawn.server.level.feature.ZombifiedFlowerFeature"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","(Lcom/mojang/serialization/Codec;)V","1e87e6745aef41753fd3c3c1d171aeed10a3e70028a13848ffaf45a03e0128f4");
bonsVerify(c,"m_142674_","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z","aa670a63b14c33251a27344a80e731c70823f061f127bdd686a1f65f70968e04");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"m_142674_","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",147);
methodVisitor = new MethodNode(1,"m_142674_","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext<Lnet/minecraft/world/level/levelgen/feature/configurations/NoneFeatureConfiguration;>;)Z",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(23, label0);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",2,methodVisitor,[]);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(24, label1);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",5,methodVisitor,[]);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(25, label2);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",8,methodVisitor,[]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(26, label3);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",10,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",11,methodVisitor,[]);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(28, label4);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",17,methodVisitor,[]);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(29, label5);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",23,methodVisitor,[]);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(31, label6);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",25,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",28,methodVisitor,[]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitFrame(O.F_FULL, 9, ["com/ninni/spawn/server/level/feature/ZombifiedFlowerFeature", "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "net/minecraft/core/BlockPos", "net/minecraft/world/level/WorldGenLevel", "net/minecraft/util/RandomSource", "net/minecraft/core/BlockPos$MutableBlockPos", O.INTEGER, O.INTEGER, O.INTEGER], 0, []);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",29,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",25,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",16,methodVisitor,[]);
var label8 = new Label();
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",34,methodVisitor,[label8]);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(32, label9);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",36,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",39,methodVisitor,[]);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",36,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",16,methodVisitor,[]);
var label11 = new Label();
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",34,methodVisitor,[label11]);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(33, label12);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",29,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",25,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",50,methodVisitor,[]);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(34, label13);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",36,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",55,methodVisitor,[]);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(35, label14);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",56,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",56,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",58,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",59,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",59,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",58,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",58,methodVisitor,[]);
var label15 = new Label();
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",34,methodVisitor,[label15]);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(36, label16);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",29,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",70,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",74,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",75,methodVisitor,[]);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(38, label17);
methodVisitor.visitFrame(O.F_APPEND,2, [O.INTEGER, O.INTEGER], 0, null);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",76,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",78,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",79,methodVisitor,[]);
var label18 = new Label();
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",80,methodVisitor,[label18]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos$MutableBlockPos", "m_123342_", "()I", false);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",76,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/world/level/LevelHeightAccessor", "m_141937_", "()I", true);
methodVisitor.visitJumpInsn(O.IF_ICMPLE, label18);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",82,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",83,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",84,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",85,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",84,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",87,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",88,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",75,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",90,methodVisitor,[label17]);
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(40, label18);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",76,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",78,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",94,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",80,methodVisitor,[label15]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",76,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",98,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",78,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",100,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",101,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",80,methodVisitor,[label15]);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(42, label19);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",103,methodVisitor,[]);
var label20 = new Label();
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",104,methodVisitor,[label20]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",106,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",107,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",108,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",109,methodVisitor,[label20]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",76,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",98,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",78,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",79,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",80,methodVisitor,[label20]);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(43, label21);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",76,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",117,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",118,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",120,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",98,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",124,methodVisitor,[]);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(44, label22);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",103,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",23,methodVisitor,[]);
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(47, label20);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",106,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",107,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",108,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",109,methodVisitor,[label15]);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(48, label23);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",76,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",136,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",120,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",139,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",75,methodVisitor,[]);
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(32, label15);
methodVisitor.visitFrame(O.F_CHOP,2, null, 0, null);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",141,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",90,methodVisitor,[label10]);
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(31, label11);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",143,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",90,methodVisitor,[label7]);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(56, label8);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"m_142674_(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",146,methodVisitor,[]);
var label24 = new Label();
methodVisitor.visitLabel(label24);
methodVisitor.visitLocalVariable("j1", "I", null, label13, label15, 10);
methodVisitor.visitLocalVariable("k1", "I", null, label14, label15, 11);
methodVisitor.visitLocalVariable("i1", "I", null, label10, label11, 9);
methodVisitor.visitLocalVariable("l", "I", null, label7, label8, 8);
methodVisitor.visitLocalVariable("this", "Lcom/ninni/spawn/server/level/feature/ZombifiedFlowerFeature;", null, label0, label24, 0);
methodVisitor.visitLocalVariable("context", "Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;", "Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext<Lnet/minecraft/world/level/levelgen/feature/configurations/NoneFeatureConfiguration;>;", label0, label24, 1);
methodVisitor.visitLocalVariable("blockpos", "Lnet/minecraft/core/BlockPos;", null, label1, label24, 2);
methodVisitor.visitLocalVariable("worldgenlevel", "Lnet/minecraft/world/level/WorldGenLevel;", null, label2, label24, 3);
methodVisitor.visitLocalVariable("randomsource", "Lnet/minecraft/util/RandomSource;", null, label3, label24, 4);
methodVisitor.visitLocalVariable("pos", "Lnet/minecraft/core/BlockPos$MutableBlockPos;", null, label4, label24, 5);
methodVisitor.visitLocalVariable("size", "I", null, label5, label24, 6);
methodVisitor.visitLocalVariable("flowerAmount", "I", null, label6, label24, 7);
methodVisitor.visitMaxs(6, 12);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
