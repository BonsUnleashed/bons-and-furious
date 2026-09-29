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
var BONS_KEY = "moon_animation_entity_guard"; var BONS_SCRIPT = "furious14-moon_animation.js"; var BONS_TARGET = "net.mcreator.underthemoon.init.EntityAnimationFactory";
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

// Insert Bons' entity guard into a copy of the installed method. Original mod
// dispatch code stays in its own JAR; this adapter does not embed that code.
function bonsInsertAnimationGuard(c) {
    var MethodNode = Java.type('org.objectweb.asm.tree.MethodNode');
    var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var LdcInsnNode = Java.type('org.objectweb.asm.tree.LdcInsnNode');
    var TypeInsnNode = Java.type('org.objectweb.asm.tree.TypeInsnNode');
    var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
    for (var i=0; i<c.methods.size(); i++) {
        var old=c.methods.get(i);
        if (old.name !== 'onEntityTick') continue;
        var exceptions=[];
        for(var e=0;e<old.exceptions.size();e++) exceptions.push(String(old.exceptions.get(e)));
        var copy=new MethodNode(old.access, old.name, old.desc, old.signature, exceptions);
        old.accept(copy);
        var nodes=copy.instructions.toArray(), call=null;
        for(var n=0;n<nodes.length;n++)
            if(nodes[n].getType()===5 && nodes[n].name==='getEntity') {call=nodes[n];break;}
        if(call===null) throw new Error('Missing guarded entity getter');
        var original=new LabelNode(), prefix=new InsnList();
        prefix.add(new VarInsnNode(O.ALOAD,0));
        prefix.add(new JumpInsnNode(O.IFNULL,original));
        prefix.add(new VarInsnNode(O.ALOAD,0));
        prefix.add(new MethodInsnNode(O.INVOKEVIRTUAL,'java/lang/Object','getClass','()Ljava/lang/Class;',false));
        prefix.add(new LdcInsnNode(Type.getType(copy.desc.substring(1,copy.desc.indexOf(')')))));
        prefix.add(new JumpInsnNode(O.IF_ACMPNE,original));
        prefix.add(new VarInsnNode(O.ALOAD,0));
        prefix.add(new MethodInsnNode(call.getOpcode(),call.owner,call.name,call.desc,call.itf));
        prefix.add(new TypeInsnNode(O.INSTANCEOF,'software/bernie/geckolib/animatable/GeoEntity'));
        prefix.add(new JumpInsnNode(O.IFNE,original));
        prefix.add(new InsnNode(O.RETURN));
        prefix.add(original);
        copy.instructions.insert(prefix);
        copy.maxStack=Math.max(copy.maxStack,2);
        c.methods.set(i,copy);
        return c;
    }
    throw new Error('Missing onEntityTick');
}

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"net.mcreator.underthemoon.init.EntityAnimationFactory"},transformer:function(c){return bonsGuarded(c,function(c){
bonsVerify(c,"<init>","()V","5942991974f47333822ecb04a032adb3b9163f71c0d69d81b0c174bd3882a02c");
bonsVerify(c,"onEntityTick","(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V","9f9c4b9d67ad9153303eeb117b998a4b3013f13ff3d2d96f50f93e7ea413b1eb");
return bonsInsertAnimationGuard(c);
});}}};}
