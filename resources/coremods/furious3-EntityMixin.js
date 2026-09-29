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

var BONS_KEY = "ryoamiclights_chunk_iteration"; var BONS_SCRIPT = "furious3-EntityMixin.js"; var BONS_TARGET = "org.thinkingstudio.ryoamiclights.mixin.lightsource.EntityMixin";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"org.thinkingstudio.ryoamiclights.mixin.lightsource.EntityMixin"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"ryoamiclights$scheduleTrackedChunksRebuild","(Lnet/minecraft/client/renderer/LevelRenderer;)V",22);
methodVisitor = new MethodNode(1,"ryoamiclights$scheduleTrackedChunksRebuild","(Lnet/minecraft/client/renderer/LevelRenderer;)V",null,[]);
methodVisitor.visitParameter("renderer", 0);
{
annotationVisitor0 = methodVisitor.visitTypeAnnotation(369098752, null, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(1, false);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(211, label0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/client/Minecraft", "m_91087_", "()Lnet/minecraft/client/Minecraft;", false);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/client/Minecraft", "f_91073_", "Lnet/minecraft/client/multiplayer/ClientLevel;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/thinkingstudio/ryoamiclights/mixin/lightsource/EntityMixin", "f_19853_", "Lnet/minecraft/world/level/Level;");
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPNE, label1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(212, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "org/thinkingstudio/ryoamiclights/mixin/lightsource/EntityMixin", "ryoamiclights$trackedLitChunkPos", "Lit/unimi/dsi/fastutil/longs/LongOpenHashSet;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "it/unimi/dsi/fastutil/longs/LongOpenHashSet", "iterator", "()Lit/unimi/dsi/fastutil/longs/LongIterator;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitFrame(O.F_APPEND,1, ["it/unimi/dsi/fastutil/longs/LongIterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongIterator", "hasNext", "()Z", true);
methodVisitor.visitJumpInsn(O.IFEQ, label1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "it/unimi/dsi/fastutil/longs/LongIterator", "nextLong", "()J", true);
methodVisitor.visitVarInsn(O.LSTORE, 3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(213, label4);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.LLOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "org/thinkingstudio/ryoamiclights/RyoamicLights", "scheduleChunkRebuild", "(Lnet/minecraft/client/renderer/LevelRenderer;J)V", false);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(214, label5);
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(215, label1);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLocalVariable("pos", "J", null, label4, label5, 3);
methodVisitor.visitLocalVariable("this", "Lorg/thinkingstudio/ryoamiclights/mixin/lightsource/EntityMixin;", null, label0, label6, 0);
methodVisitor.visitLocalVariable("renderer", "Lnet/minecraft/client/renderer/LevelRenderer;", null, label0, label6, 1);
methodVisitor.visitMaxs(3, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
