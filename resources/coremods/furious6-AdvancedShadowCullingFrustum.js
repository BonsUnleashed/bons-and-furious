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

var BONS_KEY = "oculus_shadow_edge_vectors"; var BONS_SCRIPT = "furious6-AdvancedShadowCullingFrustum.js"; var BONS_TARGET = "net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"addEdgePlane","(Lorg/joml/Vector4f;Lorg/joml/Vector4f;)V",71);
methodVisitor = new MethodNode(2,"addEdgePlane","(Lorg/joml/Vector4f;Lorg/joml/Vector4f;)V",null,[]);
methodVisitor.visitParameter("backPlane4", 0);
methodVisitor.visitParameter("frontPlane4", 0);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(180, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "truncate", "(Lorg/joml/Vector4f;)Lorg/joml/Vector3f;", false);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(181, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "truncate", "(Lorg/joml/Vector4f;)Lorg/joml/Vector3f;", false);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(184, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "cross", "(Lorg/joml/Vector3f;Lorg/joml/Vector3f;)Lorg/joml/Vector3f;", false);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(189, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "shadowLightVectorFromOrigin", "Lorg/joml/Vector3f;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "cross", "(Lorg/joml/Vector3f;Lorg/joml/Vector3f;)Lorg/joml/Vector3f;", false);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(217, label4);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3f", "cross", "(Lorg/joml/Vector3fc;Lorg/joml/Vector3f;)Lorg/joml/Vector3f;", false);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(218, label5);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3f", "cross", "(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;", false);
methodVisitor.visitVarInsn(O.ASTORE, 9);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(220, label6);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector4f", "w", "()F", false);
methodVisitor.visitInsn(O.FNEG);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3f", "mul", "(F)Lorg/joml/Vector3f;", false);
methodVisitor.visitInsn(O.POP);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(221, label7);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector4f", "w", "()F", false);
methodVisitor.visitInsn(O.FNEG);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3f", "mul", "(F)Lorg/joml/Vector3f;", false);
methodVisitor.visitInsn(O.POP);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(223, label8);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 9);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3f", "add", "(Lorg/joml/Vector3fc;)Lorg/joml/Vector3f;", false);
methodVisitor.visitInsn(O.POP);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(225, label9);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(226, label10);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitInsn(O.FCONST_1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "lengthSquared", "(Lorg/joml/Vector3f;)F", false);
methodVisitor.visitInsn(O.FDIV);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3f", "mul", "(F)Lorg/joml/Vector3f;", false);
methodVisitor.visitInsn(O.POP);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(239, label11);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/joml/Vector3f", "dot", "(Lorg/joml/Vector3fc;)F", false);
methodVisitor.visitVarInsn(O.FSTORE, 9);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(240, label12);
methodVisitor.visitVarInsn(O.FLOAD, 9);
methodVisitor.visitInsn(O.FNEG);
methodVisitor.visitVarInsn(O.FSTORE, 10);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(242, label13);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.FLOAD, 10);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "extend", "(Lorg/joml/Vector3f;F)Lorg/joml/Vector4f;", false);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(265, label14);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/irisshaders/iris/shadows/frustum/advanced/AdvancedShadowCullingFrustum", "addPlane", "(Lorg/joml/Vector4f;)V", false);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(266, label15);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(4, 11);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
