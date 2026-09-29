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
var BONS_KEY = "netherdepths_reuse_enchantment_decode"; var BONS_SCRIPT = "furious14-strider.js"; var BONS_TARGET = "com.scouter.netherdepthsupgrade.events.ForgeEvents";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"com.scouter.netherdepthsupgrade.events.ForgeEvents"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n177");
bonsVerify(c,"lavaMovementSpeed","(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V","25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n198|11\n25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n182|net/minecraft/world/entity/player/Player|m_7500_|()Z|false\n154|11\n25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n182|net/minecraft/world/entity/player/Player|m_5833_|()Z|false\n153|12\n177\n14\n57|1\n25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n182|net/minecraft/world/entity/player/Player|m_20184_|()Lnet/minecraft/world/phys/Vec3;|false\n180|net/minecraft/world/phys/Vec3|f_82480_|D\n14\n152\n157|23\n4\n167|24\n3\n54|3\n21|3\n153|34\n25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n178|net/minecraft/world/effect/MobEffects|f_19591_|Lnet/minecraft/world/effect/MobEffect;\n182|net/minecraft/world/entity/player/Player|m_21023_|(Lnet/minecraft/world/effect/MobEffect;)Z|false\n153|34\n18|0.01\n57|1\n25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n178|net/minecraft/world/entity/EquipmentSlot|FEET|Lnet/minecraft/world/entity/EquipmentSlot;\n182|net/minecraft/world/entity/player/Player|m_6844_|(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;|false\n184|net/minecraft/world/item/enchantment/EnchantmentHelper|m_44831_|(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Map;|false\n178|com/scouter/netherdepthsupgrade/enchantments/NDUEnchantments|HELL_STRIDER|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n185|java/util/Map|containsKey|(Ljava/lang/Object;)Z|true\n153|157\n25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n178|net/minecraft/world/entity/EquipmentSlot|FEET|Lnet/minecraft/world/entity/EquipmentSlot;\n182|net/minecraft/world/entity/player/Player|m_6844_|(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;|false\n184|net/minecraft/world/item/enchantment/EnchantmentHelper|m_44831_|(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Map;|false\n178|com/scouter/netherdepthsupgrade/enchantments/NDUEnchantments|HELL_STRIDER|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n185|java/util/Map|get|(Ljava/lang/Object;)Ljava/lang/Object;|true\n192|java/lang/Integer\n182|java/lang/Integer|intValue|()I|false\n135\n57|4\n25|0\n180|net/minecraftforge/event/TickEvent$PlayerTickEvent|player|Lnet/minecraft/world/entity/player/Player;\n58|6\n187|net/minecraft/core/BlockPos\n89\n25|6\n182|net/minecraft/world/entity/player/Player|m_146892_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7096_|()D|false\n142\n25|6\n182|net/minecraft/world/entity/player/Player|m_146892_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7098_|()D|false\n142\n25|6\n182|net/minecraft/world/entity/player/Player|m_146892_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7094_|()D|false\n142\n183|net/minecraft/core/BlockPos|<init>|(III)V|false\n58|7\n25|6\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|7\n182|net/minecraft/world/level/Level|m_6425_|(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;|false\n58|8\n25|6\n182|net/minecraft/world/entity/player/Player|m_20077_|()Z|false\n153|157\n25|6\n182|net/minecraft/world/entity/player/Player|m_6129_|()Z|false\n153|157\n25|8\n178|net/minecraft/tags/FluidTags|f_13132_|Lnet/minecraft/tags/TagKey;\n182|net/minecraft/world/level/material/FluidState|m_205070_|(Lnet/minecraft/tags/TagKey;)Z|false\n153|157\n25|6\n182|net/minecraft/world/entity/player/Player|m_20186_|()D|false\n57|9\n18|1.15\n18|0.1\n24|4\n107\n99\n144\n56|11\n25|6\n25|6\n182|net/minecraft/world/entity/player/Player|m_20184_|()Lnet/minecraft/world/phys/Vec3;|false\n23|11\n141\n18|0.800000011920929\n23|11\n141\n182|net/minecraft/world/phys/Vec3|m_82542_|(DDD)Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/entity/player/Player|m_20256_|(Lnet/minecraft/world/phys/Vec3;)V|false\n25|6\n24|1\n21|3\n25|6\n182|net/minecraft/world/entity/player/Player|m_20184_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/entity/player/Player|m_20994_|(DZLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;|false\n58|12\n25|6\n25|12\n182|net/minecraft/world/entity/player/Player|m_20256_|(Lnet/minecraft/world/phys/Vec3;)V|false\n25|6\n182|net/minecraft/world/entity/player/Player|m_6144_|()Z|false\n153|131\n25|6\n25|12\n180|net/minecraft/world/phys/Vec3|f_82479_|D\n18|-0.0750000011920929\n24|4\n107\n25|12\n180|net/minecraft/world/phys/Vec3|f_82481_|D\n182|net/minecraft/world/entity/player/Player|m_20334_|(DDD)V|false\n25|6\n180|net/minecraft/world/entity/player/Player|f_19862_|Z\n153|157\n25|6\n25|12\n180|net/minecraft/world/phys/Vec3|f_82479_|D\n25|12\n180|net/minecraft/world/phys/Vec3|f_82480_|D\n18|0.6000000238418579\n99\n25|6\n182|net/minecraft/world/entity/player/Player|m_20186_|()D|false\n103\n24|9\n99\n25|12\n180|net/minecraft/world/phys/Vec3|f_82481_|D\n182|net/minecraft/world/entity/player/Player|m_20229_|(DDD)Z|false\n153|157\n25|6\n25|12\n180|net/minecraft/world/phys/Vec3|f_82479_|D\n18|0.30000001192092896\n25|12\n180|net/minecraft/world/phys/Vec3|f_82481_|D\n182|net/minecraft/world/entity/player/Player|m_20334_|(DDD)V|false\n177");
bonsVerify(c,"changeFish","(Lnet/minecraftforge/event/entity/player/ItemFishedEvent;)V","25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n58|1\n25|1\n178|net/minecraft/world/InteractionHand|MAIN_HAND|Lnet/minecraft/world/InteractionHand;\n182|net/minecraft/world/entity/player/Player|m_21120_|(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;|false\n58|2\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getHookEntity|()Lnet/minecraft/world/entity/projectile/FishingHook;|false\n58|3\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getDrops|()Lnet/minecraft/core/NonNullList;|false\n58|4\n25|3\n193|com/scouter/netherdepthsupgrade/entity/entities/LavaFishingBobberEntity\n153|401\n178|com/scouter/netherdepthsupgrade/config/NetherDepthsUpgradeConfig|FISH_ENTITIES|Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;\n182|net/minecraftforge/common/ForgeConfigSpec$ConfigValue|get|()Ljava/lang/Object;|false\n192|java/lang/Boolean\n182|java/lang/Boolean|booleanValue|()Z|false\n153|401\n25|4\n185|java/util/List|iterator|()Ljava/util/Iterator;|true\n58|5\n25|5\n185|java/util/Iterator|hasNext|()Z|true\n153|394\n25|5\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/world/item/ItemStack\n58|6\n1\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|SEARING_COD|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|46\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|SEARING_COD|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|SOULSUCKER|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|59\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|SOULSUCKER|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|LAVA_PUFFERFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|72\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|LAVA_PUFFERFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|BONEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|85\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|BONEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|WITHER_BONEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|98\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|WITHER_BONEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|GLOWDINE|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|111\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|GLOWDINE|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|MAGMACUBEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|124\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|MAGMACUBEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|OBSIDIANFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|137\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|OBSIDIAN_FISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|BLAZEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|150\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|BLAZEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|EYEBALL_FISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|163\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|EYEBALL_FISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|6\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|FORTRESS_GROUPER|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n166|176\n178|com/scouter/netherdepthsupgrade/entity/NDUEntity|FORTRESS_GROUPER|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/entity/EntityType\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n182|net/minecraft/world/entity/EntityType|m_20615_|(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;|false\n58|7\n25|7\n199|304\n187|net/minecraft/world/entity/item/ItemEntity\n89\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20185_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20186_|()D|false\n15\n99\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20189_|()D|false\n25|6\n183|net/minecraft/world/entity/item/ItemEntity|<init>|(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V|false\n58|8\n25|1\n182|net/minecraft/world/entity/player/Player|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7096_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7096_|()D|false\n103\n57|9\n25|1\n182|net/minecraft/world/entity/player/Player|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7098_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7098_|()D|false\n15\n99\n103\n57|11\n25|1\n182|net/minecraft/world/entity/player/Player|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7094_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7094_|()D|false\n103\n57|13\n18|0.1\n57|15\n25|8\n24|9\n18|0.1\n107\n24|11\n18|0.1\n107\n24|9\n24|9\n107\n24|11\n24|11\n107\n99\n24|13\n24|13\n107\n99\n184|java/lang/Math|sqrt|(D)D|false\n184|java/lang/Math|sqrt|(D)D|false\n18|0.08\n107\n99\n24|13\n18|0.1\n107\n182|net/minecraft/world/entity/item/ItemEntity|m_20334_|(DDD)V|false\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|8\n182|net/minecraft/world/level/Level|m_7967_|(Lnet/minecraft/world/entity/Entity;)Z|false\n87\n25|1\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n187|net/minecraft/world/entity/ExperienceOrb\n89\n25|1\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|1\n182|net/minecraft/world/entity/player/Player|m_20185_|()D|false\n25|1\n182|net/minecraft/world/entity/player/Player|m_20186_|()D|false\n18|0.5\n99\n25|1\n182|net/minecraft/world/entity/player/Player|m_20189_|()D|false\n18|0.5\n99\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_9236_|()Lnet/minecraft/world/level/Level;|false\n180|net/minecraft/world/level/Level|f_46441_|Lnet/minecraft/util/RandomSource;\n16|6\n185|net/minecraft/util/RandomSource|m_188503_|(I)I|true\n4\n96\n183|net/minecraft/world/entity/ExperienceOrb|<init>|(Lnet/minecraft/world/level/Level;DDDI)V|false\n182|net/minecraft/world/level/Level|m_7967_|(Lnet/minecraft/world/entity/Entity;)Z|false\n87\n178|net/minecraft/advancements/CriteriaTriggers|f_10553_|Lnet/minecraft/advancements/critereon/FishingRodHookedTrigger;\n25|1\n192|net/minecraft/server/level/ServerPlayer\n25|2\n25|3\n25|4\n182|net/minecraft/advancements/critereon/FishingRodHookedTrigger|m_40416_|(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V|false\n25|2\n178|net/minecraft/tags/ItemTags|f_13156_|Lnet/minecraft/tags/TagKey;\n182|net/minecraft/world/item/ItemStack|m_204117_|(Lnet/minecraft/tags/TagKey;)Z|false\n153|296\n25|1\n178|net/minecraft/stats/Stats|f_12939_|Lnet/minecraft/resources/ResourceLocation;\n4\n182|net/minecraft/world/entity/player/Player|m_36222_|(Lnet/minecraft/resources/ResourceLocation;I)V|false\n25|0\n4\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|setCanceled|(Z)V|false\n25|0\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getRodDamage|()I|false\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|damageRodBy|(I)V|false\n177\n25|7\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7096_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7098_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7094_|()D|false\n25|3\n180|net/minecraft/world/entity/projectile/FishingHook|f_19860_|F\n25|3\n180|net/minecraft/world/entity/projectile/FishingHook|f_19859_|F\n182|net/minecraft/world/entity/Entity|m_7678_|(DDDFF)V|false\n25|1\n182|net/minecraft/world/entity/player/Player|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7096_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7096_|()D|false\n103\n57|8\n25|1\n182|net/minecraft/world/entity/player/Player|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7098_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7098_|()D|false\n103\n57|10\n25|1\n182|net/minecraft/world/entity/player/Player|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7094_|()D|false\n25|3\n182|net/minecraft/world/entity/projectile/FishingHook|m_20182_|()Lnet/minecraft/world/phys/Vec3;|false\n182|net/minecraft/world/phys/Vec3|m_7094_|()D|false\n103\n57|12\n18|0.12\n57|14\n25|7\n24|8\n24|14\n107\n24|10\n24|14\n107\n24|8\n24|8\n107\n24|10\n24|10\n107\n99\n24|12\n24|12\n107\n99\n184|java/lang/Math|sqrt|(D)D|false\n184|java/lang/Math|sqrt|(D)D|false\n18|0.14\n107\n99\n24|12\n24|14\n107\n182|net/minecraft/world/entity/Entity|m_20334_|(DDD)V|false\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n182|net/minecraft/world/entity/player/Player|m_9236_|()Lnet/minecraft/world/level/Level;|false\n25|7\n182|net/minecraft/world/level/Level|m_7967_|(Lnet/minecraft/world/entity/Entity;)Z|false\n87\n178|net/minecraft/advancements/CriteriaTriggers|f_10553_|Lnet/minecraft/advancements/critereon/FishingRodHookedTrigger;\n25|1\n192|net/minecraft/server/level/ServerPlayer\n25|2\n25|3\n25|4\n182|net/minecraft/advancements/critereon/FishingRodHookedTrigger|m_40416_|(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V|false\n25|2\n178|net/minecraft/tags/ItemTags|f_13156_|Lnet/minecraft/tags/TagKey;\n182|net/minecraft/world/item/ItemStack|m_204117_|(Lnet/minecraft/tags/TagKey;)Z|false\n153|393\n25|1\n178|net/minecraft/stats/Stats|f_12939_|Lnet/minecraft/resources/ResourceLocation;\n4\n182|net/minecraft/world/entity/player/Player|m_36222_|(Lnet/minecraft/resources/ResourceLocation;I)V|false\n167|24\n25|0\n4\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|setCanceled|(Z)V|false\n25|0\n25|0\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|getRodDamage|()I|false\n182|net/minecraftforge/event/entity/player/ItemFishedEvent|damageRodBy|(I)V|false\n177");
bonsVerify(c,"frogFeed","(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V","25|0\n182|net/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract|getLevel|()Lnet/minecraft/world/level/Level;|false\n180|net/minecraft/world/level/Level|f_46443_|Z\n154|12\n25|0\n182|net/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract|getTarget|()Lnet/minecraft/world/entity/Entity;|false\n193|net/minecraft/world/entity/animal/frog/Frog\n153|12\n25|0\n182|net/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n193|net/minecraft/world/entity/player/Player\n154|13\n177\n25|0\n182|net/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract|getTarget|()Lnet/minecraft/world/entity/Entity;|false\n192|net/minecraft/world/entity/animal/frog/Frog\n58|1\n25|0\n182|net/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract|getEntity|()Lnet/minecraft/world/entity/player/Player;|false\n58|2\n25|0\n182|net/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract|getLevel|()Lnet/minecraft/world/level/Level;|false\n58|3\n187|net/minecraft/world/item/ItemStack\n89\n178|net/minecraft/world/item/Items|f_220220_|Lnet/minecraft/world/item/Item;\n183|net/minecraft/world/item/ItemStack|<init>|(Lnet/minecraft/world/level/ItemLike;)V|false\n58|4\n187|net/minecraft/world/item/ItemStack\n89\n178|net/minecraft/world/item/Items|f_220222_|Lnet/minecraft/world/item/Item;\n183|net/minecraft/world/item/ItemStack|<init>|(Lnet/minecraft/world/level/ItemLike;)V|false\n58|5\n187|net/minecraft/world/item/ItemStack\n89\n178|net/minecraft/world/item/Items|f_220221_|Lnet/minecraft/world/item/Item;\n183|net/minecraft/world/item/ItemStack|<init>|(Lnet/minecraft/world/level/ItemLike;)V|false\n58|6\n187|net/minecraft/world/entity/item/ItemEntity\n89\n25|3\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20185_|()D|false\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20186_|()D|false\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20189_|()D|false\n25|4\n183|net/minecraft/world/entity/item/ItemEntity|<init>|(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V|false\n58|7\n187|net/minecraft/world/entity/item/ItemEntity\n89\n25|3\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20185_|()D|false\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20186_|()D|false\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20189_|()D|false\n25|5\n183|net/minecraft/world/entity/item/ItemEntity|<init>|(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V|false\n58|8\n187|net/minecraft/world/entity/item/ItemEntity\n89\n25|3\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20185_|()D|false\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20186_|()D|false\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20189_|()D|false\n25|6\n183|net/minecraft/world/entity/item/ItemEntity|<init>|(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V|false\n58|9\n25|2\n178|net/minecraft/world/InteractionHand|MAIN_HAND|Lnet/minecraft/world/InteractionHand;\n182|net/minecraft/world/entity/player/Player|m_21120_|(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;|false\n58|10\n25|10\n182|net/minecraft/world/item/ItemStack|m_41720_|()Lnet/minecraft/world/item/Item;|false\n178|com/scouter/netherdepthsupgrade/items/NDUItems|MAGMACUBEFISH|Lnet/minecraftforge/registries/RegistryObject;\n182|net/minecraftforge/registries/RegistryObject|get|()Ljava/lang/Object;|false\n192|net/minecraft/world/item/Item\n182|net/minecraft/world/item/Item|m_5456_|()Lnet/minecraft/world/item/Item;|false\n166|127\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_28554_|()Lnet/minecraft/world/entity/animal/FrogVariant;|false\n178|net/minecraft/world/entity/animal/FrogVariant|f_218187_|Lnet/minecraft/world/entity/animal/FrogVariant;\n166|93\n25|3\n25|9\n182|net/minecraft/world/level/Level|m_7967_|(Lnet/minecraft/world/entity/Entity;)Z|false\n87\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_28554_|()Lnet/minecraft/world/entity/animal/FrogVariant;|false\n178|net/minecraft/world/entity/animal/FrogVariant|f_218185_|Lnet/minecraft/world/entity/animal/FrogVariant;\n166|101\n25|3\n25|7\n182|net/minecraft/world/level/Level|m_7967_|(Lnet/minecraft/world/entity/Entity;)Z|false\n87\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_28554_|()Lnet/minecraft/world/entity/animal/FrogVariant;|false\n178|net/minecraft/world/entity/animal/FrogVariant|f_218186_|Lnet/minecraft/world/entity/animal/FrogVariant;\n166|109\n25|3\n25|8\n182|net/minecraft/world/level/Level|m_7967_|(Lnet/minecraft/world/entity/Entity;)Z|false\n87\n25|2\n182|net/minecraft/world/entity/player/Player|m_7500_|()Z|false\n154|127\n25|3\n1\n25|1\n182|net/minecraft/world/entity/animal/frog/Frog|m_20183_|()Lnet/minecraft/core/BlockPos;|false\n178|net/minecraft/sounds/SoundEvents|f_215692_|Lnet/minecraft/sounds/SoundEvent;\n178|net/minecraft/sounds/SoundSource|NEUTRAL|Lnet/minecraft/sounds/SoundSource;\n12\n12\n182|net/minecraft/world/level/Level|m_5594_|(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V|false\n25|10\n25|10\n182|net/minecraft/world/item/ItemStack|m_41613_|()I|false\n4\n100\n182|net/minecraft/world/item/ItemStack|m_41764_|(I)V|false\n177");
bonsVerify(c,"<clinit>","()V","184|com/mojang/logging/LogUtils|getLogger|()Lorg/slf4j/Logger;|false\n179|com/scouter/netherdepthsupgrade/events/ForgeEvents|LOGGER|Lorg/slf4j/Logger;\n177");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"lavaMovementSpeed","(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",158);
methodVisitor = new MethodNode(9,"lavaMovementSpeed","(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V",null,[]);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lnet/minecraftforge/eventbus/api/SubscribeEvent;", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(49, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_7500_", "()Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_5833_", "()Z", false);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label2);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(50, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(52, label2);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.DCONST_0);
methodVisitor.visitVarInsn(O.DSTORE, 1);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(53, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20184_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82480_", "D");
methodVisitor.visitInsn(O.DCONST_0);
methodVisitor.visitInsn(O.DCMPG);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFGT, label4);
methodVisitor.visitInsn(O.ICONST_1);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label5);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_APPEND,1, [O.DOUBLE], 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(54, label6);
methodVisitor.visitVarInsn(O.ILOAD, 3);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label7);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/world/effect/MobEffects", "f_19591_", "Lnet/minecraft/world/effect/MobEffect;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_21023_", "(Lnet/minecraft/world/effect/MobEffect;)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label7);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(55, label8);
methodVisitor.visitLdcInsn(number('0.01', NT.DOUBLE));
methodVisitor.visitVarInsn(O.DSTORE, 1);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(58, label7);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET", "Lnet/minecraft/world/entity/EquipmentSlot;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_6844_", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/item/enchantment/EnchantmentHelper", "m_44831_", "(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Map;", false);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ASTORE, 13);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/scouter/netherdepthsupgrade/enchantments/NDUEnchantments", "HELL_STRIDER", "Lnet/minecraftforge/registries/RegistryObject;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/registries/RegistryObject", "get", "()Ljava/lang/Object;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Map", "containsKey", "(Ljava/lang/Object;)Z", true);
var label9 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label9);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(59, label10);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/scouter/netherdepthsupgrade/events/ForgeEvents", "ac$ordinaryPlayer", "(Lnet/minecraft/world/entity/player/Player;)Z", false);
var label11 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label11);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET", "Lnet/minecraft/world/entity/EquipmentSlot;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_6844_", "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/item/enchantment/EnchantmentHelper", "m_44831_", "(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Map;", false);
var label12 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label12);
methodVisitor.visitLabel(label11);
methodVisitor.visitFrame(O.F_FULL, 13, ["net/minecraftforge/event/TickEvent$PlayerTickEvent", O.DOUBLE, O.INTEGER, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, "java/util/Map"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 13);
methodVisitor.visitLabel(label12);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["java/util/Map"]);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/scouter/netherdepthsupgrade/enchantments/NDUEnchantments", "HELL_STRIDER", "Lnet/minecraftforge/registries/RegistryObject;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/registries/RegistryObject", "get", "()Ljava/lang/Object;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Integer");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
methodVisitor.visitInsn(O.I2D);
methodVisitor.visitVarInsn(O.DSTORE, 4);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(60, label13);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$PlayerTickEvent", "player", "Lnet/minecraft/world/entity/player/Player;");
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(61, label14);
methodVisitor.visitTypeInsn(O.NEW, "net/minecraft/core/BlockPos");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_146892_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/phys/Vec3", "m_7096_", "()D", false);
methodVisitor.visitInsn(O.D2I);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_146892_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/phys/Vec3", "m_7098_", "()D", false);
methodVisitor.visitInsn(O.D2I);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_146892_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/phys/Vec3", "m_7094_", "()D", false);
methodVisitor.visitInsn(O.D2I);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/core/BlockPos", "<init>", "(III)V", false);
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(62, label15);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_6425_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;", false);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(63, label16);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20077_", "()Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label9);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_6129_", "()Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label9);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/tags/FluidTags", "f_13132_", "Lnet/minecraft/tags/TagKey;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/material/FluidState", "m_205070_", "(Lnet/minecraft/tags/TagKey;)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label9);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(65, label17);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20186_", "()D", false);
methodVisitor.visitVarInsn(O.DSTORE, 9);
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(66, label18);
methodVisitor.visitLdcInsn(number('1.15', NT.DOUBLE));
methodVisitor.visitLdcInsn(number('0.1', NT.DOUBLE));
methodVisitor.visitVarInsn(O.DLOAD, 4);
methodVisitor.visitInsn(O.DMUL);
methodVisitor.visitInsn(O.DADD);
methodVisitor.visitInsn(O.D2F);
methodVisitor.visitVarInsn(O.FSTORE, 11);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(67, label19);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20184_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitVarInsn(O.FLOAD, 11);
methodVisitor.visitInsn(O.F2D);
methodVisitor.visitLdcInsn(number('0.800000011920929', NT.DOUBLE));
methodVisitor.visitVarInsn(O.FLOAD, 11);
methodVisitor.visitInsn(O.F2D);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/phys/Vec3", "m_82542_", "(DDD)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20256_", "(Lnet/minecraft/world/phys/Vec3;)V", false);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(68, label20);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.DLOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20184_", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20994_", "(DZLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitVarInsn(O.ASTORE, 12);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(69, label21);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20256_", "(Lnet/minecraft/world/phys/Vec3;)V", false);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(70, label22);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_6144_", "()Z", false);
var label23 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label23);
var label24 = new Label();
methodVisitor.visitLabel(label24);
methodVisitor.visitLineNumber(71, label24);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82479_", "D");
methodVisitor.visitLdcInsn(number('-0.0750000011920929', NT.DOUBLE));
methodVisitor.visitVarInsn(O.DLOAD, 4);
methodVisitor.visitInsn(O.DMUL);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82481_", "D");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20334_", "(DDD)V", false);
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(74, label23);
methodVisitor.visitFrame(O.F_FULL, 11, ["net/minecraftforge/event/TickEvent$PlayerTickEvent", O.DOUBLE, O.INTEGER, O.DOUBLE, "net/minecraft/world/entity/player/Player", "net/minecraft/core/BlockPos", "net/minecraft/world/level/material/FluidState", O.DOUBLE, O.FLOAT, "net/minecraft/world/phys/Vec3", "java/util/Map"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/entity/player/Player", "f_19862_", "Z");
methodVisitor.visitJumpInsn(O.IFEQ, label9);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82479_", "D");
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82480_", "D");
methodVisitor.visitLdcInsn(number('0.6000000238418579', NT.DOUBLE));
methodVisitor.visitInsn(O.DADD);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20186_", "()D", false);
methodVisitor.visitInsn(O.DSUB);
methodVisitor.visitVarInsn(O.DLOAD, 9);
methodVisitor.visitInsn(O.DADD);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82481_", "D");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20229_", "(DDD)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label9);
var label25 = new Label();
methodVisitor.visitLabel(label25);
methodVisitor.visitLineNumber(75, label25);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82479_", "D");
methodVisitor.visitLdcInsn(number('0.30000001192092896', NT.DOUBLE));
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/phys/Vec3", "f_82481_", "D");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/player/Player", "m_20334_", "(DDD)V", false);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(79, label9);
methodVisitor.visitFrame(O.F_FULL, 13, ["net/minecraftforge/event/TickEvent$PlayerTickEvent", O.DOUBLE, O.INTEGER, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, O.TOP, "java/util/Map"], 0, []);
methodVisitor.visitInsn(O.RETURN);
var label26 = new Label();
methodVisitor.visitLabel(label26);
methodVisitor.visitLocalVariable("e", "D", null, label18, label9, 9);
methodVisitor.visitLocalVariable("speed", "F", null, label19, label9, 11);
methodVisitor.visitLocalVariable("vec33", "Lnet/minecraft/world/phys/Vec3;", null, label21, label9, 12);
methodVisitor.visitLocalVariable("level", "D", null, label13, label9, 4);
methodVisitor.visitLocalVariable("player", "Lnet/minecraft/world/entity/player/Player;", null, label14, label9, 6);
methodVisitor.visitLocalVariable("eyePos", "Lnet/minecraft/core/BlockPos;", null, label15, label9, 7);
methodVisitor.visitLocalVariable("state", "Lnet/minecraft/world/level/material/FluidState;", null, label16, label9, 8);
methodVisitor.visitLocalVariable("event", "Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;", null, label0, label26, 0);
methodVisitor.visitLocalVariable("d0", "D", null, label3, label26, 1);
methodVisitor.visitLocalVariable("flag", "Z", null, label6, label26, 3);
methodVisitor.visitMaxs(8, 14);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"ac$ordinaryPlayer","(Lnet/minecraft/world/entity/player/Player;)Z",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(5, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label1);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(6, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
methodVisitor.visitVarInsn(O.ASTORE, 1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(7, label2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("net.minecraft.server.level.ServerPlayer");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("net.minecraft.client.player.LocalPlayer");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("net.minecraft.client.player.RemotePlayer");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label4);
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/lang/String"], 0, null);
methodVisitor.visitInsn(O.ICONST_1);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label5);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(2, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
