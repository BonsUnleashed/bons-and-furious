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
var BONS_KEY = "valkyrien_entity_unloads"; var BONS_SCRIPT = "pure15-valkyrien-MixinPersistentEntitySectionManager.js"; var BONS_TARGET = "org.valkyrienskies.mod.mixin.feature.shipyard_entities.MixinPersistentEntitySectionManager";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"org.valkyrienskies.mod.mixin.feature.shipyard_entities.MixinPersistentEntitySectionManager"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"replaceProcessUnloads","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V","187|it/unimi/dsi/fastutil/longs/LongOpenHashSet\n89\n183|it/unimi/dsi/fastutil/longs/LongOpenHashSet|<init>|()V|false\n58|2\n25|0\n180|org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager|f_157499_|Lit/unimi/dsi/fastutil/longs/LongSet;\n185|it/unimi/dsi/fastutil/longs/LongSet|iterator|()Lit/unimi/dsi/fastutil/longs/LongIterator;|true\n58|3\n25|3\n185|java/util/Iterator|hasNext|()Z|true\n153|36\n25|3\n185|java/util/Iterator|next|()Ljava/lang/Object;|true\n192|java/lang/Long\n182|java/lang/Long|longValue|()J|false\n55|4\n25|0\n180|org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager|f_157497_|Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;\n22|4\n185|it/unimi/dsi/fastutil/longs/Long2ObjectMap|get|(J)Ljava/lang/Object;|true\n178|net/minecraft/world/level/entity/Visibility|HIDDEN|Lnet/minecraft/world/level/entity/Visibility;\n165|27\n25|2\n22|4\n185|it/unimi/dsi/fastutil/longs/LongSet|add|(J)Z|true\n87\n167|35\n25|0\n22|4\n182|org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager|m_157568_|(J)Z|false\n153|35\n25|2\n22|4\n185|it/unimi/dsi/fastutil/longs/LongSet|add|(J)Z|true\n87\n167|8\n25|0\n180|org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager|f_157499_|Lit/unimi/dsi/fastutil/longs/LongSet;\n25|2\n185|it/unimi/dsi/fastutil/longs/LongSet|removeAll|(Lit/unimi/dsi/fastutil/longs/LongCollection;)Z|true\n87\n167|45\n58|2\n25|2\n182|java/lang/Exception|printStackTrace|()V|false\n25|1\n182|org/spongepowered/asm/mixin/injection/callback/CallbackInfo|cancel|()V|false\n177\ncatch|0|41|42|java/lang/Exception");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"replaceProcessUnloads","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",48);
methodVisitor = new MethodNode(2,"replaceProcessUnloads","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,[]);
methodVisitor.visitParameter("ci", 0);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "processUnloads");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "HEAD");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visit("cancellable", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
var label1 = new Label();
var label2 = new Label();
methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager", "f_157499_", "Lit/unimi/dsi/fastutil/longs/LongSet;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongSet", "isEmpty", "()Z", true);
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel", "()V", false);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(68, label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitTypeInsn(O.NEW, "it/unimi/dsi/fastutil/longs/LongOpenHashSet");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "it/unimi/dsi/fastutil/longs/LongOpenHashSet", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(69, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager", "f_157499_", "Lit/unimi/dsi/fastutil/longs/LongSet;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongSet", "iterator", "()Lit/unimi/dsi/fastutil/longs/LongIterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_APPEND,2, ["it/unimi/dsi/fastutil/longs/LongOpenHashSet", "it/unimi/dsi/fastutil/longs/LongIterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label5);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/lang/Long");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
methodVisitor.visitVarInsn(O.LSTORE, 4);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(70, label6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager", "f_157497_", "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;");
methodVisitor.visitVarInsn(O.LLOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/Long2ObjectMap", "get", "(J)Ljava/lang/Object;", true);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/world/level/entity/Visibility", "HIDDEN", "Lnet/minecraft/world/level/entity/Visibility;");
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label7);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(71, label8);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.LLOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongSet", "add", "(J)Z", true);
methodVisitor.visitInsn(O.POP);
var label9 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label9);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(72, label7);
methodVisitor.visitFrame(O.F_APPEND,1, [O.LONG], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.LLOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager", "m_157568_", "(J)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label9);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(73, label10);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.LLOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongSet", "add", "(J)Z", true);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(75, label9);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label4);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(76, label5);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager", "f_157499_", "Lit/unimi/dsi/fastutil/longs/LongSet;");
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongSet", "removeAll", "(Lit/unimi/dsi/fastutil/longs/LongCollection;)Z", true);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(79, label1);
var label11 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label11);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(77, label2);
methodVisitor.visitFrame(O.F_FULL, 2, ["org/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager", "org/spongepowered/asm/mixin/injection/callback/CallbackInfo"], 1, ["java/lang/Exception"]);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(78, label12);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V", false);
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(80, label11);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/lang/Object"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel", "()V", false);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(81, label13);
methodVisitor.visitInsn(O.RETURN);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLocalVariable("key", "J", null, label6, label9, 4);
methodVisitor.visitLocalVariable("toRemove", "Lit/unimi/dsi/fastutil/longs/LongSet;", null, label3, label1, 2);
methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label12, label11, 2);
methodVisitor.visitLocalVariable("this", "Lorg/valkyrienskies/mod/mixin/feature/shipyard_entities/MixinPersistentEntitySectionManager;", null, label0, label14, 0);
methodVisitor.visitLocalVariable("ci", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;", null, label0, label14, 1);
methodVisitor.visitMaxs(3, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
