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

var BONS_KEY = "presencefootsteps_duplicate_tracking"; var BONS_SCRIPT = "pure6-SoundEngine.js"; var BONS_TARGET = "eu.ha3.presencefootsteps.sound.SoundEngine";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"eu.ha3.presencefootsteps.sound.SoundEngine"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getTargets","(Lnet/minecraft/world/entity/Entity;)Ljava/util/stream/Stream;",40);
removeMethod(c,"lambda$getTargets$3","(Lnet/minecraft/world/entity/Entity;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;)Z",33);
methodVisitor = new MethodNode(2,"getTargets","(Lnet/minecraft/world/entity/Entity;)Ljava/util/stream/Stream;","(Lnet/minecraft/world/entity/Entity;)Ljava/util/stream/Stream<+Lnet/minecraft/world/entity/Entity;>;",[]);
methodVisitor.visitParameter("cameraEntity", 0);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(98, label0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/entity/Entity");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_20191_", "()Lnet/minecraft/world/phys/AABB;", false);
methodVisitor.visitLdcInsn(number('16.0', NT.DOUBLE));
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/phys/AABB", "m_82400_", "(D)Lnet/minecraft/world/phys/AABB;", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInvokeDynamicInsn("test", "(Leu/ha3/presencefootsteps/sound/SoundEngine;Lnet/minecraft/world/entity/Entity;)Ljava/util/function/Predicate;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Z"), new Handle(O.H_INVOKEVIRTUAL, "eu/ha3/presencefootsteps/sound/SoundEngine", "lambda$getTargets$1", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Z", false), Type.getType("(Lnet/minecraft/world/entity/Entity;)Z")]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_6249_", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(114, label1);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInvokeDynamicInsn("applyAsDouble", "(Lnet/minecraft/world/entity/Entity;)Ljava/util/function/ToDoubleFunction;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)D"), new Handle(O.H_INVOKESTATIC, "eu/ha3/presencefootsteps/sound/SoundEngine", "lambda$getTargets$2", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)D", false), Type.getType("(Lnet/minecraft/world/entity/Entity;)D")]);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/util/Comparator", "comparingDouble", "(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(116, label2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "eu/ha3/presencefootsteps/sound/SoundEngine", "config", "Leu/ha3/presencefootsteps/PFConfig;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "eu/ha3/presencefootsteps/PFConfig", "getMaxSteppingEntities", "()I", false);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(117, label4);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "stream", "()Ljava/util/stream/Stream;", true);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(119, label3);
methodVisitor.visitFrame(O.F_APPEND,2, ["java/util/List", "java/util/Comparator"], 0, null);
methodVisitor.visitTypeInsn(O.NEW, "agentcraft/pure/AcFootstepSet");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "agentcraft/pure/AcFootstepSet", "<init>", "()V", false);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(120, label5);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "stream", "()Ljava/util/stream/Stream;", true);
methodVisitor.visitVarInsn(O.ALOAD, 3);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(121, label6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "sorted", "(Ljava/util/Comparator;)Ljava/util/stream/Stream;", true);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInvokeDynamicInsn("test", "(Leu/ha3/presencefootsteps/sound/SoundEngine;Lnet/minecraft/world/entity/Entity;Ljava/util/Set;)Ljava/util/function/Predicate;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Z"), new Handle(O.H_INVOKEVIRTUAL, "eu/ha3/presencefootsteps/sound/SoundEngine", "lambda$getTargets$3", "(Lnet/minecraft/world/entity/Entity;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;)Z", false), Type.getType("(Lnet/minecraft/world/entity/Entity;)Z")]);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(124, label7);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "filter", "(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", true);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(120, label8);
methodVisitor.visitInsn(O.ARETURN);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLocalVariable("this", "Leu/ha3/presencefootsteps/sound/SoundEngine;", null, label0, label9, 0);
methodVisitor.visitLocalVariable("cameraEntity", "Lnet/minecraft/world/entity/Entity;", null, label0, label9, 1);
methodVisitor.visitLocalVariable("entities", "Ljava/util/List;", "Ljava/util/List<+Lnet/minecraft/world/entity/Entity;>;", label1, label9, 2);
methodVisitor.visitLocalVariable("nearest", "Ljava/util/Comparator;", "Ljava/util/Comparator<Lnet/minecraft/world/entity/Entity;>;", label2, label9, 3);
methodVisitor.visitLocalVariable("alreadyVisited", "Ljava/util/Set;", "Ljava/util/Set<Ljava/lang/Integer;>;", label5, label9, 4);
methodVisitor.visitMaxs(5, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(4098,"lambda$getTargets$3","(Lnet/minecraft/world/entity/Entity;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;)Z",null,[]);
methodVisitor.visitParameter("cameraEntity", 0);
methodVisitor.visitParameter("alreadyVisited", 0);
methodVisitor.visitParameter("e", 0);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(124, label0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label1);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/world/entity/player/Player");
methodVisitor.visitJumpInsn(O.IFNE, label1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Set", "size", "()I", true);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "eu/ha3/presencefootsteps/sound/SoundEngine", "config", "Leu/ha3/presencefootsteps/PFConfig;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "eu/ha3/presencefootsteps/PFConfig", "getMaxSteppingEntities", "()I", false);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_6095_", "()Lnet/minecraft/world/entity/EntityType;", false);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_20183_", "()Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/pure/AcFootstepSet", "addPair", "(Ljava/util/Set;Ljava/lang/Object;Ljava/lang/Object;)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label2);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_1);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(3, 4);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
