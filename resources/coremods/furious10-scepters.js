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
var BONS_KEY = "iceandfire_lazy_reference_lists"; var BONS_SCRIPT = "furious10-scepters.js"; var BONS_TARGET = "com.github.alexthe666.iceandfire.entity.props.MiscData";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.github.alexthe666.iceandfire.entity.props.MiscData"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n177");
bonsVerify(c,"tickMisc","(Lnet/minecraft/world/entity/LivingEntity;)V","25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|isInitialized|Z\n154|7\n25|0\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|com/github/alexthe666/iceandfire/entity/props/MiscData|initialize|(Lnet/minecraft/world/level/Level;)V|false\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|loveTicks|I\n158|44\n25|0\n89\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|loveTicks|I\n4\n100\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|loveTicks|I\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|loveTicks|I\n154|23\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n177\n25|1\n193|net/minecraft/world/entity/Mob\n153|41\n25|1\n192|net/minecraft/world/entity/Mob\n58|2\n25|2\n1\n182|net/minecraft/world/entity/Mob|m_6598_|(Lnet/minecraft/world/entity/player/Player;)V|false\n25|2\n1\n182|net/minecraft/world/entity/Mob|m_6703_|(Lnet/minecraft/world/entity/LivingEntity;)V|false\n25|2\n1\n182|net/minecraft/world/entity/Mob|m_6710_|(Lnet/minecraft/world/entity/LivingEntity;)V|false\n25|2\n3\n182|net/minecraft/world/entity/Mob|m_21561_|(Z)V|false\n25|0\n25|1\n182|com/github/alexthe666/iceandfire/entity/props/MiscData|createLoveParticles|(Lnet/minecraft/world/entity/LivingEntity;)V|false\n177");
bonsVerify(c,"getTargetedByScepter","()Ljava/util/List;","25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n184|java/util/Collections|emptyList|()Ljava/util/List;|false\n184|java/util/Objects|requireNonNullElse|(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;|false\n192|java/util/List\n176");
bonsVerify(c,"addScepterTarget","(Lnet/minecraft/world/entity/LivingEntity;)V","25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n199|9\n25|0\n187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n167|15\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n25|1\n185|java/util/List|contains|(Ljava/lang/Object;)Z|true\n153|15\n177\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n25|1\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n177");
bonsVerify(c,"removeScepterTarget","(Lnet/minecraft/world/entity/LivingEntity;)V","25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n199|4\n177\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n25|1\n185|java/util/List|remove|(Ljava/lang/Object;)Z|true\n87\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n177");
bonsVerify(c,"setLoveTicks","(I)V","25|0\n21|1\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|loveTicks|I\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n177");
bonsVerify(c,"setLungeTicks","(I)V","25|0\n21|1\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|lungeTicks|I\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n177");
bonsVerify(c,"setDismounted","(Z)V","25|0\n21|1\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|hasDismounted|Z\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n177");
bonsVerify(c,"serialize","(Lnet/minecraft/nbt/CompoundTag;)V","187|net/minecraft/nbt/CompoundTag\n89\n183|net/minecraft/nbt/CompoundTag|<init>|()V|false\n58|2\n25|2\n18|loveTicks\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|loveTicks|I\n182|net/minecraft/nbt/CompoundTag|m_128405_|(Ljava/lang/String;I)V|false\n25|2\n18|lungeTicks\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|lungeTicks|I\n182|net/minecraft/nbt/CompoundTag|m_128405_|(Ljava/lang/String;I)V|false\n25|2\n18|hasDismounted\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|hasDismounted|Z\n182|net/minecraft/nbt/CompoundTag|m_128379_|(Ljava/lang/String;Z)V|false\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n198|49\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n185|java/util/List|size|()I|true\n188|10\n58|3\n3\n54|4\n21|4\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n185|java/util/List|size|()I|true\n162|45\n25|3\n21|4\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n21|4\n185|java/util/List|get|(I)Ljava/lang/Object;|true\n192|net/minecraft/world/entity/LivingEntity\n182|net/minecraft/world/entity/LivingEntity|m_19879_|()I|false\n79\n132|4|1\n167|29\n25|1\n18|targetedByScepterIds\n25|3\n182|net/minecraft/nbt/CompoundTag|m_128385_|(Ljava/lang/String;[I)V|false\n25|1\n18|miscData\n25|2\n182|net/minecraft/nbt/CompoundTag|m_128365_|(Ljava/lang/String;Lnet/minecraft/nbt/Tag;)Lnet/minecraft/nbt/Tag;|false\n87\n177");
bonsVerify(c,"deserialize","(Lnet/minecraft/nbt/CompoundTag;)V","25|1\n18|miscData\n182|net/minecraft/nbt/CompoundTag|m_128469_|(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;|false\n58|2\n25|0\n25|2\n18|loveTicks\n182|net/minecraft/nbt/CompoundTag|m_128451_|(Ljava/lang/String;)I|false\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|loveTicks|I\n25|0\n25|2\n18|lungeTicks\n182|net/minecraft/nbt/CompoundTag|m_128451_|(Ljava/lang/String;)I|false\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|lungeTicks|I\n25|0\n25|2\n18|hasDismounted\n182|net/minecraft/nbt/CompoundTag|m_128471_|(Ljava/lang/String;)Z|false\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|hasDismounted|Z\n25|2\n18|targetedByScepterIds\n182|net/minecraft/nbt/CompoundTag|m_128465_|(Ljava/lang/String;)[I|false\n58|3\n25|0\n3\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|isInitialized|Z\n25|3\n190\n158|56\n25|0\n187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepterIds|Ljava/util/List;\n25|3\n58|4\n25|4\n190\n54|5\n3\n54|6\n21|6\n21|5\n162|56\n25|4\n21|6\n46\n54|7\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepterIds|Ljava/util/List;\n21|7\n184|java/lang/Integer|valueOf|(I)Ljava/lang/Integer;|false\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n132|6|1\n167|41\n177");
bonsVerify(c,"doesClientNeedUpdate","()Z","25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n153|8\n25|0\n3\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|triggerClientUpdate|Z\n4\n172\n3\n172");
bonsVerify(c,"createLoveParticles","(Lnet/minecraft/world/entity/LivingEntity;)V","25|1\n182|net/minecraft/world/entity/LivingEntity|m_217043_|()Lnet/minecraft/util/RandomSource;|false\n16|7\n185|net/minecraft/util/RandomSource|m_188503_|(I)I|true\n154|49\n3\n54|2\n21|2\n8\n162|49\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_9236_|()Lnet/minecraft/world/level/Level;|false\n178|net/minecraft/core/particles/ParticleTypes|f_123750_|Lnet/minecraft/core/particles/SimpleParticleType;\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_20185_|()D|false\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_217043_|()Lnet/minecraft/util/RandomSource;|false\n185|net/minecraft/util/RandomSource|m_188500_|()D|true\n18|0.5\n103\n18|3\n107\n99\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_20186_|()D|false\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_217043_|()Lnet/minecraft/util/RandomSource;|false\n185|net/minecraft/util/RandomSource|m_188500_|()D|true\n18|0.5\n103\n18|3\n107\n99\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_20189_|()D|false\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_217043_|()Lnet/minecraft/util/RandomSource;|false\n185|net/minecraft/util/RandomSource|m_188500_|()D|true\n18|0.5\n103\n18|3\n107\n99\n14\n14\n14\n182|net/minecraft/world/level/Level|m_7106_|(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V|false\n132|2|1\n167|7\n177");
bonsVerify(c,"initialize","(Lnet/minecraft/world/level/Level;)V","187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|2\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepterIds|Ljava/util/List;\n198|38\n25|0\n180|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepterIds|Ljava/util/List;\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|3\n25|3\n185|java/util/Iterator|hasNext|()Z|true\n153|38\n25|3\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n54|4\n21|4\n2\n160|23\n167|11\n25|1\n21|4\n182|net/minecraft/world/level/Level|m_6815_|(I)Lnet/minecraft/world/entity/Entity;|false\n58|5\n25|5\n193|net/minecraft/world/entity/LivingEntity\n153|37\n25|5\n192|net/minecraft/world/entity/LivingEntity\n58|6\n25|2\n25|6\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n167|11\n25|2\n185|java/util/List|isEmpty|()Z|true\n154|45\n25|0\n25|2\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n167|48\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepter|Ljava/util/List;\n25|0\n1\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|targetedByScepterIds|Ljava/util/List;\n25|0\n4\n181|com/github/alexthe666/iceandfire/entity/props/MiscData|isInitialized|Z\n177");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"initialize","(Lnet/minecraft/world/level/Level;)V",55);
methodVisitor = new MethodNode(2,"initialize","(Lnet/minecraft/world/level/Level;)V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(152, label0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(153, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/MiscData", "targetedByScepterIds", "Ljava/util/List;");
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/github/alexthe666/iceandfire/entity/props/MiscData", "targetedByScepterIds", "Ljava/util/List;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_APPEND,2, ["java/util/ArrayList", "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
methodVisitor.visitJumpInsn(O.IFEQ, label2);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Integer");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(155, label4);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitInsn(O.ICONST_M1);
methodVisitor.visitJumpInsn(O.IF_ICMPEQ, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_6815_", "(I)Lnet/minecraft/world/entity/Entity;", false);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ASTORE, 5);
methodVisitor.visitVarInsn(O.ASTORE, 7);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/world/entity/LivingEntity");
methodVisitor.visitJumpInsn(O.IFEQ, label3);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/entity/LivingEntity");
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(156, label5);
methodVisitor.visitVarInsn(O.ALOAD, 2);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label6);
methodVisitor.visitTypeInsn(O.NEW, "java/util/ArrayList");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(157, label6);
methodVisitor.visitFrame(O.F_FULL, 8, ["com/github/alexthe666/iceandfire/entity/props/MiscData", "net/minecraft/world/level/Level", "java/util/ArrayList", "java/util/Iterator", O.INTEGER, "net/minecraft/world/entity/Entity", "net/minecraft/world/entity/LivingEntity", "net/minecraft/world/entity/Entity"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
methodVisitor.visitInsn(O.POP);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(158, label7);
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(159, label2);
methodVisitor.visitFrame(O.F_FULL, 3, ["com/github/alexthe666/iceandfire/entity/props/MiscData", "net/minecraft/world/level/Level", "java/util/ArrayList"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/MiscData", "targetedByScepter", "Ljava/util/List;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/MiscData", "targetedByScepterIds", "Ljava/util/List;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitFieldInsn(O.PUTFIELD, "com/github/alexthe666/iceandfire/entity/props/MiscData", "isInitialized", "Z");
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(160, label8);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(2, 8);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
