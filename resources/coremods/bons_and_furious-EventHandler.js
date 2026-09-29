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

var BONS_KEY="hostilevillages_distant_generation_queue";var BONS_SCRIPT="bons_and_furious-EventHandler.js";var BONS_TARGET="com.hostilevillages.event.EventHandler";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.hostilevillages.event.EventHandler"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","5942991974f47333822ecb04a032adb3b9163f71c0d69d81b0c174bd3882a02c");
bonsVerify(c,"onEntityAdd","(Lnet/minecraftforge/event/entity/EntityJoinLevelEvent;)V","ab0925088758bd8b2fec309544ab704b89d5564d848b1d520adf3e96732aa2af");
bonsVerify(c,"replaceEntityOnSpawn","(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z","9ef1cf43af15ea70136a3bbfe03225423fcd3d30bf5e81bf5d2eff50bd846d7e");
bonsVerify(c,"addToWorld","(Lnet/minecraftforge/event/TickEvent$LevelTickEvent;)V","0f9c62420bc5c63d62b43cff856b98765208095552cf022a2e9672581f5905f1");
bonsVerify(c,"<clinit>","()V","f14b705af3311eef2af81fee016dabbdb43f8f2ed1809e9b7a15c9573f4b7891");
var bonsOriginal=bonsCapture(c);
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"replaceEntityOnSpawn","(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",172);
methodVisitor = new MethodNode(9,"replaceEntityOnSpawn","(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",null,[]);
methodVisitor.visitCode();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",107,methodVisitor,[]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/hostilevillages/DistantGenerationCompat", "isDistantGeneration", "(Ljava/lang/Object;)Z", false);
var label0 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label0]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",22,methodVisitor,[]);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(65, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",1,methodVisitor,[]);
var label1 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",2,methodVisitor,[label1]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",5,methodVisitor,[]);
var label2 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",6,methodVisitor,[label2]);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(67, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",10,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",12,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",13,methodVisitor,[label2]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",15,methodVisitor,[label2]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label2]);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(69, label3);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",22,methodVisitor,[]);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(73, label2);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",25,methodVisitor,[]);
var label4 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",26,methodVisitor,[label4]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",29,methodVisitor,[]);
var label5 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",6,methodVisitor,[label5]);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(75, label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",31,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",34,methodVisitor,[]);
var label6 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label6]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",25,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",6,methodVisitor,[label6]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(77, label7);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",22,methodVisitor,[]);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(80, label6);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",9,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",10,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",11,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",47,methodVisitor,[]);
var label8 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",48,methodVisitor,[label8]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",14,methodVisitor,[]);
var label9 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",15,methodVisitor,[label9]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",19,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",2,methodVisitor,[label9]);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(82, label8);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",56,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",57,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",58,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",59,methodVisitor,[]);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(83, label10);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",18,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",63,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",64,methodVisitor,[]);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(86, label9);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",8,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",67,methodVisitor,[]);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(88, label11);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",14,methodVisitor,[]);
var label12 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",69,methodVisitor,[label12]);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(90, label13);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",22,methodVisitor,[]);
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(93, label12);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",25,methodVisitor,[]);
var label14 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",6,methodVisitor,[label14]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",77,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",78,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",79,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label14]);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(95, label15);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",22,methodVisitor,[]);
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(98, label14);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",84,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",85,methodVisitor,[]);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(100, label16);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",87,methodVisitor,[]);
var label17 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label17]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label17]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",21,methodVisitor,[]);
var label18 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",94,methodVisitor,[label18]);
methodVisitor.visitLabel(label17);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",40,methodVisitor,[]);
methodVisitor.visitLabel(label18);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",96,methodVisitor,[]);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(102, label19);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",98,methodVisitor,[]);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitFrame(O.F_APPEND,2, [O.INTEGER, O.INTEGER], 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",99,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",31,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",103,methodVisitor,[]);
var label21 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",104,methodVisitor,[label21]);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(104, label22);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",14,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",106,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",107,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",108,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",109,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",110,methodVisitor,[]);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(106, label23);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",25,methodVisitor,[]);
var label24 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",6,methodVisitor,[label24]);
var label25 = new Label();
methodVisitor.visitLabel(label25);
methodVisitor.visitLineNumber(109, label25);
var label26 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",94,methodVisitor,[label26]);
methodVisitor.visitLabel(label24);
methodVisitor.visitLineNumber(112, label24);
methodVisitor.visitFrame(O.F_APPEND,1, ["net/minecraft/world/entity/Entity"], 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",87,methodVisitor,[]);
var label27 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",2,methodVisitor,[label27]);
var label28 = new Label();
methodVisitor.visitLabel(label28);
methodVisitor.visitLineNumber(114, label28);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",94,methodVisitor,[label26]);
methodVisitor.visitLabel(label27);
methodVisitor.visitLineNumber(117, label27);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",120,methodVisitor,[]);
var label29 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label29]);
var label30 = new Label();
methodVisitor.visitLabel(label30);
methodVisitor.visitLineNumber(119, label30);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",124,methodVisitor,[]);
var label31 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",94,methodVisitor,[label31]);
methodVisitor.visitLabel(label29);
methodVisitor.visitLineNumber(121, label29);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",4,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",128,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",129,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",2,methodVisitor,[label26]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",90,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label31]);
var label32 = new Label();
methodVisitor.visitLabel(label32);
methodVisitor.visitLineNumber(123, label32);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",94,methodVisitor,[label26]);
methodVisitor.visitLabel(label31);
methodVisitor.visitLineNumber(126, label31);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",31,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",32,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",33,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",139,methodVisitor,[]);
var label33 = new Label();
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",20,methodVisitor,[label33]);
var label34 = new Label();
methodVisitor.visitLabel(label34);
methodVisitor.visitLineNumber(128, label34);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",141,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",90,methodVisitor,[]);
var label35 = new Label();
methodVisitor.visitLabel(label35);
methodVisitor.visitLineNumber(129, label35);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",91,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",147,methodVisitor,[]);
var label36 = new Label();
methodVisitor.visitLabel(label36);
methodVisitor.visitLineNumber(128, label36);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",148,methodVisitor,[]);
methodVisitor.visitLabel(label33);
methodVisitor.visitLineNumber(132, label33);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",151,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",153,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",0,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",155,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",156,methodVisitor,[]);
var label37 = new Label();
methodVisitor.visitLabel(label37);
methodVisitor.visitLineNumber(133, label37);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",157,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",158,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",57,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",111,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",107,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",108,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",163,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",164,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",165,methodVisitor,[]);
methodVisitor.visitLabel(label26);
methodVisitor.visitLineNumber(102, label26);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",166,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",94,methodVisitor,[label20]);
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(136, label21);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",21,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",22,methodVisitor,[]);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(139, label5);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",40,methodVisitor,[]);
bonsReuse(bonsOriginal,"replaceEntityOnSpawn(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",22,methodVisitor,[]);
var label38 = new Label();
methodVisitor.visitLabel(label38);
methodVisitor.visitLocalVariable("replacementEntity", "Lnet/minecraft/world/entity/Entity;", null, label23, label26, 4);
methodVisitor.visitLocalVariable("i", "I", null, label20, label21, 3);
methodVisitor.visitLocalVariable("requirePersistance", "Z", null, label19, label5, 2);
methodVisitor.visitLocalVariable("entity", "Lnet/minecraft/world/entity/Entity;", null, label0, label38, 0);
methodVisitor.visitLocalVariable("world", "Lnet/minecraft/world/level/ServerLevelAccessor;", null, label0, label38, 1);
methodVisitor.visitMaxs(7, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
