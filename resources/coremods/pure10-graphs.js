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
var BONS_KEY = "oculus_empty_transparency_graphs"; var BONS_SCRIPT = "pure10-graphs.js"; var BONS_TARGET = "net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager";
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
 if(result.join('\n')!==expected)throw new Error('Opcode/operand mismatch '+name+desc);
}

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"net.irisshaders.batchedentityrendering.impl.ordering.GraphTranslucencyRenderOrderManager"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n25|0\n3\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n25|0\n187|de/odysseus/ithaka/digraph/util/fas/SimpleFeedbackArcSetProvider\n89\n183|de/odysseus/ithaka/digraph/util/fas/SimpleFeedbackArcSetProvider|<init>|()V|false\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|feedbackArcSetProvider|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;\n25|0\n187|java/util/EnumMap\n89\n18|Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;\n183|java/util/EnumMap|<init>|(Ljava/lang/Class;)V|false\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|0\n187|java/util/EnumMap\n89\n18|Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;\n183|java/util/EnumMap|<init>|(Ljava/lang/Class;)V|false\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n184|net/irisshaders/batchedentityrendering/impl/TransparencyType|values|()[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|false\n58|1\n25|1\n190\n54|2\n3\n54|3\n21|3\n21|2\n162|46\n25|1\n21|3\n50\n58|4\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|4\n187|de/odysseus/ithaka/digraph/MapDigraph\n89\n183|de/odysseus/ithaka/digraph/MapDigraph|<init>|()V|false\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n87\n132|3|1\n167|29\n177");
bonsVerify(c,"getTransparencyType","(Lnet/minecraft/client/renderer/RenderType;)Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;","25|0\n193|net/irisshaders/batchedentityrendering/impl/WrappableRenderType\n153|8\n25|0\n192|net/irisshaders/batchedentityrendering/impl/WrappableRenderType\n185|net/irisshaders/batchedentityrendering/impl/WrappableRenderType|unwrap|()Lnet/minecraft/client/renderer/RenderType;|true\n58|0\n167|0\n25|0\n193|net/irisshaders/batchedentityrendering/impl/BlendingStateHolder\n153|15\n25|0\n192|net/irisshaders/batchedentityrendering/impl/BlendingStateHolder\n185|net/irisshaders/batchedentityrendering/impl/BlendingStateHolder|getTransparencyType|()Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|true\n176\n178|net/irisshaders/batchedentityrendering/impl/TransparencyType|GENERAL_TRANSPARENT|Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;\n176");
bonsVerify(c,"begin","(Lnet/minecraft/client/renderer/RenderType;)V","25|1\n184|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|getTransparencyType|(Lnet/minecraft/client/renderer/RenderType;)Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|false\n58|2\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|2\n182|java/util/EnumMap|get|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|de/odysseus/ithaka/digraph/Digraph\n58|3\n25|3\n25|1\n185|de/odysseus/ithaka/digraph/Digraph|add|(Ljava/lang/Object;)Z|true\n87\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n153|40\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n25|2\n25|1\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n192|net/minecraft/client/renderer/RenderType\n58|4\n25|4\n199|26\n177\n25|3\n25|4\n25|1\n185|de/odysseus/ithaka/digraph/Digraph|get|(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/OptionalInt;|true\n3\n182|java/util/OptionalInt|orElse|(I)I|false\n54|5\n132|5|1\n25|3\n25|4\n25|1\n21|5\n185|de/odysseus/ithaka/digraph/Digraph|put|(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/util/OptionalInt;|true\n87\n177");
bonsVerify(c,"startGroup","()V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n153|8\n187|java/lang/IllegalStateException\n89\n18|Already in a group\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n25|0\n4\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n177");
bonsVerify(c,"maybeStartGroup","()Z","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n153|5\n3\n172\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n25|0\n4\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n4\n172");
bonsVerify(c,"endGroup","()V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n154|8\n187|java/lang/IllegalStateException\n89\n18|Not in a group\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n25|0\n3\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n177");
bonsVerify(c,"reset","()V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n184|net/irisshaders/batchedentityrendering/impl/TransparencyType|values|()[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|false\n58|1\n25|1\n190\n54|2\n3\n54|3\n21|3\n21|2\n162|27\n25|1\n21|3\n50\n58|4\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|4\n187|de/odysseus/ithaka/digraph/MapDigraph\n89\n183|de/odysseus/ithaka/digraph/MapDigraph|<init>|()V|false\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n87\n132|3|1\n167|10\n177");
bonsVerify(c,"resetType","(Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;)V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|1\n187|de/odysseus/ithaka/digraph/MapDigraph\n89\n183|de/odysseus/ithaka/digraph/MapDigraph|<init>|()V|false\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n87\n177");
bonsVerify(c,"getRenderOrder","()Ljava/util/List;","3\n54|1\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|values|()Ljava/util/Collection;|false\n185|java/util/Collection|iterator|()Ljava/util/Iterator;|true\n58|2\n25|2\n185|java/util/Iterator|hasNext|()Z|true\n153|20\n25|2\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|de/odysseus/ithaka/digraph/Digraph\n58|3\n21|1\n25|3\n185|de/odysseus/ithaka/digraph/Digraph|getVertexCount|()I|true\n96\n54|1\n167|7\n187|java/util/ArrayList\n89\n21|1\n183|java/util/ArrayList|<init>|(I)V|false\n58|2\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|values|()Ljava/util/Collection;|false\n185|java/util/Collection|iterator|()Ljava/util/Iterator;|true\n58|3\n25|3\n185|java/util/Iterator|hasNext|()Z|true\n153|84\n25|3\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|de/odysseus/ithaka/digraph/Digraph\n58|4\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|feedbackArcSetProvider|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;\n25|4\n25|4\n178|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy|MIN_WEIGHT|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;\n185|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider|getFeedbackArcSet|(Lde/odysseus/ithaka/digraph/Digraph;Lde/odysseus/ithaka/digraph/EdgeWeights;Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;)Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSet;|true\n58|5\n25|5\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|getEdgeCount|()I|false\n158|77\n25|5\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|vertices|()Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|6\n25|6\n185|java/util/Iterator|hasNext|()Z|true\n153|77\n25|6\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/client/renderer/RenderType\n58|7\n25|5\n25|7\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|targets|(Ljava/lang/Object;)Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|8\n25|8\n185|java/util/Iterator|hasNext|()Z|true\n153|76\n25|8\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/client/renderer/RenderType\n58|9\n25|4\n25|7\n25|9\n185|de/odysseus/ithaka/digraph/Digraph|remove|(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/OptionalInt;|true\n87\n167|63\n167|51\n25|2\n25|4\n3\n184|de/odysseus/ithaka/digraph/Digraphs|toposort|(Lde/odysseus/ithaka/digraph/Digraph;Z)Ljava/util/List;|false\n185|java/util/List|addAll|(Ljava/util/Collection;)Z|true\n87\n167|30\n25|2\n176");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getRenderOrder","()Ljava/util/List;",86);
methodVisitor = new MethodNode(1,"getRenderOrder","()Ljava/util/List;","()Ljava/util/List<Lnet/minecraft/client/renderer/RenderType;>;",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(41, label0);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 1);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(42, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "types", "Ljava/util/EnumMap;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/EnumMap", "values", "()Ljava/util/Collection;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Collection", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_APPEND,2, [O.INTEGER, "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label3);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "de/odysseus/ithaka/digraph/Digraph");
methodVisitor.visitVarInsn(O.ASTORE, 3);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "de/odysseus/ithaka/digraph/Digraph", "getVertexCount", "()I", true);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitVarInsn(O.ISTORE, 1);
methodVisitor.visitJumpInsn(O.GOTO, label2);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(43, label3);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitTypeInsn(O.NEW, "java/util/ArrayList");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "java/util/ArrayList", "<init>", "(I)V", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(44, label4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "types", "Ljava/util/EnumMap;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/EnumMap", "values", "()Ljava/util/Collection;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Collection", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_FULL, 4, ["net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", O.INTEGER, "java/util/ArrayList", "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label6);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "de/odysseus/ithaka/digraph/Digraph");
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(45, label7);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "de/odysseus/ithaka/digraph/Digraph", "getClass", "()Ljava/lang/Class;", true);
methodVisitor.visitLdcInsn(Type.getType("Lde/odysseus/ithaka/digraph/MapDigraph;"));
var label8 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label8);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "feedbackArcSetProvider", "Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider", "getClass", "()Ljava/lang/Class;", true);
methodVisitor.visitLdcInsn(Type.getType("Lde/odysseus/ithaka/digraph/util/fas/SimpleFeedbackArcSetProvider;"));
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label8);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "de/odysseus/ithaka/digraph/Digraph", "getVertexCount", "()I", true);
methodVisitor.visitJumpInsn(O.IFNE, label8);
methodVisitor.visitJumpInsn(O.GOTO, label5);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(46, label8);
methodVisitor.visitFrame(O.F_APPEND,1, ["de/odysseus/ithaka/digraph/Digraph"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "feedbackArcSetProvider", "Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitFieldInsn(O.GETSTATIC, "de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy", "MIN_WEIGHT", "Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider", "getFeedbackArcSet", "(Lde/odysseus/ithaka/digraph/Digraph;Lde/odysseus/ithaka/digraph/EdgeWeights;Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;)Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSet;", true);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(47, label9);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet", "getEdgeCount", "()I", false);
var label10 = new Label();
methodVisitor.visitJumpInsn(O.IFLE, label10);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet", "vertices", "()Ljava/lang/Iterable;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitFrame(O.F_APPEND,2, ["de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet", "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
methodVisitor.visitJumpInsn(O.IFEQ, label10);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/client/renderer/RenderType");
methodVisitor.visitVarInsn(O.ASTORE, 7);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet", "targets", "(Ljava/lang/Object;)Ljava/lang/Iterable;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/lang/Iterable", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/client/renderer/RenderType", "java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label13 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label13);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/client/renderer/RenderType");
methodVisitor.visitVarInsn(O.ASTORE, 9);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "de/odysseus/ithaka/digraph/Digraph", "remove", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/OptionalInt;", true);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitJumpInsn(O.GOTO, label12);
methodVisitor.visitLabel(label13);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label11);
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(48, label10);
methodVisitor.visitFrame(O.F_CHOP,3, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "de/odysseus/ithaka/digraph/Digraphs", "toposort", "(Lde/odysseus/ithaka/digraph/Digraph;Z)Ljava/util/List;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/ArrayList", "addAll", "(Ljava/util/Collection;)Z", false);
methodVisitor.visitInsn(O.POP);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(49, label14);
methodVisitor.visitJumpInsn(O.GOTO, label5);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(50, label6);
methodVisitor.visitFrame(O.F_CHOP,2, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(4, 10);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}

var bons14PriorInitializer=initializeCoreMod;
initializeCoreMod=function(){var defs=bons14PriorInitializer();var prior=defs.patch.transformer;defs.patch.transformer=function(c){c=prior(c);var oldKey=BONS_KEY,oldScript=BONS_SCRIPT;BONS_KEY="oculus_reuse_empty_graphs";BONS_SCRIPT="pure14-graphs.js";try{return bonsGuarded(c,function(c) {
bonsVerify(c,"<init>","()V","25|0\n183|java/lang/Object|<init>|()V|false\n25|0\n3\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n25|0\n187|de/odysseus/ithaka/digraph/util/fas/SimpleFeedbackArcSetProvider\n89\n183|de/odysseus/ithaka/digraph/util/fas/SimpleFeedbackArcSetProvider|<init>|()V|false\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|feedbackArcSetProvider|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;\n25|0\n187|java/util/EnumMap\n89\n18|Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;\n183|java/util/EnumMap|<init>|(Ljava/lang/Class;)V|false\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|0\n187|java/util/EnumMap\n89\n18|Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;\n183|java/util/EnumMap|<init>|(Ljava/lang/Class;)V|false\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n184|net/irisshaders/batchedentityrendering/impl/TransparencyType|values|()[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|false\n58|1\n25|1\n190\n54|2\n3\n54|3\n21|3\n21|2\n162|46\n25|1\n21|3\n50\n58|4\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|4\n187|de/odysseus/ithaka/digraph/MapDigraph\n89\n183|de/odysseus/ithaka/digraph/MapDigraph|<init>|()V|false\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n87\n132|3|1\n167|29\n177");
bonsVerify(c,"getTransparencyType","(Lnet/minecraft/client/renderer/RenderType;)Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;","25|0\n193|net/irisshaders/batchedentityrendering/impl/WrappableRenderType\n153|8\n25|0\n192|net/irisshaders/batchedentityrendering/impl/WrappableRenderType\n185|net/irisshaders/batchedentityrendering/impl/WrappableRenderType|unwrap|()Lnet/minecraft/client/renderer/RenderType;|true\n58|0\n167|0\n25|0\n193|net/irisshaders/batchedentityrendering/impl/BlendingStateHolder\n153|15\n25|0\n192|net/irisshaders/batchedentityrendering/impl/BlendingStateHolder\n185|net/irisshaders/batchedentityrendering/impl/BlendingStateHolder|getTransparencyType|()Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|true\n176\n178|net/irisshaders/batchedentityrendering/impl/TransparencyType|GENERAL_TRANSPARENT|Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;\n176");
bonsVerify(c,"begin","(Lnet/minecraft/client/renderer/RenderType;)V","25|1\n184|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|getTransparencyType|(Lnet/minecraft/client/renderer/RenderType;)Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|false\n58|2\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|2\n182|java/util/EnumMap|get|(Ljava/lang/Object;)Ljava/lang/Object;|false\n192|de/odysseus/ithaka/digraph/Digraph\n58|3\n25|3\n25|1\n185|de/odysseus/ithaka/digraph/Digraph|add|(Ljava/lang/Object;)Z|true\n87\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n153|40\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n25|2\n25|1\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n192|net/minecraft/client/renderer/RenderType\n58|4\n25|4\n199|26\n177\n25|3\n25|4\n25|1\n185|de/odysseus/ithaka/digraph/Digraph|get|(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/OptionalInt;|true\n3\n182|java/util/OptionalInt|orElse|(I)I|false\n54|5\n132|5|1\n25|3\n25|4\n25|1\n21|5\n185|de/odysseus/ithaka/digraph/Digraph|put|(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/util/OptionalInt;|true\n87\n177");
bonsVerify(c,"startGroup","()V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n153|8\n187|java/lang/IllegalStateException\n89\n18|Already in a group\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n25|0\n4\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n177");
bonsVerify(c,"maybeStartGroup","()Z","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n153|5\n3\n172\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n25|0\n4\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n4\n172");
bonsVerify(c,"endGroup","()V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n154|8\n187|java/lang/IllegalStateException\n89\n18|Not in a group\n183|java/lang/IllegalStateException|<init>|(Ljava/lang/String;)V|false\n191\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|currentTypes|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n25|0\n3\n181|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|inGroup|Z\n177");
bonsVerify(c,"reset","()V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|clear|()V|false\n184|net/irisshaders/batchedentityrendering/impl/TransparencyType|values|()[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;|false\n58|1\n25|1\n190\n54|2\n3\n54|3\n21|3\n21|2\n162|27\n25|1\n21|3\n50\n58|4\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|4\n187|de/odysseus/ithaka/digraph/MapDigraph\n89\n183|de/odysseus/ithaka/digraph/MapDigraph|<init>|()V|false\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n87\n132|3|1\n167|10\n177");
bonsVerify(c,"resetType","(Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;)V","25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n25|1\n187|de/odysseus/ithaka/digraph/MapDigraph\n89\n183|de/odysseus/ithaka/digraph/MapDigraph|<init>|()V|false\n182|java/util/EnumMap|put|(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;|false\n87\n177");
try{bonsVerify(c,"getRenderOrder","()Ljava/util/List;","3\n54|1\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|values|()Ljava/util/Collection;|false\n185|java/util/Collection|iterator|()Ljava/util/Iterator;|true\n58|2\n25|2\n185|java/util/Iterator|hasNext|()Z|true\n153|20\n25|2\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|de/odysseus/ithaka/digraph/Digraph\n58|3\n21|1\n25|3\n185|de/odysseus/ithaka/digraph/Digraph|getVertexCount|()I|true\n96\n54|1\n167|7\n187|java/util/ArrayList\n89\n21|1\n183|java/util/ArrayList|<init>|(I)V|false\n58|2\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|values|()Ljava/util/Collection;|false\n185|java/util/Collection|iterator|()Ljava/util/Iterator;|true\n58|3\n25|3\n185|java/util/Iterator|hasNext|()Z|true\n153|84\n25|3\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|de/odysseus/ithaka/digraph/Digraph\n58|4\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|feedbackArcSetProvider|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;\n25|4\n25|4\n178|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy|MIN_WEIGHT|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;\n185|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider|getFeedbackArcSet|(Lde/odysseus/ithaka/digraph/Digraph;Lde/odysseus/ithaka/digraph/EdgeWeights;Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;)Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSet;|true\n58|5\n25|5\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|getEdgeCount|()I|false\n158|77\n25|5\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|vertices|()Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|6\n25|6\n185|java/util/Iterator|hasNext|()Z|true\n153|77\n25|6\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/client/renderer/RenderType\n58|7\n25|5\n25|7\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|targets|(Ljava/lang/Object;)Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|8\n25|8\n185|java/util/Iterator|hasNext|()Z|true\n153|76\n25|8\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/client/renderer/RenderType\n58|9\n25|4\n25|7\n25|9\n185|de/odysseus/ithaka/digraph/Digraph|remove|(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/OptionalInt;|true\n87\n167|63\n167|51\n25|2\n25|4\n3\n184|de/odysseus/ithaka/digraph/Digraphs|toposort|(Lde/odysseus/ithaka/digraph/Digraph;Z)Ljava/util/List;|false\n185|java/util/List|addAll|(Ljava/util/Collection;)Z|true\n87\n167|30\n25|2\n176");}catch(bons14PriorShape){bonsVerify(c,"getRenderOrder","()Ljava/util/List;","3\n54|1\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|values|()Ljava/util/Collection;|false\n185|java/util/Collection|iterator|()Ljava/util/Iterator;|true\n58|2\n25|2\n185|java/util/Iterator|hasNext|()Z|true\n153|20\n25|2\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|de/odysseus/ithaka/digraph/Digraph\n58|3\n21|1\n25|3\n185|de/odysseus/ithaka/digraph/Digraph|getVertexCount|()I|true\n96\n54|1\n167|7\n187|java/util/ArrayList\n89\n21|1\n183|java/util/ArrayList|<init>|(I)V|false\n58|2\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|types|Ljava/util/EnumMap;\n182|java/util/EnumMap|values|()Ljava/util/Collection;|false\n185|java/util/Collection|iterator|()Ljava/util/Iterator;|true\n58|3\n25|3\n185|java/util/Iterator|hasNext|()Z|true\n153|97\n25|3\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|de/odysseus/ithaka/digraph/Digraph\n58|4\n25|4\n185|de/odysseus/ithaka/digraph/Digraph|getClass|()Ljava/lang/Class;|true\n18|Lde/odysseus/ithaka/digraph/MapDigraph;\n166|50\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|feedbackArcSetProvider|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;\n185|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider|getClass|()Ljava/lang/Class;|true\n18|Lde/odysseus/ithaka/digraph/util/fas/SimpleFeedbackArcSetProvider;\n166|50\n25|4\n185|de/odysseus/ithaka/digraph/Digraph|getVertexCount|()I|true\n154|50\n167|30\n25|0\n180|net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager|feedbackArcSetProvider|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider;\n25|4\n25|4\n178|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy|MIN_WEIGHT|Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;\n185|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSetProvider|getFeedbackArcSet|(Lde/odysseus/ithaka/digraph/Digraph;Lde/odysseus/ithaka/digraph/EdgeWeights;Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSetPolicy;)Lde/odysseus/ithaka/digraph/util/fas/FeedbackArcSet;|true\n58|5\n25|5\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|getEdgeCount|()I|false\n158|90\n25|5\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|vertices|()Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|6\n25|6\n185|java/util/Iterator|hasNext|()Z|true\n153|90\n25|6\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/client/renderer/RenderType\n58|7\n25|5\n25|7\n182|de/odysseus/ithaka/digraph/util/fas/FeedbackArcSet|targets|(Ljava/lang/Object;)Ljava/lang/Iterable;|false\n185|java/lang/Iterable|iterator|()Ljava/util/Iterator;|true\n58|8\n25|8\n185|java/util/Iterator|hasNext|()Z|true\n153|89\n25|8\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|net/minecraft/client/renderer/RenderType\n58|9\n25|4\n25|7\n25|9\n185|de/odysseus/ithaka/digraph/Digraph|remove|(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/OptionalInt;|true\n87\n167|76\n167|64\n25|2\n25|4\n3\n184|de/odysseus/ithaka/digraph/Digraphs|toposort|(Lde/odysseus/ithaka/digraph/Digraph;Z)Ljava/util/List;|false\n182|java/util/ArrayList|addAll|(Ljava/util/Collection;)Z|false\n87\n167|30\n25|2\n176");}

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"reset","()V",28);
methodVisitor = new MethodNode(1,"reset","()V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(8, label0);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "ac$transparencyTypes", "[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;");
methodVisitor.visitVarInsn(O.ASTORE, 1);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.ARRAYLENGTH);
methodVisitor.visitVarInsn(O.ISTORE, 2);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_APPEND,3, ["[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;", O.INTEGER, O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitVarInsn(O.ILOAD, 2);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitInsn(O.AALOAD);
methodVisitor.visitVarInsn(O.ASTORE, 4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "types", "Ljava/util/EnumMap;");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/EnumMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "de/odysseus/ithaka/digraph/Digraph");
methodVisitor.visitVarInsn(O.ASTORE, 5);
methodVisitor.visitVarInsn(O.ALOAD, 5);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label3);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "de/odysseus/ithaka/digraph/Digraph", "getVertexCount", "()I", true);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label4);
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/irisshaders/batchedentityrendering/impl/TransparencyType", "de/odysseus/ithaka/digraph/Digraph"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "types", "Ljava/util/EnumMap;");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitTypeInsn(O.NEW, "de/odysseus/ithaka/digraph/MapDigraph");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "de/odysseus/ithaka/digraph/MapDigraph", "<init>", "()V", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/EnumMap", "put", "(Ljava/lang/Enum;Ljava/lang/Object;)Ljava/lang/Object;", false);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitIincInsn(3, 1);
methodVisitor.visitJumpInsn(O.GOTO, label1);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_CHOP,2, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(4, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(8,"<clinit>","()V",null,[]);
methodVisitor.visitCode();
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/irisshaders/batchedentityrendering/impl/TransparencyType", "values", "()[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;", false);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "net/irisshaders/batchedentityrendering/impl/ordering/GraphTranslucencyRenderOrderManager", "ac$transparencyTypes", "[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;");
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(1, 0);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
{
fieldVisitor = c.visitField(O.ACC_PRIVATE | O.ACC_FINAL | O.ACC_STATIC, "ac$transparencyTypes", "[Lnet/irisshaders/batchedentityrendering/impl/TransparencyType;", null, null);
fieldVisitor.visitEnd();
}
return c;
});}finally{BONS_KEY=oldKey;BONS_SCRIPT=oldScript;}};return defs;};
