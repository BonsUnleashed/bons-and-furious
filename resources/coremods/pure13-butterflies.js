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

var BONS_KEY="butterflies_landing_rule_lookup";var BONS_SCRIPT="pure13-butterflies.js";var BONS_TARGET="com.bokmcdok.butterflies.butterfly_data.LandingRules";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.bokmcdok.butterflies.butterfly_data.LandingRules"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","(Ljava/util/Set;)V","bc6dbc97ffe1bd94962b877380abe9ea1173e1a963416dafd258259b3295ed6e");
bonsVerify(c,"isValidLandingBlock","(Lnet/minecraft/world/level/block/state/BlockState;)Z","e71463ac97cc9a6550239420a86368e554d6cd3958149ec39e9e187547f1fe77");
bonsVerify(c,"requireResourceLocation","(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;","1835e2305bb469f889f6c4debcc271b56646e1de198c5b5f99618ffd99d6a75a");
bonsVerify(c,"requireBlock","(Ljava/lang/String;)Lnet/minecraft/world/level/block/Block;","c576d738983fffa0b117bae7c07240d163bb23d8b951e32778d6a55e16002593");
bonsVerify(c,"requireBlockTag","(Ljava/lang/String;)Lnet/minecraft/tags/TagKey;","c5ea9bfb4ec7710206c58f813ea09860875b2e6f59e648e207920d38a5055b53");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"isValidLandingBlock","(Lnet/minecraft/world/level/block/state/BlockState;)Z",40);
methodVisitor = new MethodNode(1,"isValidLandingBlock","(Lnet/minecraft/world/level/block/state/BlockState;)Z",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(27, label0);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",11,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/block/state/BlockState", "m_60734_", "()Lnet/minecraft/world/level/block/Block;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Set", "contains", "(Ljava/lang/Object;)Z", true);
var label1 = new Label();
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",6,methodVisitor,[label1]);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(28, label2);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",16,methodVisitor,[]);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(30, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",0,methodVisitor,[]);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/bokmcdok/butterflies/butterfly_data/LandingRules", "bons$tags", "[Lnet/minecraft/tags/TagKey;");
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",3,methodVisitor,[]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(31, label3);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",4,methodVisitor,[]);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(32, label5);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",38,methodVisitor,[]);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "net/minecraft/tags/TagKey");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Set", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "[Lnet/minecraft/tags/TagKey;");
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",3,methodVisitor,[]);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(33, label6);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",4,methodVisitor,[]);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/bokmcdok/butterflies/butterfly_data/LandingRules", "bons$tags", "[Lnet/minecraft/tags/TagKey;");
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(35, label4);
methodVisitor.visitFrame(O.F_APPEND,1, ["[Lnet/minecraft/tags/TagKey;"], 0, null);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",10,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",12,methodVisitor,[]);
methodVisitor.visitInsn(O.ARRAYLENGTH);
methodVisitor.visitVarInsn(O.ISTORE, 4);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",38,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ISTORE, 5);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitFrame(O.F_APPEND,3, ["[Lnet/minecraft/tags/TagKey;", O.INTEGER, O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitVarInsn(O.ILOAD, 4);
var label8 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label8);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",12,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitInsn(O.AALOAD);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(36, label9);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",11,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 6);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",13,methodVisitor,[]);
var label10 = new Label();
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",6,methodVisitor,[label10]);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(37, label11);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",16,methodVisitor,[]);
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(35, label10);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitIincInsn(5, 1);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",17,methodVisitor,[label7]);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(40, label8);
methodVisitor.visitFrame(O.F_CHOP,3, null, 0, null);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",38,methodVisitor,[]);
bonsReuse(bonsOriginal,"isValidLandingBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",16,methodVisitor,[]);
methodVisitor.visitMaxs(2, 7);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
{
fieldVisitor = c.visitField(O.ACC_PRIVATE | O.ACC_VOLATILE, "bons$tags", "[Lnet/minecraft/tags/TagKey;", "[Lnet/minecraft/tags/TagKey<Lnet/minecraft/world/level/block/Block;>;", null);
fieldVisitor.visitEnd();
}
return c;
});}}};}
