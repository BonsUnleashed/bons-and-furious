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
var BONS_KEY = "embeddium_pending_upload_sum"; var BONS_SCRIPT = "furious10-arena.js"; var BONS_TARGET = "me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;IILme/jellysquid/mods/sodium/client/gl/arena/staging/StagingBuffer;)V","25|0\n183|java/lang/Object|<init>|()V|false\n25|0\n21|2\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n25|0\n21|2\n5\n108\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|resizeIncrement|I\n25|0\n21|3\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n25|0\n187|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment\n89\n25|0\n3\n21|2\n183|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|<init>|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferArena;II)V|false\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setFree|(Z)V|false\n25|0\n25|1\n185|me/jellysquid/mods/sodium/client/gl/device/CommandList|createMutableBuffer|()Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;|true\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n25|1\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n133\n21|3\n133\n105\n178|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|BUFFER_USAGE|Lme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;\n185|me/jellysquid/mods/sodium/client/gl/device/CommandList|allocateStorage|(Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;JLme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;)V|true\n25|0\n25|4\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stagingBuffer|Lme/jellysquid/mods/sodium/client/gl/arena/staging/StagingBuffer;\n177");
bonsVerify(c,"resize","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;I)V","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n21|2\n164|9\n187|java/lang/UnsupportedOperationException\n89\n18|New capacity must be larger than used size\n183|java/lang/UnsupportedOperationException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|checkAssertions|()V|false\n21|2\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n100\n54|3\n25|0\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|getUsedSegments|()Ljava/util/ArrayList;|false\n58|4\n25|0\n25|4\n21|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|buildTransferList|(Ljava/util/List;I)Ljava/util/List;|false\n58|5\n25|0\n25|1\n25|5\n21|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|transferSegments|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;I)V|false\n25|0\n187|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment\n89\n25|0\n3\n21|3\n183|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|<init>|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferArena;II)V|false\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setFree|(Z)V|false\n25|4\n185|java/util/List|isEmpty|()Z|true\n153|49\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setNext|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n167|62\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n25|4\n3\n185|java/util/List|get|(I)Ljava/lang/Object;|true\n192|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setNext|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setPrev|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|0\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|checkAssertions|()V|false\n177");
bonsVerify(c,"buildTransferList","(Ljava/util/List;I)Ljava/util/List;","187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|3\n1\n58|4\n21|2\n54|5\n3\n54|6\n21|6\n25|1\n185|java/util/List|size|()I|true\n162|96\n25|1\n21|6\n185|java/util/List|get|(I)Ljava/lang/Object;|true\n192|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment\n58|7\n25|4\n198|29\n25|4\n180|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|readOffset|I\n25|4\n180|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|length|I\n96\n25|7\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n159|45\n25|4\n198|35\n25|3\n25|4\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n187|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand\n89\n25|7\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n21|5\n25|7\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n183|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|<init>|(III)V|false\n58|4\n167|52\n25|4\n89\n180|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|length|I\n25|7\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n96\n181|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|length|I\n25|7\n21|5\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setOffset|(I)V|false\n21|6\n4\n96\n25|1\n185|java/util/List|size|()I|true\n162|70\n25|7\n25|1\n21|6\n4\n96\n185|java/util/List|get|(I)Ljava/lang/Object;|true\n192|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setNext|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n167|73\n25|7\n1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setNext|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n21|6\n4\n100\n156|81\n25|7\n1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setPrev|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n167|89\n25|7\n25|1\n21|6\n4\n100\n185|java/util/List|get|(I)Ljava/lang/Object;|true\n192|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setPrev|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n21|5\n25|7\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n96\n54|5\n132|6|1\n167|10\n25|4\n198|102\n25|3\n25|4\n185|java/util/List|add|(Ljava/lang/Object;)Z|true\n87\n25|3\n176");
bonsVerify(c,"transferSegments","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;I)V","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n58|4\n25|1\n185|me/jellysquid/mods/sodium/client/gl/device/CommandList|createMutableBuffer|()Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;|true\n58|5\n25|1\n25|5\n21|3\n133\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n105\n178|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|BUFFER_USAGE|Lme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;\n185|me/jellysquid/mods/sodium/client/gl/device/CommandList|allocateStorage|(Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;JLme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;)V|true\n25|2\n185|java/util/Collection|iterator|()Ljava/util/Iterator;|true\n58|6\n25|6\n185|java/util/Iterator|hasNext|()Z|true\n153|52\n25|6\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand\n58|7\n25|1\n25|4\n25|5\n25|7\n180|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|readOffset|I\n133\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n105\n25|7\n180|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|writeOffset|I\n133\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n105\n25|7\n180|me/jellysquid/mods/sodium/client/gl/arena/PendingBufferCopyCommand|length|I\n133\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n105\n185|me/jellysquid/mods/sodium/client/gl/device/CommandList|copyBufferSubData|(Lme/jellysquid/mods/sodium/client/gl/buffer/GlBuffer;Lme/jellysquid/mods/sodium/client/gl/buffer/GlBuffer;JJJ)V|true\n167|19\n25|1\n25|4\n185|me/jellysquid/mods/sodium/client/gl/device/CommandList|deleteBuffer|(Lme/jellysquid/mods/sodium/client/gl/buffer/GlBuffer;)V|true\n25|0\n25|5\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n25|0\n21|3\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n25|0\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n5\n108\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|resizeIncrement|I\n177");
bonsVerify(c,"getUsedSegments","()Ljava/util/ArrayList;","187|java/util/ArrayList\n89\n183|java/util/ArrayList|<init>|()V|false\n58|1\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n58|2\n25|2\n198|22\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|3\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n154|19\n25|1\n25|2\n182|java/util/ArrayList|add|(Ljava/lang/Object;)Z|false\n87\n25|3\n58|2\n167|7\n25|1\n176");
bonsVerify(c,"getDeviceUsedMemory","()I","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n104\n172");
bonsVerify(c,"getDeviceAllocatedMemory","()I","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n104\n172");
bonsVerify(c,"getDeviceUsedMemoryL","()J","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n133\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n105\n173");
bonsVerify(c,"getDeviceAllocatedMemoryL","()J","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n133\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n105\n173");
bonsVerify(c,"alloc","(I)Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;","25|0\n21|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|findFree|(I)Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|2\n25|2\n199|8\n1\n176\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n21|1\n160|18\n25|2\n3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setFree|(Z)V|false\n25|2\n58|3\n167|53\n187|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment\n89\n25|0\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getEnd|()I|false\n21|1\n100\n21|1\n183|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|<init>|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferArena;II)V|false\n58|4\n25|4\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setNext|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|4\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setPrev|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n198|42\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setPrev|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|2\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n21|1\n100\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setLength|(I)V|false\n25|2\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setNext|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|4\n58|3\n25|0\n89\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n96\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n25|0\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|checkAssertions|()V|false\n25|3\n176");
bonsVerify(c,"findFree","(I)Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n58|2\n1\n58|3\n25|2\n198|33\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|29\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n21|1\n160|16\n25|2\n176\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n21|1\n161|29\n25|3\n198|27\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n164|29\n25|2\n58|3\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|2\n167|5\n25|3\n176");
bonsVerify(c,"free","(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V","25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|8\n187|java/lang/IllegalStateException\n89\n18|Already freed\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|1\n4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|setFree|(Z)V|false\n25|0\n89\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n100\n181|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|2\n25|2\n198|29\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|29\n25|1\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|mergeInto|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getPrev|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|3\n25|3\n198|40\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|40\n25|3\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|mergeInto|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n25|0\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|checkAssertions|()V|false\n177");
bonsVerify(c,"delete","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;)V","25|1\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n185|me/jellysquid/mods/sodium/client/gl/device/CommandList|deleteBuffer|(Lme/jellysquid/mods/sodium/client/gl/buffer/GlBuffer;)V|true\n177");
bonsVerify(c,"isEmpty","()Z","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n157|5\n4\n167|6\n3\n172");
bonsVerify(c,"getBufferObject","()Lme/jellysquid/mods/sodium/client/gl/buffer/GlBuffer;","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n176");
bonsVerify(c,"upload","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/stream/Stream;)Z","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n58|3\n25|2\n186|get|()Ljava/util/function/Supplier;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|()Ljava/lang/Object;|java/util/LinkedList.<init>()V (8)|()Ljava/util/LinkedList;\n184|java/util/stream/Collectors|toCollection|(Ljava/util/function/Supplier;)Ljava/util/stream/Collector;|false\n185|java/util/stream/Stream|collect|(Ljava/util/stream/Collector;)Ljava/lang/Object;|true\n192|java/util/List\n58|4\n25|0\n25|1\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|tryUploads|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/List;)V|false\n25|4\n185|java/util/List|isEmpty|()Z|true\n154|43\n25|4\n185|java/util/List|stream|()Ljava/util/stream/Stream;|true\n186|applyAsLong|()Ljava/util/function/ToLongFunction;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)J|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena.lambda$upload$0(Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)J (6)|(Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)J\n185|java/util/stream/Stream|mapToLong|(Ljava/util/function/ToLongFunction;)Ljava/util/stream/LongStream;|true\n185|java/util/stream/LongStream|sum|()J|true\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n109\n136\n54|5\n25|0\n25|1\n21|5\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|ensureCapacity|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;I)V|false\n25|0\n25|1\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|tryUploads|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/List;)V|false\n25|4\n185|java/util/List|isEmpty|()Z|true\n154|43\n187|java/lang/RuntimeException\n89\n18|Failed to upload all buffers\n183|java/lang/RuntimeException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n25|3\n165|49\n4\n167|50\n3\n172");
bonsVerify(c,"tryUploads","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/List;)V","25|2\n25|0\n25|1\n186|test|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferArena;Lme/jellysquid/mods/sodium/client/gl/device/CommandList;)Ljava/util/function/Predicate;|java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; (6)|(Ljava/lang/Object;)Z|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena.lambda$tryUploads$1(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)Z (5)|(Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)Z\n185|java/util/List|removeIf|(Ljava/util/function/Predicate;)Z|true\n87\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stagingBuffer|Lme/jellysquid/mods/sodium/client/gl/arena/staging/StagingBuffer;\n25|1\n185|me/jellysquid/mods/sodium/client/gl/arena/staging/StagingBuffer|flush|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;)V|true\n177");
bonsVerify(c,"tryUpload","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)Z","25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/PendingUpload|getDataBuffer|()Lme/jellysquid/mods/sodium/client/util/NativeBuffer;|false\n182|me/jellysquid/mods/sodium/client/util/NativeBuffer|getDirectBuffer|()Ljava/nio/ByteBuffer;|false\n58|3\n25|3\n182|java/nio/ByteBuffer|remaining|()I|false\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n108\n54|4\n25|0\n21|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|alloc|(I)Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|5\n25|5\n199|18\n3\n172\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stagingBuffer|Lme/jellysquid/mods/sodium/client/gl/arena/staging/StagingBuffer;\n25|1\n25|3\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|arenaBuffer|Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;\n25|5\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n133\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|stride|I\n133\n105\n185|me/jellysquid/mods/sodium/client/gl/arena/staging/StagingBuffer|enqueueCopy|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/nio/ByteBuffer;Lme/jellysquid/mods/sodium/client/gl/buffer/GlBuffer;J)V|true\n25|2\n25|5\n182|me/jellysquid/mods/sodium/client/gl/arena/PendingUpload|setResult|(Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;)V|false\n4\n172");
bonsVerify(c,"ensureCapacity","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;I)V","21|2\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n100\n100\n54|3\n25|0\n25|1\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|resizeIncrement|I\n96\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n21|3\n96\n184|java/lang/Math|max|(II)I|false\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|resize|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;I)V|false\n177");
bonsVerify(c,"checkAssertions","()V","177");
bonsVerify(c,"checkAssertions0","()V","25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|head|Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;\n58|1\n3\n54|2\n25|1\n198|116\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n156|15\n187|java/lang/IllegalStateException\n89\n18|segment.start < 0: out of bounds\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getEnd|()I|false\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n164|25\n187|java/lang/IllegalStateException\n89\n18|segment.end > arena.capacity: out of bounds\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n154|33\n21|2\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getLength|()I|false\n96\n54|2\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|3\n25|3\n198|73\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getEnd|()I|false\n162|48\n187|java/lang/IllegalStateException\n89\n18|segment.next.start < segment.end: overlapping segments (corrupted)\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getEnd|()I|false\n164|58\n187|java/lang/IllegalStateException\n89\n18|segment.next.start > segment.end: not truly connected (sparsity error)\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|73\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n198|73\n25|3\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getNext|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|73\n187|java/lang/IllegalStateException\n89\n18|segment.free && segment.next.free: not merged consecutive segments\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getPrev|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n58|4\n25|4\n198|113\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getEnd|()I|false\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n164|88\n187|java/lang/IllegalStateException\n89\n18|segment.prev.end > segment.start: overlapping segments (corrupted)\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getEnd|()I|false\n25|1\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getOffset|()I|false\n162|98\n187|java/lang/IllegalStateException\n89\n18|segment.prev.end < segment.start: not truly connected (sparsity error)\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|113\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getPrev|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n198|113\n25|4\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|getPrev|()Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment;|false\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferSegment|isFree|()Z|false\n153|113\n187|java/lang/IllegalStateException\n89\n18|segment.free && segment.prev.free: not merged consecutive segments\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|3\n58|1\n167|5\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n156|124\n187|java/lang/IllegalStateException\n89\n18|arena.used < 0: failure to track\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|capacity|I\n164|134\n187|java/lang/IllegalStateException\n89\n18|arena.used > arena.capacity: failure to track\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n180|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|used|I\n21|2\n159|143\n187|java/lang/IllegalStateException\n89\n18|arena.used is invalid\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n177");
bonsVerify(c,"lambda$tryUploads$1","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)Z","25|0\n25|1\n25|2\n182|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|tryUpload|(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)Z|false\n172");
bonsVerify(c,"lambda$upload$0","(Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)J","25|0\n182|me/jellysquid/mods/sodium/client/gl/arena/PendingUpload|getDataBuffer|()Lme/jellysquid/mods/sodium/client/util/NativeBuffer;|false\n182|me/jellysquid/mods/sodium/client/util/NativeBuffer|getLength|()I|false\n133\n173");
bonsVerify(c,"<clinit>","()V","178|me/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage|STATIC_DRAW|Lme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;\n179|me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena|BUFFER_USAGE|Lme/jellysquid/mods/sodium/client/gl/buffer/GlBufferUsage;\n177");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"upload","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/stream/Stream;)Z",51);
methodVisitor = new MethodNode(1,"upload","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/stream/Stream;)Z","(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/stream/Stream<Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;>;)Z",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(274, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "arenaBuffer", "Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;");
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(277, label1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("()Ljava/lang/Object;"), new Handle(O.H_NEWINVOKESPECIAL, "java/util/LinkedList", "<init>", "()V", false), Type.getType("()Ljava/util/LinkedList;")]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/util/stream/Collectors", "toCollection", "(Ljava/util/function/Supplier;)Ljava/util/stream/Collector;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "collect", "(Ljava/util/stream/Collector;)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/util/List");
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(280, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "tryUploads", "(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/List;)V", false);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(283, label3);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(285, label5);
methodVisitor.visitVarInsn(O.ALOAD, 4);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(286, label6);
methodVisitor.visitLineNumber(287, label6);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "ac$pendingBytes", "(Ljava/util/List;)J", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "stride", "I");
methodVisitor.visitInsn(O.I2L);
methodVisitor.visitInsn(O.LDIV);
methodVisitor.visitInsn(O.L2I);
methodVisitor.visitVarInsn(O.ISTORE, 5);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(292, label7);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "ensureCapacity", "(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;I)V", false);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(295, label8);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "tryUploads", "(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Ljava/util/List;)V", false);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(298, label9);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
methodVisitor.visitJumpInsn(O.IFNE, label4);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(299, label10);
methodVisitor.visitTypeInsn(O.NEW, "java/lang/RuntimeException");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitLdcInsn("Failed to upload all buffers");
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
methodVisitor.visitInsn(O.ATHROW);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(303, label4);
methodVisitor.visitFrame(O.F_APPEND,2, ["me/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer", "java/util/List"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "arenaBuffer", "Lme/jellysquid/mods/sodium/client/gl/buffer/GlMutableBuffer;");
methodVisitor.visitVarInsn(O.ALOAD, 3);
var label11 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label11);
methodVisitor.visitInsn(O.ICONST_1);
var label12 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label12);
methodVisitor.visitLabel(label11);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLabel(label12);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
methodVisitor.visitInsn(O.IRETURN);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLocalVariable("remainingElements", "I", null, label7, label4, 5);
methodVisitor.visitLocalVariable("this", "Lme/jellysquid/mods/sodium/client/gl/arena/GlBufferArena;", null, label0, label13, 0);
methodVisitor.visitLocalVariable("commandList", "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;", null, label0, label13, 1);
methodVisitor.visitLocalVariable("stream", "Ljava/util/stream/Stream;", "Ljava/util/stream/Stream<Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;>;", label0, label13, 2);
methodVisitor.visitLocalVariable("buffer", "Lme/jellysquid/mods/sodium/client/gl/buffer/GlBuffer;", null, label1, label13, 3);
methodVisitor.visitLocalVariable("queue", "Ljava/util/List;", "Ljava/util/List<Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;>;", label2, label13, 4);
methodVisitor.visitMaxs(4, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"ac$pendingBytes","(Ljava/util/List;)J","(Ljava/util/List<Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;>;)J",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(164, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 1);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "me/jellysquid/mods/sodium/client/gl/arena/PendingUpload");
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label3);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
methodVisitor.visitLdcInsn(Type.getType("Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;"));
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label3);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/gl/arena/PendingUpload", "getDataBuffer", "()Lme/jellysquid/mods/sodium/client/util/NativeBuffer;", false);
methodVisitor.visitJumpInsn(O.IFNULL, label3);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/gl/arena/PendingUpload", "getDataBuffer", "()Lme/jellysquid/mods/sodium/client/util/NativeBuffer;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
methodVisitor.visitLdcInsn(Type.getType("Lme/jellysquid/mods/sodium/client/util/NativeBuffer;"));
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label4);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(165, label3);
methodVisitor.visitFrame(O.F_APPEND,1, ["me/jellysquid/mods/sodium/client/gl/arena/PendingUpload"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "stream", "()Ljava/util/stream/Stream;", true);
methodVisitor.visitInvokeDynamicInsn("applyAsLong", "()Ljava/util/function/ToLongFunction;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)J"), new Handle(O.H_INVOKESTATIC, "me/jellysquid/mods/sodium/client/gl/arena/GlBufferArena", "lambda$ac$pendingBytes$0", "(Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)J", false), Type.getType("(Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)J")]);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "mapToLong", "(Ljava/util/function/ToLongFunction;)Ljava/util/stream/LongStream;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/LongStream", "sum", "()J", true);
methodVisitor.visitInsn(O.LRETURN);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(164, label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label1);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(166, label2);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitInsn(O.LCONST_0);
methodVisitor.visitVarInsn(O.LSTORE, 1);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(167, label5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_FULL, 3, ["java/util/List", O.LONG, "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label7);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "me/jellysquid/mods/sodium/client/gl/arena/PendingUpload");
methodVisitor.visitVarInsn(O.ASTORE, 4);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/gl/arena/PendingUpload", "getDataBuffer", "()Lme/jellysquid/mods/sodium/client/util/NativeBuffer;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/util/NativeBuffer", "getLength", "()I", false);
methodVisitor.visitInsn(O.I2L);
methodVisitor.visitInsn(O.LADD);
methodVisitor.visitVarInsn(O.LSTORE, 1);
methodVisitor.visitJumpInsn(O.GOTO, label6);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(168, label7);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitInsn(O.LRETURN);
methodVisitor.visitMaxs(4, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(4106,"lambda$ac$pendingBytes$0","(Lme/jellysquid/mods/sodium/client/gl/arena/PendingUpload;)J",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(165, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/gl/arena/PendingUpload", "getDataBuffer", "()Lme/jellysquid/mods/sodium/client/util/NativeBuffer;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "me/jellysquid/mods/sodium/client/util/NativeBuffer", "getLength", "()I", false);
methodVisitor.visitInsn(O.I2L);
methodVisitor.visitInsn(O.LRETURN);
methodVisitor.visitMaxs(2, 1);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
