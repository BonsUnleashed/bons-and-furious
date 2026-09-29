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

var BONS_KEY="valkyrien_chunk_set_version";var BONS_SCRIPT="furious15-valkyrien-ShipActiveChunksSet.js";var BONS_TARGET="org.valkyrienskies.core.impl.chunk_tracking.ShipActiveChunksSet";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.core.impl.chunk_tracking.ShipActiveChunksSet"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","(Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)V","782ced15ba1bd2343ecd8b7704f2ab78321faa1406c578115203fe8ea77371c6");
bonsVerify(c,"add","(II)Z","8ced51b1a3f6e9206eb5eb84171a1ffd582a448647c9da3f40d187691ebb5554");
bonsVerify(c,"remove","(II)Z","b12dc755b0fc655f14e68ead4904abe456bd006d43cd0552c5f6ea95c63e76d7");
bonsVerify(c,"contains","(II)Z","59d250b5229b12bdc0158009d6414eb2783ecef2d70cc34d537af3f0028ff5c9");
bonsVerify(c,"iterateChunkPos","(Lkotlin/jvm/functions/Function2;)V","0b5df20a2bf53257b1b513cedb87e7b03fead08477d7bebf7f4e55067f4bf2d9");
bonsVerify(c,"forEach","(Lorg/valkyrienskies/core/api/util/functions/IntBinaryConsumer;)V","e86cdc8c1431e0d47fbb3affa4a9731386f97cfd67fafbd2cb903202456883bb");
bonsVerify(c,"getSize","()I","d16d352af48a7c698d669d9de0fc72597760ec99963948494d775e54da73e718");
bonsVerify(c,"equals","(Ljava/lang/Object;)Z","b28491a27765965ab9a1fb285d976d175d1b817f70b89640ecdd9abd318545c3");
bonsVerify(c,"hashCode","()I","c7fbe10a10bb90972daf4a850888f5320bcf26a3f371378a5f4cf5e95c091e8e");
bonsVerify(c,"clone","()Lorg/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet;","e16c9fe4209764badfeabef0ebc5bc9b37bda62f6e57cbce175ff56cd9f83226");
bonsVerify(c,"component1","()Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;","fc6f6b095a26716ae890c888d944f7653ae8beeff6fbc852b4c1a49220951958");
bonsVerify(c,"copy","(Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;)Lorg/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet;","9e9954474a1422a1b54704d00bad4b03bd0fed4c389547695662baedbec21458");
bonsVerify(c,"copy$default","(Lorg/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet;Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;ILjava/lang/Object;)Lorg/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet;","e9178bb1294ef3a046d3deae435af3264d1c85da947af52ee62704656581c527");
bonsVerify(c,"toString","()Ljava/lang/String;","c742062e409e6a9c5d70065603d4c6fbe747f7393a9239a1c23e6ec95a5dc8cf");
bonsVerify(c,"<clinit>","()V","7baa9396a23f671a14a6e6e989ce6cb2662fb26c1eb0491135f34136d94c7c3b");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"add","(II)Z",8);
removeMethod(c,"remove","(II)Z",8);
methodVisitor = new MethodNode(17,"add","(II)Z",null,[]);
methodVisitor.visitParameter("chunkX", 0);
methodVisitor.visitParameter("chunkZ", 0);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
bonsReuse(bonsOriginal,"add(II)Z",0,methodVisitor,[]);
var label1 = new Label();
methodVisitor.visitLabel(label1);
bonsReuse(bonsOriginal,"add(II)Z",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"add(II)Z",2,methodVisitor,[]);
bonsReuse(bonsOriginal,"add(II)Z",3,methodVisitor,[]);
var label2 = new Label();
methodVisitor.visitLabel(label2);
bonsReuse(bonsOriginal,"add(II)Z",4,methodVisitor,[]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
bonsReuse(bonsOriginal,"add(II)Z",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"add(II)Z",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"add(II)Z",0,methodVisitor,[]);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet", "acVsMods", "J");
methodVisitor.visitInsn(O.LCONST_1);
methodVisitor.visitInsn(O.LADD);
methodVisitor.visitFieldInsn(O.PUTFIELD, "org/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet", "acVsMods", "J");
bonsReuse(bonsOriginal,"add(II)Z",7,methodVisitor,[]);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet;", null, label0, label1, 0);
methodVisitor.visitLocalVariable("chunkX", "I", null, label0, label2, 1);
methodVisitor.visitLocalVariable("chunkZ", "I", null, label0, label3, 2);
methodVisitor.visitMaxs(6, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(17,"remove","(II)Z",null,[]);
methodVisitor.visitParameter("chunkX", 0);
methodVisitor.visitParameter("chunkZ", 0);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
bonsReuse(bonsOriginal,"remove(II)Z",0,methodVisitor,[]);
var label1 = new Label();
methodVisitor.visitLabel(label1);
bonsReuse(bonsOriginal,"remove(II)Z",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"remove(II)Z",2,methodVisitor,[]);
bonsReuse(bonsOriginal,"remove(II)Z",3,methodVisitor,[]);
var label2 = new Label();
methodVisitor.visitLabel(label2);
bonsReuse(bonsOriginal,"remove(II)Z",4,methodVisitor,[]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
bonsReuse(bonsOriginal,"remove(II)Z",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"remove(II)Z",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"remove(II)Z",0,methodVisitor,[]);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet", "acVsMods", "J");
methodVisitor.visitInsn(O.LCONST_1);
methodVisitor.visitInsn(O.LADD);
methodVisitor.visitFieldInsn(O.PUTFIELD, "org/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet", "acVsMods", "J");
bonsReuse(bonsOriginal,"remove(II)Z",7,methodVisitor,[]);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/core/impl/chunk_tracking/ShipActiveChunksSet;", null, label0, label1, 0);
methodVisitor.visitLocalVariable("chunkX", "I", null, label0, label2, 1);
methodVisitor.visitLocalVariable("chunkZ", "I", null, label0, label3, 2);
methodVisitor.visitMaxs(6, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
{
fieldVisitor = c.visitField(O.ACC_PUBLIC | O.ACC_TRANSIENT, "acVsMods", "J", null, null);
fieldVisitor.visitEnd();
}
{
fieldVisitor = c.visitField(O.ACC_PUBLIC | O.ACC_TRANSIENT, "acVsExtents", "Ljava/lang/Object;", null, null);
fieldVisitor.visitEnd();
}
{
fieldVisitor = c.visitField(O.ACC_PUBLIC | O.ACC_TRANSIENT, "acVsWorld", "Ljava/lang/Object;", null, null);
fieldVisitor.visitEnd();
}
return c;
});}}};}
