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

var BONS_KEY = "engineering_vat_growth_reuse"; var BONS_SCRIPT = "furious3-GrowthProgram.js"; var BONS_TARGET = "agentcraft.bioengineering.GrowthProgram";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"agentcraft.bioengineering.GrowthProgram"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"stage","(Lnet/minecraft/resources/ResourceLocation;IIZ)I",40);
removeMethod(c,"<clinit>","()V",28);
methodVisitor = new MethodNode(9,"stage","(Lnet/minecraft/resources/ResourceLocation;IIZ)I",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(6, label0);
methodVisitor.visitVarInsn(O.ILOAD, 3);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label1);
methodVisitor.visitInsn(O.ICONST_5);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 2);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IFGT, label2);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 1);
methodVisitor.visitInsn(O.I2F);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitInsn(O.I2F);
methodVisitor.visitInsn(O.FDIV);
methodVisitor.visitVarInsn(O.FSTORE, 4);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/bioengineering/GrowthProgram", "ac$cuts", "(Lnet/minecraft/resources/ResourceLocation;)[F", false);
methodVisitor.visitVarInsn(O.ASTORE, 6);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitInsn(O.ARRAYLENGTH);
methodVisitor.visitVarInsn(O.ISTORE, 7);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 8);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_FULL, 9, ["net/minecraft/resources/ResourceLocation", O.INTEGER, O.INTEGER, O.INTEGER, O.FLOAT, O.INTEGER, "[F", O.INTEGER, O.INTEGER], 0, []);
methodVisitor.visitVarInsn(O.ILOAD, 8);
methodVisitor.visitVarInsn(O.ILOAD, 7);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label4);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ILOAD, 8);
methodVisitor.visitInsn(O.FALOAD);
methodVisitor.visitVarInsn(O.FSTORE, 9);
methodVisitor.visitVarInsn(O.FLOAD, 4);
methodVisitor.visitVarInsn(O.FLOAD, 9);
methodVisitor.visitInsn(O.FCMPL);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IFLT, label5);
methodVisitor.visitIincInsn(5, 1);
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_APPEND,1, [O.FLOAT], 0, null);
methodVisitor.visitIincInsn(8, 1);
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(2, 10);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(8,"<clinit>","()V",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(4, label0);
methodVisitor.visitIntInsn(O.BIPUSH, 6);
methodVisitor.visitTypeInsn(O.ANEWARRAY, "java/lang/String");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLdcInsn("Cell expansion");
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitLdcInsn("Tissue specialization");
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitLdcInsn("Body formation");
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_3);
methodVisitor.visitLdcInsn("Maturation");
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitLdcInsn("Readiness assessment");
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_5);
methodVisitor.visitLdcInsn("Ready for release");
methodVisitor.visitInsn(O.AASTORE);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "agentcraft/bioengineering/GrowthProgram", "STAGES", "[Ljava/lang/String;");
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitIntInsn(O.NEWARRAY, O.T_FLOAT);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLdcInsn(number('0.16', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitLdcInsn(number('0.34', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitLdcInsn(number('0.66', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_3);
methodVisitor.visitLdcInsn(number('0.94', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "agentcraft/bioengineering/GrowthProgram", "ac$cuts0", "[F");
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitIntInsn(O.NEWARRAY, O.T_FLOAT);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLdcInsn(number('0.15', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitLdcInsn(number('0.44', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitLdcInsn(number('0.78', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_3);
methodVisitor.visitLdcInsn(number('0.95', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "agentcraft/bioengineering/GrowthProgram", "ac$cuts1", "[F");
methodVisitor.visitInsn(O.ICONST_4);
methodVisitor.visitIntInsn(O.NEWARRAY, O.T_FLOAT);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLdcInsn(number('0.18', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitLdcInsn(number('0.4', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_2);
methodVisitor.visitLdcInsn(number('0.74', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitInsn(O.ICONST_3);
methodVisitor.visitLdcInsn(number('0.95', NT.FLOAT));
methodVisitor.visitInsn(O.FASTORE);
methodVisitor.visitFieldInsn(O.PUTSTATIC, "agentcraft/bioengineering/GrowthProgram", "ac$cuts2", "[F");
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitMaxs(4, 0);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(9,"ac$cuts","(Lnet/minecraft/resources/ResourceLocation;)[F",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(5, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNONNULL, label1);
methodVisitor.visitLdcInsn("");
var label2 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label2);
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/resources/ResourceLocation", "m_135815_", "()Ljava/lang/String;", false);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, ["java/lang/String"]);
methodVisitor.visitVarInsn(O.ASTORE, 1);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("wing");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("harrier");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("ray");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label4);
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/lang/String"], 0, null);
methodVisitor.visitFieldInsn(O.GETSTATIC, "agentcraft/bioengineering/GrowthProgram", "ac$cuts0", "[F");
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("back");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label5);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("maw");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label5);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitLdcInsn("duelist");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label6);
methodVisitor.visitLabel(label5);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitFieldInsn(O.GETSTATIC, "agentcraft/bioengineering/GrowthProgram", "ac$cuts1", "[F");
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitFieldInsn(O.GETSTATIC, "agentcraft/bioengineering/GrowthProgram", "ac$cuts2", "[F");
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(2, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
{
fieldVisitor = c.visitField(O.ACC_PRIVATE | O.ACC_FINAL | O.ACC_STATIC, "ac$cuts0", "[F", null, null);
fieldVisitor.visitEnd();
}
{
fieldVisitor = c.visitField(O.ACC_PRIVATE | O.ACC_FINAL | O.ACC_STATIC, "ac$cuts1", "[F", null, null);
fieldVisitor.visitEnd();
}
{
fieldVisitor = c.visitField(O.ACC_PRIVATE | O.ACC_FINAL | O.ACC_STATIC, "ac$cuts2", "[F", null, null);
fieldVisitor.visitEnd();
}
return c;
}); }}};}
