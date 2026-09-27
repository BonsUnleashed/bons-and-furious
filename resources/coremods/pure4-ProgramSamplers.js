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

var BONS_KEY = "oculus_program_traversal"; var BONS_SCRIPT = "pure4-ProgramSamplers.js"; var BONS_TARGET = "net.irisshaders.iris.gl.program.ProgramSamplers";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"net.irisshaders.iris.gl.program.ProgramSamplers"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"update","()V",50);
removeMethod(c,"removeListeners","()V",18);
methodVisitor = new MethodNode(1,"update","()V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(58, label0);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/irisshaders/iris/gl/program/ProgramSamplers", "active", "Lnet/irisshaders/iris/gl/program/ProgramSamplers;");
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(59, label2);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/irisshaders/iris/gl/program/ProgramSamplers", "active", "Lnet/irisshaders/iris/gl/program/ProgramSamplers;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/gl/program/ProgramSamplers", "removeListeners", "()V", false);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(62, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "net/irisshaders/iris/gl/program/ProgramSamplers", "active", "Lnet/irisshaders/iris/gl/program/ProgramSamplers;");
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(64, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/iris/gl/program/ProgramSamplers", "initializer", "Ljava/util/List;");
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(65, label5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/iris/gl/program/ProgramSamplers", "initializer", "Ljava/util/List;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 1);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label7);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/irisshaders/iris/gl/program/GlUniform1iCall");
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(66, label8);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/gl/program/GlUniform1iCall", "location", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/gl/program/GlUniform1iCall", "value", "()I", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/mojang/blaze3d/systems/RenderSystem", "glUniform1i", "(II)V", false);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(67, label9);
methodVisitor.visitJumpInsn(O.GOTO, label6);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(69, label7);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitFieldInsn(O.PUTFIELD, "net/irisshaders/iris/gl/program/ProgramSamplers", "initializer", "Ljava/util/List;");
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(74, label4);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/irisshaders/iris/mixin/GlStateManagerAccessor", "getActiveTexture", "()I", true);
methodVisitor.visitVarInsn(O.ISTORE, 1);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(76, label10);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/iris/gl/program/ProgramSamplers", "samplerBindings", "Lcom/google/common/collect/ImmutableList;");
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/google/common/collect/ImmutableList", "size", "()I", false);
methodVisitor.visitVarInsn(O.ISTORE, 5);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitFrame(O.F_FULL, 6, ["net/irisshaders/iris/gl/program/ProgramSamplers", O.INTEGER, "com/google/common/collect/ImmutableList", O.TOP, O.INTEGER, O.INTEGER], 0, []);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitVarInsn(O.ILOAD, 5);
var label12 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label12);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/google/common/collect/ImmutableList", "get", "(I)Ljava/lang/Object;", false);
methodVisitor.visitIincInsn(4, 1);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/irisshaders/iris/gl/sampler/SamplerBinding");
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(77, label13);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/gl/sampler/SamplerBinding", "update", "()V", false);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(78, label14);
methodVisitor.visitJumpInsn(O.GOTO, label11);
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(80, label12);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitLdcInsn(number('33984', NT.INTEGER));
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/mojang/blaze3d/systems/RenderSystem", "activeTexture", "(I)V", false);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(81, label15);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(2, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(1,"removeListeners","()V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(84, label0);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "net/irisshaders/iris/gl/program/ProgramSamplers", "active", "Lnet/irisshaders/iris/gl/program/ProgramSamplers;");
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(86, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/iris/gl/program/ProgramSamplers", "notifiersToReset", "Lcom/google/common/collect/ImmutableList;");
methodVisitor.visitVarInsn(O.ASTORE, 1);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/google/common/collect/ImmutableList", "size", "()I", false);
methodVisitor.visitVarInsn(O.ISTORE, 4);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_FULL, 5, ["net/irisshaders/iris/gl/program/ProgramSamplers", "com/google/common/collect/ImmutableList", O.TOP, O.INTEGER, O.INTEGER], 0, []);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitVarInsn(O.ILOAD, 4);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/google/common/collect/ImmutableList", "get", "(I)Ljava/lang/Object;", false);
methodVisitor.visitIincInsn(3, 1);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/irisshaders/iris/gl/state/ValueUpdateNotifier");
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(87, label4);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/irisshaders/iris/gl/state/ValueUpdateNotifier", "setListener", "(Ljava/lang/Runnable;)V", true);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(88, label5);
methodVisitor.visitJumpInsn(O.GOTO, label2);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(89, label3);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(2, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
