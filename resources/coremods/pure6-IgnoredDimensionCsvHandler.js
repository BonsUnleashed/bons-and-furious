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

var BONS_KEY = "distanthorizons_ignored_dimension_match"; var BONS_SCRIPT = "pure6-IgnoredDimensionCsvHandler.js"; var BONS_TARGET = "com.seibel.distanthorizons.core.config.eventHandlers.IgnoredDimensionCsvHandler";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"com.seibel.distanthorizons.core.config.eventHandlers.IgnoredDimensionCsvHandler"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"dimensionNameShouldBeIgnored","(Ljava/lang/String;)Z",43);
methodVisitor = new MethodNode(1,"dimensionNameShouldBeIgnored","(Ljava/lang/String;)Z",null,[]);
methodVisitor.visitParameter("dimName", 0);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(4, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/seibel/distanthorizons/core/config/eventHandlers/IgnoredDimensionCsvHandler", "dimensionNames", "[Ljava/lang/String;");
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/seibel/distanthorizons/core/config/eventHandlers/IgnoredDimensionCsvHandler", "dimensionNames", "[Ljava/lang/String;");
methodVisitor.visitInsn(O.ARRAYLENGTH);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label2);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(5, label2);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitIntInsn(O.BIPUSH, 64);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I", false);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitVarInsn(O.ISTORE, 2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitInsn(O.ISUB);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(6, label3);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_APPEND,3, [O.INTEGER, O.INTEGER, O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/seibel/distanthorizons/core/config/eventHandlers/IgnoredDimensionCsvHandler", "dimensionNames", "[Ljava/lang/String;");
methodVisitor.visitInsn(O.ARRAYLENGTH);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label5);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(7, label6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/seibel/distanthorizons/core/config/eventHandlers/IgnoredDimensionCsvHandler", "dimensionNames", "[Ljava/lang/String;");
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitInsn(O.AALOAD);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(8, label7);
methodVisitor.visitVarInsn(O.ALOAD, 5);
var label8 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label8);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitJumpInsn(O.IF_ICMPNE, label8);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "regionMatches", "(ZILjava/lang/String;II)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label8);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(6, label8);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/lang/String"], 0, null);
methodVisitor.visitIincInsn(4, 1);
methodVisitor.visitJumpInsn(O.GOTO, label4);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(10, label5);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(6, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
