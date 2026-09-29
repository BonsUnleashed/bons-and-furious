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
var BONS_KEY = "ars_primitive_mana_discounts"; var BONS_SCRIPT = "furious10-mana.js"; var BONS_TARGET = "com.hollingsworth.arsnouveau.api.util.ManaUtil";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.hollingsworth.arsnouveau.api.util.ManaUtil"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n177");
bonsVerify(c,"getPlayerDiscounts","(Lnet/minecraft/world/entity/LivingEntity;Lcom/hollingsworth/arsnouveau/api/spell/Spell;Lnet/minecraft/world/item/ItemStack;)I","25|0\n199|4\n3\n172\n187|java/util/concurrent/atomic/AtomicInteger\n89\n183|java/util/concurrent/atomic/AtomicInteger|<init>|()V|false\n58|3\n25|0\n184|com/hollingsworth/arsnouveau/api/util/CuriosUtil|getAllWornItems|(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;|false\n25|3\n25|1\n186|accept|(Ljava/util/concurrent/atomic/AtomicInteger;Lcom/hollingsworth/arsnouveau/api/spell/Spell;)Lnet/minecraftforge/common/util/NonNullConsumer;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)V|com/hollingsworth/arsnouveau/api/util/ManaUtil.lambda$getPlayerDiscounts$0(Ljava/util/concurrent/atomic/AtomicInteger;Lcom/hollingsworth/arsnouveau/api/spell/Spell;Lnet/minecraftforge/items/IItemHandlerModifiable;)V (6)|(Lnet/minecraftforge/items/IItemHandlerModifiable;)V\n182|net/minecraftforge/common/util/LazyOptional|ifPresent|(Lnet/minecraftforge/common/util/NonNullConsumer;)V|false\n25|0\n182|net/minecraft/world/entity/LivingEntity|m_6168_|()Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|4\n25|4\n185|java/util/Iterator|hasNext|()Z|true\n153|42\n25|4\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/world/item/ItemStack\n58|5\n25|5\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n58|7\n25|7\n193|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment\n153|41\n25|7\n192|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment\n58|6\n25|3\n25|6\n25|5\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment|getManaDiscount|(Lnet/minecraft/world/item/ItemStack;Lcom/hollingsworth/arsnouveau/api/spell/Spell;)I|true\n182|java/util/concurrent/atomic/AtomicInteger|addAndGet|(I)I|false\n87\n167|18\n25|2\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n58|5\n25|5\n193|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment\n153|58\n25|5\n192|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment\n58|4\n25|3\n25|4\n25|2\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment|getManaDiscount|(Lnet/minecraft/world/item/ItemStack;Lcom/hollingsworth/arsnouveau/api/spell/Spell;)I|true\n182|java/util/concurrent/atomic/AtomicInteger|addAndGet|(I)I|false\n87\n25|3\n182|java/util/concurrent/atomic/AtomicInteger|get|()I|false\n172");
bonsVerify(c,"getCurrentMana","(Lnet/minecraft/world/entity/LivingEntity;)D","25|0\n184|com/hollingsworth/arsnouveau/setup/registry/CapabilityRegistry|getMana|(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;|false\n1\n182|net/minecraftforge/common/util/LazyOptional|orElse|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|com/hollingsworth/arsnouveau/api/mana/IManaCap\n58|1\n25|1\n199|10\n14\n175\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaCap|getCurrentMana|()D|true\n175");
bonsVerify(c,"calcMaxMana","(Lnet/minecraft/world/entity/player/Player;)Lcom/hollingsworth/arsnouveau/api/util/ManaUtil$Mana;","25|0\n184|com/hollingsworth/arsnouveau/setup/registry/CapabilityRegistry|getMana|(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;|false\n1\n182|net/minecraftforge/common/util/LazyOptional|orElse|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|com/hollingsworth/arsnouveau/api/mana/IManaCap\n58|1\n25|1\n199|14\n187|com/hollingsworth/arsnouveau/api/util/ManaUtil$Mana\n89\n3\n11\n183|com/hollingsworth/arsnouveau/api/util/ManaUtil$Mana|<init>|(IF)V|false\n176\n14\n57|2\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaCap|getBookTier|()I|true\n54|4\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaCap|getGlyphBonus|()I|true\n54|5\n24|2\n178|com/hollingsworth/arsnouveau/setup/config/ServerConfig|INIT_MAX_MANA|Lnet/minecraftforge/common/ForgeConfigSpec$IntValue;\n182|net/minecraftforge/common/ForgeConfigSpec$IntValue|get|()Ljava/lang/Object;|false\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n135\n99\n57|2\n24|2\n21|5\n178|com/hollingsworth/arsnouveau/setup/config/ServerConfig|GLYPH_MAX_BONUS|Lnet/minecraftforge/common/ForgeConfigSpec$IntValue;\n182|net/minecraftforge/common/ForgeConfigSpec$IntValue|get|()Ljava/lang/Object;|false\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n104\n135\n99\n57|2\n24|2\n21|4\n178|com/hollingsworth/arsnouveau/setup/config/ServerConfig|TIER_MAX_BONUS|Lnet/minecraftforge/common/ForgeConfigSpec$IntValue;\n182|net/minecraftforge/common/ForgeConfigSpec$IntValue|get|()Ljava/lang/Object;|false\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n104\n135\n99\n57|2\n25|0\n178|com/hollingsworth/arsnouveau/api/perk/PerkAttributes|MAX_MANA|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/ai/attributes/Attribute\n182|net/minecraft/world/entity/player/Player|m_21051_|(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;|false\n58|6\n25|6\n198|86\n25|6\n178|com/hollingsworth/arsnouveau/api/util/ManaUtil|MAX_MANA_MODIFIER|Ljava/util/UUID;\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22111_|(Ljava/util/UUID;)Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;|false\n58|7\n25|7\n198|69\n25|7\n182|net/minecraft/world/entity/ai/attributes/AttributeModifier|m_22218_|()D|false\n24|2\n151\n153|83\n25|7\n198|74\n25|6\n25|7\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22130_|(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V|false\n25|6\n187|net/minecraft/world/entity/ai/attributes/AttributeModifier\n89\n178|com/hollingsworth/arsnouveau/api/util/ManaUtil|MAX_MANA_MODIFIER|Ljava/util/UUID;\n18|Mana Cache\n24|2\n178|net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation|ADDITION|Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;\n183|net/minecraft/world/entity/ai/attributes/AttributeModifier|<init>|(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V|false\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22118_|(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V|false\n25|6\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22135_|()D|false\n57|2\n24|2\n142\n54|7\n187|com/hollingsworth/arsnouveau/api/event/MaxManaCalcEvent\n89\n25|0\n21|7\n183|com/hollingsworth/arsnouveau/api/event/MaxManaCalcEvent|<init>|(Lnet/minecraft/world/entity/LivingEntity;I)V|false\n58|8\n178|net/minecraftforge/common/MinecraftForge|EVENT_BUS|Lnet/minecraftforge/eventbus/api/IEventBus;\n25|8\n185|net/minecraftforge/eventbus/api/IEventBus|post|(Lnet/minecraftforge/eventbus/api/Event;)Z|true\n87\n25|8\n182|com/hollingsworth/arsnouveau/api/event/MaxManaCalcEvent|getMax|()I|false\n54|7\n25|8\n182|com/hollingsworth/arsnouveau/api/event/MaxManaCalcEvent|getReserve|()F|false\n56|9\n187|com/hollingsworth/arsnouveau/api/util/ManaUtil$Mana\n89\n21|7\n23|9\n183|com/hollingsworth/arsnouveau/api/util/ManaUtil$Mana|<init>|(IF)V|false\n176");
bonsVerify(c,"getMaxMana","(Lnet/minecraft/world/entity/player/Player;)I","25|0\n184|com/hollingsworth/arsnouveau/api/util/ManaUtil|calcMaxMana|(Lnet/minecraft/world/entity/player/Player;)Lcom/hollingsworth/arsnouveau/api/util/ManaUtil$Mana;|false\n182|com/hollingsworth/arsnouveau/api/util/ManaUtil$Mana|getRealMax|()I|false\n172");
bonsVerify(c,"getManaRegen","(Lnet/minecraft/world/entity/player/Player;)D","25|0\n184|com/hollingsworth/arsnouveau/setup/registry/CapabilityRegistry|getMana|(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;|false\n1\n182|net/minecraftforge/common/util/LazyOptional|orElse|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|com/hollingsworth/arsnouveau/api/mana/IManaCap\n58|1\n25|1\n199|10\n14\n175\n14\n57|2\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaCap|getBookTier|()I|true\n54|4\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaCap|getGlyphBonus|()I|true\n135\n57|5\n24|2\n24|5\n178|com/hollingsworth/arsnouveau/setup/config/ServerConfig|GLYPH_REGEN_BONUS|Lnet/minecraftforge/common/ForgeConfigSpec$DoubleValue;\n182|net/minecraftforge/common/ForgeConfigSpec$DoubleValue|get|()Ljava/lang/Object;|false\n192|java/lang/Double\n182|java/lang/Double|doubleValue|()D|false\n107\n99\n57|2\n24|2\n21|4\n178|com/hollingsworth/arsnouveau/setup/config/ServerConfig|TIER_REGEN_BONUS|Lnet/minecraftforge/common/ForgeConfigSpec$IntValue;\n182|net/minecraftforge/common/ForgeConfigSpec$IntValue|get|()Ljava/lang/Object;|false\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n104\n135\n99\n57|2\n24|2\n178|com/hollingsworth/arsnouveau/setup/config/ServerConfig|INIT_MANA_REGEN|Lnet/minecraftforge/common/ForgeConfigSpec$IntValue;\n182|net/minecraftforge/common/ForgeConfigSpec$IntValue|get|()Ljava/lang/Object;|false\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n135\n99\n57|2\n25|0\n178|com/hollingsworth/arsnouveau/api/perk/PerkAttributes|MANA_REGEN_BONUS|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/ai/attributes/Attribute\n182|net/minecraft/world/entity/player/Player|m_21051_|(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;|false\n58|7\n25|7\n198|82\n25|7\n178|com/hollingsworth/arsnouveau/api/util/ManaUtil|MANA_REGEN_MODIFIER|Ljava/util/UUID;\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22111_|(Ljava/util/UUID;)Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;|false\n58|8\n25|8\n198|65\n25|8\n182|net/minecraft/world/entity/ai/attributes/AttributeModifier|m_22218_|()D|false\n24|2\n151\n153|79\n25|8\n198|70\n25|7\n25|8\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22130_|(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V|false\n25|7\n187|net/minecraft/world/entity/ai/attributes/AttributeModifier\n89\n178|com/hollingsworth/arsnouveau/api/util/ManaUtil|MANA_REGEN_MODIFIER|Ljava/util/UUID;\n18|Mana Regen Cache\n24|2\n178|net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation|ADDITION|Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;\n183|net/minecraft/world/entity/ai/attributes/AttributeModifier|<init>|(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V|false\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22118_|(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V|false\n25|7\n182|net/minecraft/world/entity/ai/attributes/AttributeInstance|m_22135_|()D|false\n57|2\n187|com/hollingsworth/arsnouveau/api/event/ManaRegenCalcEvent\n89\n25|0\n24|2\n183|com/hollingsworth/arsnouveau/api/event/ManaRegenCalcEvent|<init>|(Lnet/minecraft/world/entity/LivingEntity;D)V|false\n58|8\n178|net/minecraftforge/common/MinecraftForge|EVENT_BUS|Lnet/minecraftforge/eventbus/api/IEventBus;\n25|8\n185|net/minecraftforge/eventbus/api/IEventBus|post|(Lnet/minecraftforge/eventbus/api/Event;)Z|true\n87\n25|8\n182|com/hollingsworth/arsnouveau/api/event/ManaRegenCalcEvent|getRegen|()D|false\n57|2\n24|2\n175");
bonsVerify(c,"lambda$getPlayerDiscounts$0","(Ljava/util/concurrent/atomic/AtomicInteger;Lcom/hollingsworth/arsnouveau/api/spell/Spell;Lnet/minecraftforge/items/IItemHandlerModifiable;)V","3\n54|3\n21|3\n25|2\n185|net/minecraftforge/items/IItemHandlerModifiable|getSlots|()I|true\n162|28\n25|2\n21|3\n185|net/minecraftforge/items/IItemHandlerModifiable|getStackInSlot|(I)Lnet/minecraft/world/item/ItemStack;|true\n58|4\n25|4\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n58|6\n25|6\n193|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment\n153|26\n25|6\n192|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment\n58|5\n25|0\n25|5\n25|4\n25|1\n185|com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment|getManaDiscount|(Lnet/minecraft/world/item/ItemStack;Lcom/hollingsworth/arsnouveau/api/spell/Spell;)I|true\n182|java/util/concurrent/atomic/AtomicInteger|addAndGet|(I)I|false\n87\n132|3|1\n167|2\n177");
bonsVerify(c,"<clinit>","()V","18|6662fdb1-bc67-49bc-9bba-8e306bbc1ae6\n184|java/util/UUID|fromString|(Ljava/lang/String;)Ljava/util/UUID;|false\n179|com/hollingsworth/arsnouveau/api/util/ManaUtil|MAX_MANA_MODIFIER|Ljava/util/UUID;\n18|3bd42486-6a51-44c4-a88f-04021af5df03\n184|java/util/UUID|fromString|(Ljava/lang/String;)Ljava/util/UUID;|false\n179|com/hollingsworth/arsnouveau/api/util/ManaUtil|MANA_REGEN_MODIFIER|Ljava/util/UUID;\n177");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getPlayerDiscounts","(Lnet/minecraft/world/entity/LivingEntity;Lcom/hollingsworth/arsnouveau/api/spell/Spell;Lnet/minecraft/world/item/ItemStack;)I",61);
methodVisitor = new MethodNode(9,"getPlayerDiscounts","(Lnet/minecraft/world/entity/LivingEntity;Lcom/hollingsworth/arsnouveau/api/spell/Spell;Lnet/minecraft/world/item/ItemStack;)I",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(80, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label1);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(81, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(82, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/hollingsworth/arsnouveau/api/util/CuriosUtil", "getAllWornItems", "(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;", false);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/common/util/LazyOptional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraftforge/items/IItemHandlerModifiable");
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(83, label3);
methodVisitor.visitVarInsn(O.ALOAD, 4);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label4);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 5);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_APPEND,3, [O.INTEGER, "net/minecraftforge/items/IItemHandlerModifiable", O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraftforge/items/IItemHandlerModifiable", "getSlots", "()I", true);
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label4);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(84, label6);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraftforge/items/IItemHandlerModifiable", "getStackInSlot", "(I)Lnet/minecraft/world/item/ItemStack;", true);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(85, label7);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "m_41720_", "()Lnet/minecraft/world/item/Item;", false);
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(86, label8);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment");
var label9 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label9);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitTypeInsn(O.CHECKCAST, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment");
methodVisitor.visitVarInsn(O.ASTORE, 8);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment", "getManaDiscount", "(Lnet/minecraft/world/item/ItemStack;Lcom/hollingsworth/arsnouveau/api/spell/Spell;)I", true);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitVarInsn(O.ISTORE, 3);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(83, label9);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/world/item/ItemStack", "net/minecraft/world/item/Item"], 0, null);
methodVisitor.visitIincInsn(5, 1);
methodVisitor.visitJumpInsn(O.GOTO, label5);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(88, label4);
methodVisitor.visitFrame(O.F_CHOP,3, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity", "m_6168_", "()Ljava/lang/Iterable;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label11 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label11);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/item/ItemStack");
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(89, label12);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "m_41720_", "()Lnet/minecraft/world/item/Item;", false);
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(90, label13);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment");
var label14 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label14);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitTypeInsn(O.CHECKCAST, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment");
methodVisitor.visitVarInsn(O.ASTORE, 8);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment", "getManaDiscount", "(Lnet/minecraft/world/item/ItemStack;Lcom/hollingsworth/arsnouveau/api/spell/Spell;)I", true);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitVarInsn(O.ISTORE, 3);
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(91, label14);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/world/item/ItemStack", "net/minecraft/world/item/Item"], 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label10);
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(92, label11);
methodVisitor.visitFrame(O.F_CHOP,2, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "m_41720_", "()Lnet/minecraft/world/item/Item;", false);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(93, label15);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment");
var label16 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label16);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitTypeInsn(O.CHECKCAST, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment");
methodVisitor.visitVarInsn(O.ASTORE, 6);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/hollingsworth/arsnouveau/api/mana/IManaDiscountEquipment", "getManaDiscount", "(Lnet/minecraft/world/item/ItemStack;Lcom/hollingsworth/arsnouveau/api/spell/Spell;)I", true);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitVarInsn(O.ISTORE, 3);
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(94, label16);
methodVisitor.visitFrame(O.F_FULL, 6, ["net/minecraft/world/entity/LivingEntity", "com/hollingsworth/arsnouveau/api/spell/Spell", "net/minecraft/world/item/ItemStack", O.INTEGER, "net/minecraftforge/items/IItemHandlerModifiable", "net/minecraft/world/item/Item"], 0, []);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(4, 9);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
