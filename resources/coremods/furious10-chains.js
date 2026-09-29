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
var BONS_KEY = "iceandfire_lazy_reference_lists"; var BONS_SCRIPT = "furious10-chains.js"; var BONS_TARGET = "com.github.alexthe666.iceandfire.entity.props.ChainData";
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
 if(result.join('\n')!==expected)throw new Error('Opcode/operand mismatch '+name+desc);
}

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.github.alexthe666.iceandfire.entity.props.ChainData"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n177");
bonsVerify(c,"tickChain","(Lnet/minecraft/world/entity/LivingEntity;)V","25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|isInitialized|Z\n154|7\n25|0\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|com/github/alexthe666/iceandfire/entity/props/ChainData|initialize|(Lnet/minecraft/world/level/Level;)V|false\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n199|11\n177\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|2\n25|2\n185|java/util/Iterator|hasNext|()Z|true\n153|79\n25|2\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/world/entity/Entity\n58|3\n25|3\n25|1\n182|net/minecraft/world/entity/Entity|m_20270_|(Lnet/minecraft/world/entity/Entity;)F|false\n141\n57|4\n24|4\n18|7\n151\n158|78\n25|3\n182|net/minecraft/world/entity/Entity|m_20185_|()D|false\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_20185_|()D|false\n103\n24|4\n111\n57|6\n25|3\n182|net/minecraft/world/entity/Entity|m_20186_|()D|false\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_20186_|()D|false\n103\n24|4\n111\n57|8\n25|3\n182|net/minecraft/world/entity/Entity|m_20189_|()D|false\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_20189_|()D|false\n103\n24|4\n111\n57|10\n25|1\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_20184_|()Lnet/minecraft/world/phys/Vec3;|false\n24|6\n24|6\n184|java/lang/Math|abs|(D)D|false\n107\n18|0.4\n107\n24|8\n24|8\n184|java/lang/Math|abs|(D)D|false\n107\n18|0.2\n107\n24|10\n24|10\n184|java/lang/Math|abs|(D)D|false\n107\n18|0.4\n107\n182|net/minecraft/world/phys/Vec3|m_82520_|(DDD)Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/entity/LivingEntity|m_20256_|(Lnet/minecraft/world/phys/Vec3;)V|false\n167|15\n177");
bonsVerify(c,"getChainedTo","()Ljava/util/List;","25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n184|java/util/Collections|emptyList|()Ljava/util/List;|false\n184|java/util/Objects|requireNonNullElse|(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;|false\n192|java/util/List\n176");
bonsVerify(c,"clearChains","()V","25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n199|4\n177\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|triggerClientUpdate|Z\n177");
bonsVerify(c,"attachChain","(Lnet/minecraft/world/entity/Entity;)V","25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n199|9\n25|0\n187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n167|15\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n25|1\n185|java/util/List|contains|(Ljava/lang/Object;)Z|true\n153|15\n177\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n25|1\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|triggerClientUpdate|Z\n177");
bonsVerify(c,"removeChain","(Lnet/minecraft/world/entity/Entity;)V","25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n199|4\n177\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n25|1\n185|java/util/List|remove|(Ljava/lang/Object;)Z|true\n87\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|triggerClientUpdate|Z\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n185|java/util/List|isEmpty|()Z|true\n153|19\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n177");
bonsVerify(c,"isChainedTo","(Lnet/minecraft/world/entity/Entity;)Z","25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n198|7\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n185|java/util/List|isEmpty|()Z|true\n153|9\n3\n172\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n25|1\n185|java/util/List|contains|(Ljava/lang/Object;)Z|true\n172");
bonsVerify(c,"serialize","(Lnet/minecraft/nbt/CompoundTag;)V","187|net/minecraft/nbt/CompoundTag\n89\n183|net/minecraft/nbt/CompoundTag|<init>|()V|false\n58|2\n187|net/minecraft/nbt/ListTag\n89\n183|net/minecraft/nbt/ListTag|<init>|()V|false\n58|3\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n198|51\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n185|java/util/List|size|()I|true\n188|10\n58|4\n3\n54|5\n21|5\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n185|java/util/List|size|()I|true\n162|42\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n21|5\n185|java/util/List|get|(I)Ljava/lang/Object;|true\n192|net/minecraft/world/entity/Entity\n58|6\n25|4\n21|5\n25|6\n182|net/minecraft/world/entity/Entity|m_19879_|()I|false\n79\n25|3\n25|6\n182|net/minecraft/world/entity/Entity|m_20148_|()Ljava/util/UUID;|false\n184|net/minecraft/nbt/NbtUtils|m_129226_|(Ljava/util/UUID;)Lnet/minecraft/nbt/IntArrayTag;|false\n182|net/minecraft/nbt/ListTag|add|(Ljava/lang/Object;)Z|false\n87\n132|5|1\n167|18\n25|2\n18|chainedToIds\n25|4\n182|net/minecraft/nbt/CompoundTag|m_128385_|(Ljava/lang/String;[I)V|false\n25|2\n18|chainedToUUIDs\n25|3\n182|net/minecraft/nbt/CompoundTag|m_128365_|(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;|false\n87\n25|1\n18|chainedData\n25|2\n182|net/minecraft/nbt/CompoundTag|m_128365_|(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;|false\n87\n177");
bonsVerify(c,"deserialize","(Lnet/minecraft/nbt/CompoundTag;)V","25|1\n18|chainedData\n182|net/minecraft/nbt/CompoundTag|m_128469_|(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;|false\n58|2\n25|2\n18|chainedToIds\n182|net/minecraft/nbt/CompoundTag|m_128465_|(Ljava/lang/String;)[I|false\n58|3\n25|2\n18|chainedToUUIDs\n16|11\n182|net/minecraft/nbt/CompoundTag|m_128437_|(Ljava/lang/String;I)Lnet/minecraft/nbt/ListTag;|false\n58|4\n25|0\n3\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|isInitialized|Z\n25|3\n190\n158|47\n25|0\n187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToIds|Ljava/util/List;\n25|3\n58|5\n25|5\n190\n54|6\n3\n54|7\n21|7\n21|6\n162|46\n25|5\n21|7\n46\n54|8\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToIds|Ljava/util/List;\n21|8\n184|java/lang/Integer|valueOf|(I)Ljava/lang/Integer;|false\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n132|7|1\n167|31\n167|50\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToIds|Ljava/util/List;\n25|4\n182|net/minecraft/nbt/ListTag|isEmpty|()Z|false\n154|76\n25|0\n187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToUUIDs|Ljava/util/List;\n25|4\n182|net/minecraft/nbt/ListTag|iterator|()Ljava/util/Iterator;|false\n58|5\n25|5\n185|java/util/Iterator|hasNext|()Z|true\n153|75\n25|5\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/nbt/Tag\n58|6\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToUUIDs|Ljava/util/List;\n25|6\n184|net/minecraft/nbt/NbtUtils|m_129233_|(Lnet/minecraft/nbt/Tag;)Ljava/util/UUID;|false\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n167|61\n167|79\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToUUIDs|Ljava/util/List;\n177");
bonsVerify(c,"doesClientNeedUpdate","()Z","25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|triggerClientUpdate|Z\n153|8\n25|0\n3\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|triggerClientUpdate|Z\n4\n172\n3\n172");
bonsVerify(c,"initialize","(Lnet/minecraft/world/level/Level;)V","187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|2\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToUUIDs|Ljava/util/List;\n198|39\n25|1\n193|net/minecraft/server/level/ServerLevel\n153|39\n25|1\n192|net/minecraft/server/level/ServerLevel\n58|3\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToUUIDs|Ljava/util/List;\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|4\n25|4\n185|java/util/Iterator|hasNext|()Z|true\n153|35\n25|4\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|java/util/UUID\n58|5\n25|3\n25|5\n182|net/minecraft/server/level/ServerLevel|m_8791_|(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;|false\n58|6\n25|6\n198|34\n25|2\n25|6\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n167|17\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|triggerClientUpdate|Z\n167|69\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToIds|Ljava/util/List;\n198|69\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToIds|Ljava/util/List;\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|4\n25|4\n185|java/util/Iterator|hasNext|()Z|true\n153|69\n25|4\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n54|5\n21|5\n2\n160|58\n167|46\n25|1\n21|5\n182|net/minecraft/world/level/Level|m_6815_|(I)Lnet/minecraft/world/entity/Entity;|false\n58|6\n25|6\n198|68\n25|2\n25|6\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n167|46\n25|2\n185|java/util/List|isEmpty|()Z|true\n154|76\n25|0\n25|2\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n167|79\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedTo|Ljava/util/List;\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToIds|Ljava/util/List;\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|chainedToUUIDs|Ljava/util/List;\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/ChainData|isInitialized|Z\n177");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"serialize","(Lnet/minecraft/nbt/CompoundTag;)V",57);
removeMethod(c,"initialize","(Lnet/minecraft/world/level/Level;)V",89);
methodVisitor = new MethodNode(1,"serialize","(Lnet/minecraft/nbt/CompoundTag;)V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(133, label0);
methodVisitor.visitTypeInsn(O.NEW, "net/minecraft/nbt/CompoundTag");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/nbt/CompoundTag", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(134, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedTo", "Ljava/util/List;");
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label2);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(135, label3);
methodVisitor.visitTypeInsn(O.NEW, "net/minecraft/nbt/ListTag");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/nbt/ListTag", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(136, label4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedTo", "Ljava/util/List;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
methodVisitor.visitIntInsn(O.NEWARRAY, O.T_INT);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(137, label5);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 5);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_FULL, 6, ["com/github/alexthe666/iceandfire/entity/props/ChainData", "net/minecraft/nbt/CompoundTag", "net/minecraft/nbt/CompoundTag", "net/minecraft/nbt/ListTag", "[I", O.INTEGER], 0, []);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedTo", "Ljava/util/List;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label7);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(138, label8);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedTo", "Ljava/util/List;");
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/entity/Entity");
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(139, label9);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_19879_", "()I", false);
methodVisitor.visitInsn(O.IASTORE);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(140, label10);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_20148_", "()Ljava/util/UUID;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/nbt/NbtUtils", "m_129226_", "(Ljava/util/UUID;)Lnet/minecraft/nbt/IntArrayTag;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/nbt/ListTag", "add", "(Ljava/lang/Object;)Z", false);
methodVisitor.visitInsn(O.POP);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(137, label11);
methodVisitor.visitIincInsn(5, 1);
methodVisitor.visitJumpInsn(O.GOTO, label6);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(142, label7);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitLdcInsn("chainedToIds");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag", "m_128385_", "(Ljava/lang/String;[I)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitLdcInsn("chainedToUUIDs");
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag", "m_128365_", "(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", false);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(144, label2);
methodVisitor.visitFrame(O.F_CHOP,3, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("chainedData");
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/nbt/CompoundTag", "m_128365_", "(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;", false);
methodVisitor.visitInsn(O.POP);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(145, label12);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(3, 7);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(2,"initialize","(Lnet/minecraft/world/level/Level;)V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(113, label0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(114, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedToUUIDs", "Ljava/util/List;");
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/ServerLevel");
methodVisitor.visitJumpInsn(O.IFEQ, label2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/ServerLevel");
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(115, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedToUUIDs", "Ljava/util/List;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_APPEND,3, ["java/util/ArrayList", "net/minecraft/server/level/ServerLevel", "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label5);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/util/UUID");
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(116, label6);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_8791_", "(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;", false);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(117, label7);
methodVisitor.visitVarInsn(O.ALOAD, 6);
var label8 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label8);
methodVisitor.visitJumpInsn(O.GOTO, label4);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(118, label8);
methodVisitor.visitFrame(O.F_APPEND,2, ["java/util/UUID", "net/minecraft/world/entity/Entity"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
var label9 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label9);
methodVisitor.visitTypeInsn(O.NEW, "java/util/ArrayList");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(119, label9);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
methodVisitor.visitInsn(O.POP);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(120, label10);
methodVisitor.visitJumpInsn(O.GOTO, label4);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(121, label5);
methodVisitor.visitFrame(O.F_CHOP,2, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "triggerClientUpdate", "Z");
var label11 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label11);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(122, label2);
methodVisitor.visitFrame(O.F_FULL, 3, ["com/github/alexthe666/iceandfire/entity/props/ChainData", "net/minecraft/world/level/Level", O.NULL], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedToIds", "Ljava/util/List;");
methodVisitor.visitJumpInsn(O.IFNULL, label11);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(123, label12);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedToIds", "Ljava/util/List;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitFrame(O.F_FULL, 5, ["com/github/alexthe666/iceandfire/entity/props/ChainData", "net/minecraft/world/level/Level", "java/util/ArrayList", O.TOP, "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
methodVisitor.visitJumpInsn(O.IFEQ, label11);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Integer");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
methodVisitor.visitVarInsn(O.ISTORE, 5);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(125, label14);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitInsn(O.ICONST_M1);
methodVisitor.visitJumpInsn(O.IF_ICMPEQ, label13);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_6815_", "(I)Lnet/minecraft/world/entity/Entity;", false);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label15 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label15);
methodVisitor.visitJumpInsn(O.GOTO, label13);
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(126, label15);
methodVisitor.visitFrame(O.F_APPEND,2, [O.INTEGER, "net/minecraft/world/entity/Entity"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
var label16 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label16);
methodVisitor.visitTypeInsn(O.NEW, "java/util/ArrayList");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(127, label16);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
methodVisitor.visitInsn(O.POP);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(128, label17);
methodVisitor.visitJumpInsn(O.GOTO, label13);
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(130, label11);
methodVisitor.visitFrame(O.F_FULL, 3, ["com/github/alexthe666/iceandfire/entity/props/ChainData", "net/minecraft/world/level/Level", "java/util/ArrayList"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedTo", "Ljava/util/List;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedToIds", "Ljava/util/List;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "chainedToUUIDs", "Ljava/util/List;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/ChainData", "isInitialized", "Z");
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(131, label18);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(2, 7);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
