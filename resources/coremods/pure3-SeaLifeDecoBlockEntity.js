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

var BONS_KEY="spawn_sealife_tick";var BONS_SCRIPT="pure3-SeaLifeDecoBlockEntity.js";var BONS_TARGET="com.ninni.spawn.server.block.entity.SeaLifeDecoBlockEntity";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.ninni.spawn.server.block.entity.SeaLifeDecoBlockEntity"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V","02d73d5b06a8ffde0778c3a447f33092812bcc9a3c7cde648c36b8b8d5f19cd8");
bonsVerify(c,"tick","(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V","7050945d0aa9e57e4ec5a0fe6a1b016c75bd7d6b3e4e7e3f43f73dd164461543");
bonsVerify(c,"rotateTowardsMovement","()Z","de853d9a7f8c4cb381881711b6b54104b948031f05c9f9a710edd0e7489fda1b");
bonsVerify(c,"getSpeed","()F","7e29fdd25240d0f11a9ae877e1e354ca340faaecdfd288319f686cf565058c12");
bonsVerify(c,"getRotationSpeed","()F","f995e775280416ef631a7233cbb7d51ee121c5561e61b8eda538e75dc4469911");
bonsVerify(c,"getInterval","(Ljava/util/Random;)I","deb700840f73a912ae486880c12107c7ce9176ad6230d62a21a468ff8206a980");
bonsVerify(c,"getYaw","()F","ba3d01e47323504c81a10fd1e11d622a55e14eef78e129743bb16c2d9891a151");
bonsVerify(c,"setTargetYaw","(F)V","2de4f90f91f9810e339fa63fc28d0e5924197c2a591362022ca41938fad70bbd");
bonsVerify(c,"setYaw","(F)V","cdda5faa329be9e2e9be9ca661a815181a26d11206f0564756ccbbf9d55f087f");
bonsVerify(c,"setTargetMovePos","(Lorg/joml/Vector2f;)V","0ed8c77930c66ab9d2ee7eb33c5086c041388c147ef4a64067513b7e7f7dc612");
bonsVerify(c,"getMovePos","()Lorg/joml/Vector2f;","985a65b0e8bec56e065723c174917e86115502d1cfde24878f052fca66fb5e16");
bonsVerify(c,"setMovePos","(Lorg/joml/Vector2f;)V","d89a032902abadc7e02cde9175884fe6e0604fca35f15490547469d5eac235d7");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"tick","(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",219);
methodVisitor = new MethodNode(9,"tick","(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(24, label0);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",1,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",2,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",3,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",5,methodVisitor,[]);
var label1 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label1]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",9,methodVisitor,[label1]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",12,methodVisitor,[]);
var label2 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",13,methodVisitor,[label2]);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",14,methodVisitor,[]);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(26, label2);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",20,methodVisitor,[]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(27, label3);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",24,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",25,methodVisitor,[]);
methodVisitor.visitVarInsn(O.LSTORE, 10);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(29, label4);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",29,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",30,methodVisitor,[]);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(30, label5);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",33,methodVisitor,[]);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(31, label6);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
methodVisitor.visitVarInsn(O.LLOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", "ac$interval", "(J)I", false);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",37,methodVisitor,[]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(33, label7);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",39,methodVisitor,[]);
var label8 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label8]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",42,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",43,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",44,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",45,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label8]);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(34, label9);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",47,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",37,methodVisitor,[]);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(35, label10);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",29,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",51,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",30,methodVisitor,[]);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(36, label11);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",51,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",52,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",33,methodVisitor,[]);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(39, label8);
methodVisitor.visitFrame(O.F_FULL, 11, ["com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", O.TOP, O.FLOAT, O.FLOAT, O.INTEGER, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.LONG], 0, []);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",18,methodVisitor,[]);
var label12 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",62,methodVisitor,[label12]);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(40, label13);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",70,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",71,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",72,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",70,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",71,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",72,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", "ac$move", "(FF)V", false);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(41, label14);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",86,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",87,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",88,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",89,methodVisitor,[]);
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(44, label12);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",17,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",92,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",93,methodVisitor,[]);
var label15 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",13,methodVisitor,[label15]);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(45, label16);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",98,methodVisitor,[]);
var label17 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label17]);
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(46, label18);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",64,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",16,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",70,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",71,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",72,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",69,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",70,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",71,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",72,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",80,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",118,methodVisitor,[]);
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(48, label17);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",120,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",13,methodVisitor,[label15]);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(49, label19);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",98,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label15]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",68,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",86,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",87,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",88,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",134,methodVisitor,[]);
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(53, label15);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",136,methodVisitor,[]);
var label20 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",9,methodVisitor,[label20]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",136,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",141,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",142,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",13,methodVisitor,[label20]);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(54, label21);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",144,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",146,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",136,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",149,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",150,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",151,methodVisitor,[]);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(55, label22);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",144,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",154,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",136,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",157,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",150,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",159,methodVisitor,[]);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(57, label23);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",120,methodVisitor,[]);
var label24 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label24]);
var label25 = new Label();
methodVisitor.visitLabel(label25);
methodVisitor.visitLineNumber(58, label25);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",163,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",146,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",166,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",167,methodVisitor,[]);
var label26 = new Label();
methodVisitor.visitLabel(label26);
methodVisitor.visitLineNumber(59, label26);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",168,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",154,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",166,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",172,methodVisitor,[]);
var label27 = new Label();
methodVisitor.visitLabel(label27);
methodVisitor.visitLineNumber(61, label27);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",173,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",174,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",175,methodVisitor,[]);
var label28 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",13,methodVisitor,[label28]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",177,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",174,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",175,methodVisitor,[]);
var label29 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label29]);
methodVisitor.visitLabel(label28);
methodVisitor.visitLineNumber(62, label28);
methodVisitor.visitFrame(O.F_FULL, 11, ["com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", O.TOP, O.FLOAT, O.FLOAT, O.INTEGER, O.FLOAT, O.FLOAT, O.FLOAT, O.FLOAT, O.TOP, O.LONG], 0, []);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",177,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",182,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",173,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",182,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",185,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",186,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",72,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",188,methodVisitor,[]);
var label30 = new Label();
methodVisitor.visitLabel(label30);
methodVisitor.visitLineNumber(63, label30);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",190,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",192,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",193,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",150,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",89,methodVisitor,[]);
methodVisitor.visitLabel(label29);
methodVisitor.visitLineNumber(66, label29);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
var label31 = new Label();
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",196,methodVisitor,[label31]);
methodVisitor.visitLabel(label24);
methodVisitor.visitFrame(O.F_FULL, 11, ["com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", O.TOP, O.FLOAT, O.FLOAT, O.INTEGER, O.FLOAT, O.FLOAT, O.TOP, O.TOP, O.TOP, O.LONG], 0, []);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",198,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",200,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",175,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",6,methodVisitor,[label31]);
var label32 = new Label();
methodVisitor.visitLabel(label32);
methodVisitor.visitLineNumber(67, label32);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",190,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",192,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",198,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",150,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",89,methodVisitor,[]);
methodVisitor.visitLabel(label31);
methodVisitor.visitLineNumber(70, label31);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",163,methodVisitor,[]);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",168,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", "ac$move", "(FF)V", false);
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(72, label20);
methodVisitor.visitFrame(O.F_FULL, 11, ["com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", O.TOP, O.FLOAT, O.FLOAT, O.INTEGER, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.LONG], 0, []);
bonsReuse(bonsOriginal,"tick(Lcom/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity;)V",14,methodVisitor,[]);
methodVisitor.visitMaxs(8, 12);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(2,"ac$interval","(J)I",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(8, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(11, label1);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitLdcInsn(Type.getType("Lcom/ninni/spawn/server/block/entity/SeaBunnyBlockEntity;"));
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label2);
methodVisitor.visitIntInsn(O.BIPUSH, 60);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(12, label2);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/lang/Class"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitLdcInsn(Type.getType("Lcom/ninni/spawn/server/block/entity/SeaStarBlockEntity;"));
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label4);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitLdcInsn(Type.getType("Lcom/ninni/spawn/server/block/entity/SeaUrchinBlockEntity;"));
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label5);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitIntInsn(O.BIPUSH, 120);
methodVisitor.visitVarInsn(O.ISTORE, 4);
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(13, label5);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitTypeInsn(O.NEW, "java/util/Random");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/util/Random", "<init>", "(J)V", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", "getInterval", "(Ljava/util/Random;)I", false);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(14, label3);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitLdcInsn(number('25214903917', NT.LONG));
methodVisitor.visitInsn(O.LXOR);
methodVisitor.visitLdcInsn(number('281474976710655', NT.LONG));
methodVisitor.visitInsn(O.LAND);
methodVisitor.visitVarInsn(O.LSTORE, 5);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(15, label6);
methodVisitor.visitFrame(O.F_APPEND,1, [O.LONG], 0, null);
methodVisitor.visitVarInsn(O.LLOAD, 5);
methodVisitor.visitLdcInsn(number('25214903917', NT.LONG));
methodVisitor.visitInsn(O.LMUL);
methodVisitor.visitLdcInsn(number('11', NT.LONG));
methodVisitor.visitInsn(O.LADD);
methodVisitor.visitLdcInsn(number('281474976710655', NT.LONG));
methodVisitor.visitInsn(O.LAND);
methodVisitor.visitVarInsn(O.LSTORE, 5);
methodVisitor.visitVarInsn(O.LLOAD, 5);
methodVisitor.visitIntInsn(O.BIPUSH, 17);
methodVisitor.visitInsn(O.LUSHR);
methodVisitor.visitInsn(O.L2I);
methodVisitor.visitVarInsn(O.ISTORE, 7);
methodVisitor.visitVarInsn(O.ILOAD, 7);
methodVisitor.visitIntInsn(O.BIPUSH, 120);
methodVisitor.visitInsn(O.IREM);
methodVisitor.visitVarInsn(O.ISTORE, 8);
methodVisitor.visitVarInsn(O.ILOAD, 7);
methodVisitor.visitVarInsn(O.ILOAD, 8);
methodVisitor.visitInsn(O.ISUB);
methodVisitor.visitIntInsn(O.BIPUSH, 119);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitJumpInsn(O.IFLT, label6);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(16, label7);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitVarInsn(O.ILOAD, 8);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(5, 9);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(2,"ac$move","(FF)V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(18, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.FLOAD, 1);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", "x", "F");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.FLOAD, 2);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/ninni/spawn/server/block/entity/SeaLifeDecoBlockEntity", "z", "F");
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(2, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
