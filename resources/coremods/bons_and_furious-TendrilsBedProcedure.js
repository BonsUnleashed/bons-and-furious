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

var BONS_KEY="occult_bed_scan_guard";var BONS_SCRIPT="bons_and_furious-TendrilsBedProcedure.js";var BONS_TARGET="occult.procedures.TendrilsBedProcedure";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"occult.procedures.TendrilsBedProcedure"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","5942991974f47333822ecb04a032adb3b9163f71c0d69d81b0c174bd3882a02c");
bonsVerify(c,"onPlayerTick","(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V","ce2175f3b734a254eda6a8fac4bee9247992a1b220c230ab5aec93a270c30db3");
bonsVerify(c,"execute","(Lnet/minecraft/world/level/LevelAccessor;DDD)V","91977b3760634997bc3dc4253ee00814f9e88fc5e85496d5176d346920eb408d");
bonsVerify(c,"execute","(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V","8d030937271fc18435a2f68afd304c943f70ee399eaf023b1e7cd13e800205bd");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"execute","(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",142);
methodVisitor = new MethodNode(10,"execute","(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",null,[]);
methodVisitor.visitAnnotableParameterCount(5, true);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Ljavax/annotation/Nullable;", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(34, label0);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",58,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",103,methodVisitor,[]);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(35, label2);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",141,methodVisitor,[]);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(37, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",58,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",106,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(38, label3);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_7726_", "()Lnet/minecraft/server/level/ServerChunkCache;", false);
methodVisitor.visitVarInsn(O.ASTORE, 9);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(39, label4);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",5,methodVisitor,[]);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(40, label5);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",8,methodVisitor,[]);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(41, label6);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",14,methodVisitor,[]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(42, label7);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",15,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",7,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",20,methodVisitor,[]);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitFrame(O.F_FULL, 11, ["net/minecraftforge/eventbus/api/Event", "net/minecraft/world/level/LevelAccessor", O.DOUBLE, O.DOUBLE, O.DOUBLE, "net/minecraft/server/level/ServerLevel", "net/minecraft/server/level/ServerChunkCache", O.INTEGER, O.INTEGER, O.INTEGER, O.INTEGER], 0, []);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",15,methodVisitor,[]);
var label9 = new Label();
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",11,methodVisitor,[label9]);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(43, label10);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",7,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ISTORE, 14);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 14);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
var label12 = new Label();
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",11,methodVisitor,[label12]);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(44, label13);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",7,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ISTORE, 15);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 15);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
var label15 = new Label();
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",11,methodVisitor,[label15]);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(45, label16);
methodVisitor.visitVarInsn(O.ILOAD, 14);
methodVisitor.visitVarInsn(O.ILOAD, 14);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",42,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ILOAD, 15);
methodVisitor.visitVarInsn(O.ILOAD, 15);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",6,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",26,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",42,methodVisitor,[]);
methodVisitor.visitVarInsn(O.DSTORE, 16);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(51, label17);
methodVisitor.visitVarInsn(O.DLOAD, 16);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",55,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",56,methodVisitor,[]);
var label18 = new Label();
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",57,methodVisitor,[label18]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",98,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",99,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",56,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",101,methodVisitor,[label18]);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(52, label19);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",59,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ILOAD, 14);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",42,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",63,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",42,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",67,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ILOAD, 15);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",27,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",42,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",71,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 18);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(54, label20);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitVarInsn(O.ALOAD, 18);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123341_", "()I", false);
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitInsn(O.ISHR);
methodVisitor.visitVarInsn(O.ALOAD, 18);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos", "m_123343_", "()I", false);
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitInsn(O.ISHR);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerChunkCache", "m_7131_", "(II)Lnet/minecraft/world/level/chunk/LevelChunk;", false);
methodVisitor.visitVarInsn(O.ASTORE, 19);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(55, label21);
methodVisitor.visitVarInsn(O.ALOAD, 19);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",129,methodVisitor,[label18]);
methodVisitor.visitVarInsn(O.ALOAD, 19);
methodVisitor.visitVarInsn(O.ALOAD, 18);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/chunk/LevelChunk", "m_8055_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", false);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/tags/BlockTags", "f_13038_", "Lnet/minecraft/tags/TagKey;");
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",78,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",79,methodVisitor,[label18]);
methodVisitor.visitVarInsn(O.ALOAD, 8);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",81,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 18);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(56, label22);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_45517_", "(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I", false);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",96,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",11,methodVisitor,[label18]);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(57, label23);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",108,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",109,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",110,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 18);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",125,methodVisitor,[]);
var label24 = new Label();
methodVisitor.visitLabel(label24);
methodVisitor.visitLineNumber(58, label24);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",126,methodVisitor,[]);
methodVisitor.visitVarInsn(O.ASTORE, 20);
var label25 = new Label();
methodVisitor.visitLabel(label25);
methodVisitor.visitLineNumber(59, label25);
methodVisitor.visitVarInsn(O.ALOAD, 20);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",129,methodVisitor,[label18]);
var label26 = new Label();
methodVisitor.visitLabel(label26);
methodVisitor.visitLineNumber(60, label26);
methodVisitor.visitVarInsn(O.ALOAD, 20);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",131,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",131,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",131,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",134,methodVisitor,[]);
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(44, label18);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitIincInsn(15, 1);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",136,methodVisitor,[label14]);
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(43, label15);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitIincInsn(14, 1);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",136,methodVisitor,[label11]);
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(42, label12);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",135,methodVisitor,[]);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",136,methodVisitor,[label8]);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(67, label9);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD)V",141,methodVisitor,[]);
methodVisitor.visitMaxs(8, 21);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
