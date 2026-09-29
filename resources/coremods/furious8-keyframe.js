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

var BONS_KEY = "geckolib_keyframe_locals"; var BONS_SCRIPT = "furious8-keyframe.js"; var BONS_TARGET = "software.bernie.geckolib.core.animation.AnimationController";
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

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"software.bernie.geckolib.core.animation.AnimationController"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getAnimationPointAtTick","(Ljava/util/List;DZLsoftware/bernie/geckolib/core/object/Axis;)Lsoftware/bernie/geckolib/core/keyframe/AnimationPoint;",63);
methodVisitor = new MethodNode(2,"getAnimationPointAtTick","(Ljava/util/List;DZLsoftware/bernie/geckolib/core/object/Axis;)Lsoftware/bernie/geckolib/core/keyframe/AnimationPoint;","(Ljava/util/List<Lsoftware/bernie/geckolib/core/keyframe/Keyframe<Lcom/eliotlash/mclib/math/IValue;>;>;DZLsoftware/bernie/geckolib/core/object/Axis;)Lsoftware/bernie/geckolib/core/keyframe/AnimationPoint;",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(6, label0);
methodVisitor.visitInsn(O.DCONST_0);
methodVisitor.visitVarInsn(O.DSTORE, 6);
methodVisitor.visitVarInsn(O.DLOAD, 2);
methodVisitor.visitVarInsn(O.DSTORE, 8);
methodVisitor.visitInsn(O.ACONST_NULL);
methodVisitor.visitVarInsn(O.ASTORE, 10);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(7, label1);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 11);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_FULL, 9, ["software/bernie/geckolib/core/animation/AnimationController", "java/util/List", O.DOUBLE, O.INTEGER, "software/bernie/geckolib/core/object/Axis", O.DOUBLE, O.DOUBLE, O.NULL, "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 11);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label3);
methodVisitor.visitVarInsn(O.ALOAD, 11);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "software/bernie/geckolib/core/keyframe/Keyframe");
methodVisitor.visitVarInsn(O.ASTORE, 12);
methodVisitor.visitVarInsn(O.DLOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "software/bernie/geckolib/core/keyframe/Keyframe", "length", "()D", false);
methodVisitor.visitInsn(O.DADD);
methodVisitor.visitInsn(O.DUP2);
methodVisitor.visitVarInsn(O.DSTORE, 6);
methodVisitor.visitVarInsn(O.DLOAD, 2);
methodVisitor.visitInsn(O.DCMPL);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFLE, label4);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitVarInsn(O.ASTORE, 10);
methodVisitor.visitVarInsn(O.DLOAD, 2);
methodVisitor.visitVarInsn(O.DLOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 12);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "software/bernie/geckolib/core/keyframe/Keyframe", "length", "()D", false);
methodVisitor.visitInsn(O.DSUB);
methodVisitor.visitInsn(O.DSUB);
methodVisitor.visitVarInsn(O.DSTORE, 8);
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_APPEND,1, ["software/bernie/geckolib/core/keyframe/Keyframe"], 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label2);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(8, label3);
methodVisitor.visitFrame(O.F_FULL, 9, ["software/bernie/geckolib/core/animation/AnimationController", "java/util/List", O.DOUBLE, O.INTEGER, "software/bernie/geckolib/core/object/Axis", O.DOUBLE, O.DOUBLE, "software/bernie/geckolib/core/keyframe/Keyframe", "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 10);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label5);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "size", "()I", true);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.ISUB);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "software/bernie/geckolib/core/keyframe/Keyframe");
methodVisitor.visitVarInsn(O.ASTORE, 10);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(9, label5);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "software/bernie/geckolib/core/keyframe/Keyframe", "startValue", "()Lcom/eliotlash/mclib/math/IValue;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/eliotlash/mclib/math/IValue", "get", "()D", true);
methodVisitor.visitVarInsn(O.DSTORE, 11);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "software/bernie/geckolib/core/keyframe/Keyframe", "endValue", "()Lcom/eliotlash/mclib/math/IValue;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "com/eliotlash/mclib/math/IValue", "get", "()D", true);
methodVisitor.visitVarInsn(O.DSTORE, 13);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(10, label6);
methodVisitor.visitVarInsn(O.ILOAD, 4);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label7);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "software/bernie/geckolib/core/keyframe/Keyframe", "startValue", "()Lcom/eliotlash/mclib/math/IValue;", false);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "com/eliotlash/mclib/math/Constant");
var label8 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label8);
methodVisitor.visitVarInsn(O.DLOAD, 11);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Math", "toRadians", "(D)D", false);
methodVisitor.visitVarInsn(O.DSTORE, 11);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitFieldInsn(O.GETSTATIC, "software/bernie/geckolib/core/object/Axis", "X", "Lsoftware/bernie/geckolib/core/object/Axis;");
var label9 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label9);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitFieldInsn(O.GETSTATIC, "software/bernie/geckolib/core/object/Axis", "Y", "Lsoftware/bernie/geckolib/core/object/Axis;");
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label8);
methodVisitor.visitLabel(label9);
methodVisitor.visitFrame(O.F_FULL, 10, ["software/bernie/geckolib/core/animation/AnimationController", "java/util/List", O.DOUBLE, O.INTEGER, "software/bernie/geckolib/core/object/Axis", O.DOUBLE, O.DOUBLE, "software/bernie/geckolib/core/keyframe/Keyframe", O.DOUBLE, O.DOUBLE], 0, []);
methodVisitor.visitVarInsn(O.DLOAD, 11);
methodVisitor.visitLdcInsn(number('-1.0', NT.DOUBLE));
methodVisitor.visitInsn(O.DMUL);
methodVisitor.visitVarInsn(O.DSTORE, 11);
methodVisitor.visitLabel(label8);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "software/bernie/geckolib/core/keyframe/Keyframe", "endValue", "()Lcom/eliotlash/mclib/math/IValue;", false);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "com/eliotlash/mclib/math/Constant");
methodVisitor.visitJumpInsn(O.IFNE, label7);
methodVisitor.visitVarInsn(O.DLOAD, 13);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Math", "toRadians", "(D)D", false);
methodVisitor.visitVarInsn(O.DSTORE, 13);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitFieldInsn(O.GETSTATIC, "software/bernie/geckolib/core/object/Axis", "X", "Lsoftware/bernie/geckolib/core/object/Axis;");
var label10 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label10);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitFieldInsn(O.GETSTATIC, "software/bernie/geckolib/core/object/Axis", "Y", "Lsoftware/bernie/geckolib/core/object/Axis;");
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label7);
methodVisitor.visitLabel(label10);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.DLOAD, 13);
methodVisitor.visitLdcInsn(number('-1.0', NT.DOUBLE));
methodVisitor.visitInsn(O.DMUL);
methodVisitor.visitVarInsn(O.DSTORE, 13);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(11, label7);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitTypeInsn(O.NEW, "software/bernie/geckolib/core/keyframe/AnimationPoint");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitVarInsn(O.DLOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "software/bernie/geckolib/core/keyframe/Keyframe", "length", "()D", false);
methodVisitor.visitVarInsn(O.DLOAD, 11);
methodVisitor.visitVarInsn(O.DLOAD, 13);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "software/bernie/geckolib/core/keyframe/AnimationPoint", "<init>", "(Lsoftware/bernie/geckolib/core/keyframe/Keyframe;DDDD)V", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(11, 15);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
