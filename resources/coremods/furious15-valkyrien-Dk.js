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

var BONS_KEY="valkyrien_collision_axes";var BONS_SCRIPT="furious15-valkyrien-Dk.js";var BONS_TARGET="org.valkyrienskies.core.impl.shadow.Dk";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.core.impl.shadow.Dk"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","5942991974f47333822ecb04a032adb3b9163f71c0d69d81b0c174bd3882a02c");
bonsVerify(c,"createPolygonFromAABB","(Lorg/joml/primitives/AABBdc;Lorg/joml/Matrix4dc;Ljava/lang/Long;)Lorg/valkyrienskies/core/internal/collision/VsiConvexPolygonc;","2ff6125036b5d10d47a37f995554d41ffd6fd9c726e11d620d5a9b4403101351");
bonsVerify(c,"adjustEntityMovementForPolygonCollisions","(Lorg/joml/Vector3dc;Lorg/joml/primitives/AABBdc;DLjava/util/List;)Lkotlin/Pair;","8e1c356fba288fc2d4c32cf66402e15647270ba6936aef5ac94952a3c06feb0e");
bonsVerify(c,"a","(Lorg/joml/primitives/AABBdc;Lorg/joml/Vector3dc;DLjava/util/List;)Lkotlin/Pair;","f9d58a1fb1c0b25dcad1750ddd713ae07c7ff6ed52e667060fef7d9648eccbc7");
bonsVerify(c,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DD)Lkotlin/Pair;","c4fc0743d774a00f234b9443a006a5fc0a10dd56c2beab38c52c439c179bc6ec");
bonsVerify(c,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;D)Lkotlin/Pair;","72a911a3e37dd3cfc05dedb699d2a610c160651f58f2a7805ba51c5e433aac16");
bonsVerify(c,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;","509fc2c315ca95d7c34ab6fb4db7b28b79b979da4b82d7de8b168973bc03d922");
bonsVerify(c,"a$default","(Lorg/valkyrienskies/core/impl/shadow/Dk;Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;I)Lkotlin/Pair;","f8543710cc1230c4f3f15029bc45c619dbfb031de945348481d4819cd0350a30");
bonsVerify(c,"a","(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)Lorg/joml/Vector3dc;","10f536dbb1a4a1e97b22416a977220324219c8a1c0a567627010e2f9bd52655e");
bonsVerify(c,"a","(Lorg/joml/primitives/AABBdc;DD)Lorg/joml/primitives/AABBdc;","ecab71e5894b32df702456df46bfedca9101ce174ec4462488900bfbf8b0f2c7");
bonsVerify(c,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z","cf7a670abd7f6953e0809bd27c8267e21a5907c39df2be811b8bf554ea506423");
bonsVerify(c,"a","(Lorg/joml/primitives/AABBdc;)Lorg/joml/primitives/AABBdc;","66c84c3853106d92d2c76df34b1ccc7d3e2e01a01e85ba8c26628b0fabdc95e6");
bonsVerify(c,"a","(Ljava/lang/Iterable;)Ljava/util/List;","80846f64cdb6e52f400e421f110b5b6ed6d810f4af3334cda445ffdce02ab6fe");
bonsVerify(c,"<clinit>","()V","bcbe9f65e22454c2861d4b9be17a2ff46f09f9c67f9dabd8957b9d22ac8746c9");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",241);
removeMethod(c,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",182);
methodVisitor = new MethodNode(18,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;","(Lorg/joml/primitives/AABBdc;Ljava/util/List<+Lorg/valkyrienskies/core/internal/collision/VsiConvexPolygonc;>;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair<Lorg/joml/Vector3dc;Ljava/lang/Long;>;",[]);
methodVisitor.visitCode();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",11,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes", "forCandidates", "(I)Lagentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes;", false);
methodVisitor.visitVarInsn(O.ASTORE, 20);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",2,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",10,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",17,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep2", "sortByDistance", "(Ljava/lang/Iterable;Lorg/joml/primitives/AABBdc;)Ljava/util/List;", false);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",24,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",25,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",2,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",32,methodVisitor,[]);
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_FULL, 20, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", "org/joml/Vector3dc", O.DOUBLE, "org/joml/Vector3dc", "java/lang/Long", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "org/joml/Vector3d", "java/util/Iterator", O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",35,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",36,methodVisitor,[]);
var label1 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",37,methodVisitor,[label1]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",35,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",39,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",41,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",41,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",44,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",46,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",47,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",48,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",51,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",47,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",55,methodVisitor,[]);
var label2 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",37,methodVisitor,[label2]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",58,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 20);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/core/impl/shadow/Dk", "e", "[Lorg/joml/Vector3dc;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes", "axes", "(Ljava/lang/Iterable;Lagentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes;[Lorg/joml/Vector3dc;)Ljava/util/List;", false);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",60,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",61,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",46,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",66,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",70,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",71,methodVisitor,[]);
var label3 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",37,methodVisitor,[label3]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",73,methodVisitor,[]);
var label4 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",74,methodVisitor,[label4]);
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_FULL, 20, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", "org/joml/Vector3dc", O.DOUBLE, "org/joml/Vector3dc", "java/lang/Long", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "org/joml/Vector3d", "java/util/Iterator", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "java/util/List", "org/valkyrienskies/core/impl/shadow/Df", O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",75,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",76,methodVisitor,[]);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.DOUBLE]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",77,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",78,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",79,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",80,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",81,methodVisitor,[label2]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",84,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",87,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",88,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",70,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",79,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",84,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",98,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",87,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",88,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",109,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",2,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",75,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",44,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",115,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",116,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",117,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",128,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",129,methodVisitor,[]);
var label5 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",130,methodVisitor,[label5]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",131,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",135,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",136,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",138,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",37,methodVisitor,[label5]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",61,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",141,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",66,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",147,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",148,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",147,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",148,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",135,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",152,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",153,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",117,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",73,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",80,methodVisitor,[]);
var label6 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",81,methodVisitor,[label6]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",164,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",165,methodVisitor,[label6]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",168,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",80,methodVisitor,[]);
var label7 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",170,methodVisitor,[label7]);
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_FULL, 19, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", "org/joml/Vector3dc", O.DOUBLE, "org/joml/Vector3dc", "java/lang/Long", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "org/joml/Vector3dc", "java/util/Iterator", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "java/util/List", "org/joml/Vector3dc", "org/joml/Vector3dc", "org/valkyrienskies/core/impl/shadow/Dq", O.TOP, O.TOP, O.DOUBLE, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",73,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",164,methodVisitor,[]);
var label8 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",165,methodVisitor,[label8]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",80,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",81,methodVisitor,[label8]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",156,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",168,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",164,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",165,methodVisitor,[label8]);
methodVisitor.visitLabel(label7);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",187,methodVisitor,[]);
var label9 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",74,methodVisitor,[label9]);
methodVisitor.visitLabel(label8);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",189,methodVisitor,[]);
methodVisitor.visitLabel(label9);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",191,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",37,methodVisitor,[label5]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",75,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",87,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",198,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",74,methodVisitor,[label0]);
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_FULL, 19, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", "org/joml/Vector3dc", O.DOUBLE, "org/joml/Vector3dc", "java/lang/Long", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "org/joml/Vector3dc", "java/util/Iterator", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "java/util/List", "org/joml/Vector3dc", O.TOP, "org/valkyrienskies/core/impl/shadow/Dq", O.TOP, O.TOP, O.DOUBLE, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",61,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",141,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",66,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",147,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",148,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",147,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",148,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",135,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",129,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",153,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",117,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",75,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",87,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",198,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",117,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",119,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",14,methodVisitor,[]);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_FULL, 20, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", "org/joml/Vector3dc", O.DOUBLE, "org/joml/Vector3dc", "java/lang/Long", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "org/joml/Vector3d", "java/util/Iterator", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",74,methodVisitor,[label0]);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_FULL, 20, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", "org/joml/Vector3dc", O.DOUBLE, "org/joml/Vector3dc", "java/lang/Long", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", "org/joml/Vector3d", "java/util/Iterator", O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",232,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",49,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",24,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",237,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",239,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;Lorg/joml/Vector3dc;DLorg/joml/Vector3dc;)Lkotlin/Pair;",240,methodVisitor,[]);
methodVisitor.visitMaxs(10, 21);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(18,"a","(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z","(Lorg/joml/primitives/AABBdc;Ljava/util/List<+Lorg/valkyrienskies/core/internal/collision/VsiConvexPolygonc;>;D)Z",[]);
methodVisitor.visitCode();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",20,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes", "forCandidates", "(I)Lagentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes;", false);
methodVisitor.visitVarInsn(O.ASTORE, 13);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",20,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",23,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsSweep2", "sortByDistance", "(Ljava/lang/Iterable;Lorg/joml/primitives/AABBdc;)Ljava/util/List;", false);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",30,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",31,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",23,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",20,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",38,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",39,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",12,methodVisitor,[]);
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_FULL, 12, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", O.DOUBLE, O.DOUBLE, "java/util/Iterator", "org/valkyrienskies/core/impl/shadow/Dq", "org/joml/Vector3dc", O.TOP, "java/lang/Object", O.TOP, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",42,methodVisitor,[]);
var label1 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",43,methodVisitor,[label1]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",45,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",47,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",48,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",38,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",51,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",54,methodVisitor,[]);
var label2 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",43,methodVisitor,[label2]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",57,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 13);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/core/impl/shadow/Dk", "e", "[Lorg/joml/Vector3dc;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes", "axes", "(Ljava/lang/Iterable;Lagentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes;[Lorg/joml/Vector3dc;)Ljava/util/List;", false);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",59,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",60,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",50,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",48,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",63,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",64,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",66,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",72,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",23,methodVisitor,[]);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_FULL, 12, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", O.DOUBLE, O.DOUBLE, "java/util/Iterator", "org/valkyrienskies/core/impl/shadow/Dq", "org/joml/Vector3dc", "java/lang/Object", "org/valkyrienskies/core/internal/collision/VsiConvexPolygonc", O.TOP, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",74,methodVisitor,[label0]);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_FULL, 12, ["org/valkyrienskies/core/impl/shadow/Dk", "org/joml/primitives/AABBdc", "java/util/List", O.DOUBLE, O.DOUBLE, "java/util/Iterator", "org/valkyrienskies/core/impl/shadow/Dq", "org/joml/Vector3dc", O.TOP, "java/lang/Object", O.TOP, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",75,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",76,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",77,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",79,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",80,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",82,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",85,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",88,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",92,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",93,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",97,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",92,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",93,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",101,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",92,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",107,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",92,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",110,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",63,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",64,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",113,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",115,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",116,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",117,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",13,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",47,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",20,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",38,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",39,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",59,methodVisitor,[]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_FULL, 12, ["org/valkyrienskies/core/impl/shadow/Dk", "java/lang/Object", "java/util/List", O.DOUBLE, O.DOUBLE, "org/joml/primitives/AABBdc", "org/valkyrienskies/core/impl/shadow/Dq", "org/joml/Vector3dc", "org/valkyrienskies/core/impl/shadow/Dq", "java/lang/Iterable", "java/util/Iterator", "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes"], 0, []);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",66,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",42,methodVisitor,[]);
var label4 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",43,methodVisitor,[label4]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",66,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",45,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",138,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",48,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",138,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",141,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",51,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",53,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",54,methodVisitor,[]);
var label5 = new Label();
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",43,methodVisitor,[label5]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",57,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 13);
methodVisitor.visitFieldInsn(O.GETSTATIC, "org/valkyrienskies/core/impl/shadow/Dk", "e", "[Lorg/joml/Vector3dc;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes", "axes", "(Ljava/lang/Iterable;Lagentcraft/consolidated/bons_valkyrien_fixes/org/valkyrienskies/mod/common/util/AcVsAxes;[Lorg/joml/Vector3dc;)Ljava/util/List;", false);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",30,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",60,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",152,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",141,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",48,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",31,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",64,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",20,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",67,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",77,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",168,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",170,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",22,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",138,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",173,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",174,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",175,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",176,methodVisitor,[label5]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",177,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",178,methodVisitor,[]);
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",74,methodVisitor,[label3]);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",180,methodVisitor,[]);
bonsReuse(bonsOriginal,"a(Lorg/joml/primitives/AABBdc;Ljava/util/List;D)Z",178,methodVisitor,[]);
methodVisitor.visitMaxs(16, 14);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
