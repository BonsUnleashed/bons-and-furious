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

var BONS_KEY = "structure_gel_lake_guard_region"; var BONS_SCRIPT = "bons_and_furious-LakeFeatureHooks.js"; var BONS_TARGET = "com.legacy.structure_gel.core.asm_hooks.LakeFeatureHooks";
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

function initializeCoreMod() {return { patch: { target: {type:"CLASS",name:"com.legacy.structure_gel.core.asm_hooks.LakeFeatureHooks"}, transformer: function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"checkForStructures","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",18);
methodVisitor = new MethodNode(9,"checkForStructures","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext<*>;)Z",[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(17, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "m_159774_", "()Lnet/minecraft/world/level/WorldGenLevel;", false);
methodVisitor.visitVarInsn(O.ASTORE, 1);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(18, label1);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/world/level/WorldGenLevel", "m_6018_", "()Lnet/minecraft/server/level/ServerLevel;", true);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_215010_", "()Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(19, label2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/WorldGenRegion");
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/WorldGenRegion");
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(20, label4);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/StructureManager", "m_220468_", "(Lnet/minecraft/server/level/WorldGenRegion;)Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(22, label3);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/world/level/WorldGenLevel", "net/minecraft/world/level/StructureManager"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitTypeInsn(O.NEW, "net/minecraft/world/level/ChunkPos");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "m_159777_", "()Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/world/level/ChunkPos", "<init>", "(Lnet/minecraft/core/BlockPos;)V", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitInvokeDynamicInsn("test", "(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Ljava/util/function/Predicate;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Z"), new Handle(O.H_INVOKESTATIC, "com/legacy/structure_gel/core/asm_hooks/LakeFeatureHooks", "lambda$checkForStructures$0", "(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;Lnet/minecraft/world/level/levelgen/structure/Structure;)Z", false), Type.getType("(Lnet/minecraft/world/level/levelgen/structure/Structure;)Z")]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/StructureManager", "m_220477_", "(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;", false);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(24, label5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label6);
methodVisitor.visitInsn(O.ICONST_1);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.GOTO, label7);
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(22, label7);
methodVisitor.visitFrame(O.F_SAME1, 0, null, 1, [O.INTEGER]);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(4, 4);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); } }};}
