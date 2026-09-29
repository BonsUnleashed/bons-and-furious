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
var BONS_KEY = "ars_direct_perk_snapshots"; var BONS_SCRIPT = "furious10-perks.js"; var BONS_TARGET = "com.hollingsworth.arsnouveau.api.util.PerkUtil";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.hollingsworth.arsnouveau.api.util.PerkUtil"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n177");
bonsVerify(c,"getPerkHolder","(Lnet/minecraft/world/item/ItemStack;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;","25|0\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n184|com/hollingsworth/arsnouveau/api/registry/PerkRegistry|getPerkProvider|(Lnet/minecraft/world/item/Item;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkProvider;|false\n58|1\n25|1\n199|8\n1\n167|11\n25|1\n25|0\n185|com/hollingsworth/arsnouveau/api/perk/IPerkProvider|getPerkHolder|(Ljava/lang/Object;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;|true\n176");
bonsVerify(c,"perkValue","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/Attribute;)D","25|0\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_21051_|(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;|false\n58|2\n25|2\n199|9\n25|1\n182|net/minecraft/world/entity/ai/attributes/Attribute|m_22082_|()D|false\n167|11\n25|2\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22135_|()D|false\n175");
bonsVerify(c,"valueOrZero","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/Attribute;)D","25|0\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_21051_|(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;|false\n199|6\n14\n167|9\n25|0\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_21133_|(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D|false\n175");
bonsVerify(c,"getPerksAsItems","(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;","25|0\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|getPerkHolder|(Lnet/minecraft/world/item/ItemStack;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;|false\n58|1\n187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|2\n25|1\n199|11\n25|2\n176\n25|1\n185|com/hollingsworth/arsnouveau/api/perk/IPerkHolder|getPerks|()Ljava/util/List;|true\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|3\n25|3\n185|java/util/Iterator|hasNext|()Z|true\n153|35\n25|3\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|com/hollingsworth/arsnouveau/api/perk/IPerk\n58|4\n184|com/hollingsworth/arsnouveau/api/registry/PerkRegistry|getPerkItemMap|()Ljava/util/Map;|false\n25|4\n185|com/hollingsworth/arsnouveau/api/perk/IPerk|getRegistryName|()Lnet/minecraft/resources/ResourceLocation;|true\n185|java/util/Map|get|(Ljava/lang/Object;)Ljava/lang/Object;|true\n192|com/hollingsworth/arsnouveau/common/items/PerkItem\n58|5\n25|5\n198|34\n25|2\n25|5\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n167|15\n25|2\n176");
bonsVerify(c,"getPerksFromItem","(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;","187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|1\n25|0\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|getPerkHolder|(Lnet/minecraft/world/item/ItemStack;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;|false\n58|2\n25|2\n199|11\n25|1\n176\n25|1\n25|2\n185|com/hollingsworth/arsnouveau/api/perk/IPerkHolder|getPerkInstances|()Ljava/util/List;|true\n185|java/util/List|addAll|(Ljava/util/Collection;)Z|true\n87\n25|1\n176");
bonsVerify(c,"getPerksFromPlayer","(Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;","25|0\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|getPerksFromLiving|(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;|false\n176");
bonsVerify(c,"getPerksFromLiving","(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;","187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|1\n25|0\n182|net/minecraft/world/entity/LivingEntity|m_6168_|()Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|2\n25|2\n185|java/util/Iterator|hasNext|()Z|true\n153|21\n25|2\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/world/item/ItemStack\n58|3\n25|1\n25|3\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|getPerksFromItem|(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;|false\n185|java/util/List|addAll|(Ljava/util/Collection;)Z|true\n87\n167|8\n25|1\n176");
bonsVerify(c,"countForPerk","(Lcom/hollingsworth/arsnouveau/api/perk/IPerk;Lnet/minecraft/world/entity/LivingEntity;)I","3\n54|2\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_6168_|()Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|3\n25|3\n185|java/util/Iterator|hasNext|()Z|true\n153|42\n25|3\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/world/item/ItemStack\n58|4\n25|4\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|getPerkHolder|(Lnet/minecraft/world/item/ItemStack;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;|false\n58|5\n25|5\n199|19\n167|6\n25|5\n185|com/hollingsworth/arsnouveau/api/perk/IPerkHolder|getPerkInstances|()Ljava/util/List;|true\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|6\n25|6\n185|java/util/Iterator|hasNext|()Z|true\n153|41\n25|6\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|com/hollingsworth/arsnouveau/api/perk/PerkInstance\n58|7\n25|7\n182|com/hollingsworth/arsnouveau/api/perk/PerkInstance|getPerk|()Lcom/hollingsworth/arsnouveau/api/perk/IPerk;|false\n25|0\n166|40\n21|2\n25|7\n182|com/hollingsworth/arsnouveau/api/perk/PerkInstance|getSlot|()Lcom/hollingsworth/arsnouveau/api/perk/PerkSlot;|false\n180|com/hollingsworth/arsnouveau/api/perk/PerkSlot|value|I\n184|java/lang/Math|max|(II)I|false\n54|2\n167|23\n167|6\n21|2\n172");
bonsVerify(c,"countForPerk","(Lcom/hollingsworth/arsnouveau/api/perk/IPerk;Lnet/minecraft/world/entity/player/Player;)I","25|0\n25|1\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|countForPerk|(Lcom/hollingsworth/arsnouveau/api/perk/IPerk;Lnet/minecraft/world/entity/LivingEntity;)I|false\n172");
bonsVerify(c,"getHolderForPerk","(Lcom/hollingsworth/arsnouveau/api/perk/IPerk;Lnet/minecraft/world/entity/LivingEntity;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;","1\n58|2\n3\n54|3\n25|1\n182|net/minecraft/world/entity/LivingEntity|m_6168_|()Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|4\n25|4\n185|java/util/Iterator|hasNext|()Z|true\n153|46\n25|4\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/world/item/ItemStack\n58|5\n25|5\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|getPerkHolder|(Lnet/minecraft/world/item/ItemStack;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;|false\n58|6\n25|6\n199|21\n167|8\n25|6\n185|com/hollingsworth/arsnouveau/api/perk/IPerkHolder|getPerkInstances|()Ljava/util/List;|true\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|7\n25|7\n185|java/util/Iterator|hasNext|()Z|true\n153|45\n25|7\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|com/hollingsworth/arsnouveau/api/perk/PerkInstance\n58|8\n25|8\n182|com/hollingsworth/arsnouveau/api/perk/PerkInstance|getPerk|()Lcom/hollingsworth/arsnouveau/api/perk/IPerk;|false\n25|0\n166|44\n21|3\n25|8\n182|com/hollingsworth/arsnouveau/api/perk/PerkInstance|getSlot|()Lcom/hollingsworth/arsnouveau/api/perk/PerkSlot;|false\n180|com/hollingsworth/arsnouveau/api/perk/PerkSlot|value|I\n184|java/lang/Math|max|(II)I|false\n54|3\n25|6\n58|2\n167|25\n167|8\n25|2\n176");
bonsVerify(c,"getHolderForPerk","(Lcom/hollingsworth/arsnouveau/api/perk/IPerk;Lnet/minecraft/world/entity/player/Player;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;","25|0\n25|1\n184|com/hollingsworth/arsnouveau/api/util/PerkUtil|getHolderForPerk|(Lcom/hollingsworth/arsnouveau/api/perk/IPerk;Lnet/minecraft/world/entity/LivingEntity;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;|false\n176");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getPerksFromLiving","(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;",23);
methodVisitor = new MethodNode(9,"getPerksFromLiving","(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;","(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List<Lcom/hollingsworth/arsnouveau/api/perk/PerkInstance;>;",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(99, label0);
methodVisitor.visitTypeInsn(O.NEW, "java/util/ArrayList");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 1);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(100, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity", "m_6168_", "()Ljava/lang/Iterable;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_APPEND,2, ["java/util/ArrayList", "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label3);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/item/ItemStack");
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(101, label4);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/hollingsworth/arsnouveau/api/util/PerkUtil", "getPerkHolder", "(Lnet/minecraft/world/item/ItemStack;)Lcom/hollingsworth/arsnouveau/api/perk/IPerkHolder;", false);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(102, label5);
methodVisitor.visitVarInsn(O.ALOAD, 4);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label6);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/hollingsworth/arsnouveau/api/perk/IPerkHolder", "getPerkInstances", "()Ljava/util/List;", true);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/ArrayList", "addAll", "(Ljava/util/Collection;)Z", false);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(103, label6);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/world/item/ItemStack", "com/hollingsworth/arsnouveau/api/perk/IPerkHolder"], 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label2);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(104, label3);
methodVisitor.visitFrame(O.F_CHOP,2, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(2, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
