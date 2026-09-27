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

var BONS_KEY="structurify_height_cache";var BONS_SCRIPT="bons_pure_optimizations-ChunkGeneratorHeightCache.js";var BONS_TARGET="com.faboslav.structurify.common.world.level.chunk.ChunkGeneratorHeightCache";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.faboslav.structurify.common.world.level.chunk.ChunkGeneratorHeightCache"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","5942991974f47333822ecb04a032adb3b9163f71c0d69d81b0c174bd3882a02c");
bonsVerify(c,"getFirstFreeHeight","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;","039c3b0c8d3b8ded6f4a6facb9180cbce0c12b89b9117b51b91a0480cea9dd88");
bonsVerify(c,"putFirstFreeHeight","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V","4206fd61815d1de4a18c0f1caaa3183c10d8867f2d2d31150b86f1bb24074b7b");
bonsVerify(c,"getFirstOccupiedHeight","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;","f132a7b0436026d2bf3e1d322139c8047d081a3cec9d6bdca1e2bf0b802a47b2");
bonsVerify(c,"putFirstOccupiedHeight","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V","31829510dc9c9e70ffa847fe1b51b1505a154e1f15019da689026dd91a616246");
bonsVerify(c,"get","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;","d9b858adc0c278644f2f48805eebd7298cfa067e0f66ebb47ed3590432d0e499");
bonsVerify(c,"put","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V","90bc863414c42ef4bf03d49f5f44faf9fc5fd1f39b7af2fecb2d0e8106e840ef");
bonsVerify(c,"lambda$static$0","()Ljava/util/Map;","485073c51ec6830f95ad6a610f6a5e025690dd13cea646e23c0a48e9c740c7eb");
bonsVerify(c,"<clinit>","()V","1a4bcce530a04f2f76f41dbc778652ee0a59484c4f2f2fd5877493d8b53461a7");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"get","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",15);
removeMethod(c,"put","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",17);
removeMethod(c,"<clinit>","()V",4);
methodVisitor = new MethodNode(10,"get","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
var label1 = new Label();
var label2 = new Label();
methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
var label3 = new Label();
methodVisitor.visitTryCatchBlock(label2, label3, label2, null);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(51, label0);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",2,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(52, label4);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache", "drainCollectedAccessors", "(Ljava/util/Map;)V", false);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(53, label5);
methodVisitor.visitVarInsn(O.ALOAD, 6);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",10,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",13,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 7);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(56, label1);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",9,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/ref/Reference", "reachabilityFence", "(Ljava/lang/Object;)V", false);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(53, label6);
methodVisitor.visitVarInsn(O.ALOAD, 7);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",14,methodVisitor,[]);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(56, label2);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["java/lang/Throwable"]);
methodVisitor.visitVarInsn(O.ASTORE, 8);
methodVisitor.visitLabel(label3);
bonsReuse(bonsOriginal,"get(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Ljava/lang/Integer;",9,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/ref/Reference", "reachabilityFence", "(Ljava/lang/Object;)V", false);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(57, label7);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitInsn(O.ATHROW);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLocalVariable("cache", "Ljava/util/Map;", "Ljava/util/Map<Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCacheKey;Ljava/lang/Integer;>;", label4, label2, 6);
methodVisitor.visitLocalVariable("generator", "Lnet/minecraft/world/level/chunk/ChunkGenerator;", null, label0, label8, 0);
methodVisitor.visitLocalVariable("x", "I", null, label0, label8, 1);
methodVisitor.visitLocalVariable("z", "I", null, label0, label8, 2);
methodVisitor.visitLocalVariable("type", "Lnet/minecraft/world/level/levelgen/Heightmap$Types;", null, label0, label8, 3);
methodVisitor.visitLocalVariable("accessor", "Lnet/minecraft/world/level/LevelHeightAccessor;", null, label0, label8, 4);
methodVisitor.visitLocalVariable("state", "Lnet/minecraft/world/level/levelgen/RandomState;", null, label0, label8, 5);
methodVisitor.visitMaxs(9, 9);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"put","(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
var label1 = new Label();
var label2 = new Label();
methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
var label3 = new Label();
methodVisitor.visitTryCatchBlock(label2, label3, label2, null);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(63, label0);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",2,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(64, label4);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache", "drainCollectedAccessors", "(Ljava/util/Map;)V", false);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(65, label5);
methodVisitor.visitVarInsn(O.ALOAD, 7);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",5,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",10,methodVisitor,[]);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache", "HEIGHT_ACCESSOR_QUEUE", "Ljava/lang/ThreadLocal;");
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(66, label6);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",1,methodVisitor,[]);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/ref/ReferenceQueue");
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "com/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCacheKey", "<init>", "(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Ljava/lang/ref/ReferenceQueue;)V", false);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",13,methodVisitor,[]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(65, label7);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",15,methodVisitor,[]);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(68, label1);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",9,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/ref/Reference", "reachabilityFence", "(Ljava/lang/Object;)V", false);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(69, label8);
var label9 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label9);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(68, label2);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["java/lang/Throwable"]);
methodVisitor.visitVarInsn(O.ASTORE, 8);
methodVisitor.visitLabel(label3);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",9,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/ref/Reference", "reachabilityFence", "(Ljava/lang/Object;)V", false);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(69, label10);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitInsn(O.ATHROW);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(70, label9);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"put(Lnet/minecraft/world/level/chunk/ChunkGenerator;IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;I)V",16,methodVisitor,[]);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLocalVariable("cache", "Ljava/util/Map;", "Ljava/util/Map<Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCacheKey;Ljava/lang/Integer;>;", label4, label1, 7);
methodVisitor.visitLocalVariable("generator", "Lnet/minecraft/world/level/chunk/ChunkGenerator;", null, label0, label11, 0);
methodVisitor.visitLocalVariable("x", "I", null, label0, label11, 1);
methodVisitor.visitLocalVariable("z", "I", null, label0, label11, 2);
methodVisitor.visitLocalVariable("type", "Lnet/minecraft/world/level/levelgen/Heightmap$Types;", null, label0, label11, 3);
methodVisitor.visitLocalVariable("accessor", "Lnet/minecraft/world/level/LevelHeightAccessor;", null, label0, label11, 4);
methodVisitor.visitLocalVariable("state", "Lnet/minecraft/world/level/levelgen/RandomState;", null, label0, label11, 5);
methodVisitor.visitLocalVariable("height", "I", null, label0, label11, 6);
methodVisitor.visitMaxs(10, 9);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"drainCollectedAccessors","(Ljava/util/Map;)V","(Ljava/util/Map<Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCacheKey;Ljava/lang/Integer;>;)V",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(74, label0);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache", "HEIGHT_ACCESSOR_QUEUE", "Ljava/lang/ThreadLocal;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/ref/ReferenceQueue");
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(75, label1);
methodVisitor.visitFrame(O.F_APPEND,2, [O.TOP, "java/lang/ref/ReferenceQueue"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/ref/ReferenceQueue", "poll", "()Ljava/lang/ref/Reference;", false);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ASTORE, 1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(76, label4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitJumpInsn(O.GOTO, label1);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(78, label3);
methodVisitor.visitFrame(O.F_FULL, 3, ["java/util/Map", "java/lang/ref/Reference", "java/lang/ref/ReferenceQueue"], 0, []);
methodVisitor.visitInsn(O.RETURN);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLocalVariable("cache", "Ljava/util/Map;", "Ljava/util/Map<Lcom/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCacheKey;Ljava/lang/Integer;>;", label0, label5, 0);
methodVisitor.visitLocalVariable("key", "Ljava/lang/ref/Reference;", "Ljava/lang/ref/Reference<+Lnet/minecraft/world/level/LevelHeightAccessor;>;", label2, label5, 1);
methodVisitor.visitLocalVariable("queue", "Ljava/lang/ref/ReferenceQueue;", "Ljava/lang/ref/ReferenceQueue<Lnet/minecraft/world/level/LevelHeightAccessor;>;", label1, label5, 2);
methodVisitor.visitMaxs(2, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(8,"<clinit>","()V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(15, label0);
methodVisitor.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("()Ljava/lang/Object;"), new Handle(O.H_NEWINVOKESPECIAL, "java/lang/ref/ReferenceQueue", "<init>", "()V", false), Type.getType("()Ljava/lang/ref/ReferenceQueue;")]);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(16, label1);
bonsReuse(bonsOriginal,"<clinit>()V",1,methodVisitor,[]);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "com/faboslav/structurify/common/world/level/chunk/ChunkGeneratorHeightCache", "HEIGHT_ACCESSOR_QUEUE", "Ljava/lang/ThreadLocal;");
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(17, label2);
bonsReuse(bonsOriginal,"<clinit>()V",0,methodVisitor,[]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(18, label3);
bonsReuse(bonsOriginal,"<clinit>()V",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"<clinit>()V",2,methodVisitor,[]);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(17, label4);
bonsReuse(bonsOriginal,"<clinit>()V",3,methodVisitor,[]);
methodVisitor.visitMaxs(1, 0);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
{
fieldVisitor = c.visitField(O.ACC_PRIVATE | O.ACC_FINAL | O.ACC_STATIC, "HEIGHT_ACCESSOR_QUEUE", "Ljava/lang/ThreadLocal;", "Ljava/lang/ThreadLocal<Ljava/lang/ref/ReferenceQueue<Lnet/minecraft/world/level/LevelHeightAccessor;>;>;", null);
fieldVisitor.visitEnd();
}
return c;
});}}};}
