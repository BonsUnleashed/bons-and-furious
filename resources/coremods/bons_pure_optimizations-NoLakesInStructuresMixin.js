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

var BONS_KEY = "iceandfire_lake_guard_region"; var BONS_SCRIPT = "bons_pure_optimizations-NoLakesInStructuresMixin.js"; var BONS_TARGET = "com.github.alexthe666.iceandfire.mixin.gen.NoLakesInStructuresMixin";
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

function initializeCoreMod() {return { patch: { target: {type:"CLASS",name:"com.github.alexthe666.iceandfire.mixin.gen.NoLakesInStructuresMixin"}, transformer: function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"iaf_noLakesInMausoleum","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V",56);
methodVisitor = new MethodNode(2,"iaf_noLakesInMausoleum","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V","(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext<Lnet/minecraft/world/level/levelgen/feature/configurations/BlockStateConfiguration;>;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<Ljava/lang/Boolean;>;)V",[]);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z");
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
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(30, label0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "m_159774_", "()Lnet/minecraft/world/level/WorldGenLevel;", false);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/WorldGenRegion");
var label1 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label1);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(31, label2);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(33, label1);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "m_159774_", "()Lnet/minecraft/world/level/WorldGenLevel;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/world/level/WorldGenLevel", "m_9598_", "()Lnet/minecraft/core/RegistryAccess;", true);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/core/registries/Registries", "f_256944_", "Lnet/minecraft/resources/ResourceKey;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/core/RegistryAccess", "m_175515_", "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(34, label3);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "m_159774_", "()Lnet/minecraft/world/level/WorldGenLevel;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/world/level/WorldGenLevel", "m_6018_", "()Lnet/minecraft/server/level/ServerLevel;", true);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "m_215010_", "()Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "m_159774_", "()Lnet/minecraft/world/level/WorldGenLevel;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/WorldGenRegion");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/StructureManager", "m_220468_", "(Lnet/minecraft/server/level/WorldGenRegion;)Lnet/minecraft/world/level/StructureManager;", false);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(35, label4);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/github/alexthe666/iceandfire/datagen/IafStructures", "MAUSOLEUM", "Lnet/minecraft/resources/ResourceKey;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/core/Registry", "m_123009_", "(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", true);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/github/alexthe666/iceandfire/datagen/IafStructures", "GRAVEYARD", "Lnet/minecraft/resources/ResourceKey;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/core/Registry", "m_123009_", "(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", true);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/github/alexthe666/iceandfire/datagen/IafStructures", "GORGON_TEMPLE", "Lnet/minecraft/resources/ResourceKey;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/core/Registry", "m_123009_", "(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;", true);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/util/List", "of", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;", true);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(36, label5);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitFrame(O.F_FULL, 7, ["com/github/alexthe666/iceandfire/mixin/gen/NoLakesInStructuresMixin", "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "net/minecraft/core/Registry", "net/minecraft/world/level/StructureManager", "java/util/List", "java/util/Iterator"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label7);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "java/util/Optional");
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(37, label8);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/Optional", "isPresent", "()Z", false);
var label9 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label9);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/feature/FeaturePlaceContext", "m_159777_", "()Lnet/minecraft/core/BlockPos;", false);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "java/util/Optional", "get", "()Ljava/lang/Object;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/level/levelgen/structure/Structure");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/StructureManager", "m_220494_", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/structure/StructureStart", "m_73603_", "()Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label9);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(38, label10);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "setReturnValue", "(Ljava/lang/Object;)V", false);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(39, label11);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(41, label9);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitJumpInsn(O.GOTO, label6);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(42, label7);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLocalVariable("structure", "Ljava/util/Optional;", "Ljava/util/Optional<Lnet/minecraft/world/level/levelgen/structure/Structure;>;", label8, label9, 7);
methodVisitor.visitLocalVariable("this", "Lcom/github/alexthe666/iceandfire/mixin/gen/NoLakesInStructuresMixin;", null, label0, label12, 0);
methodVisitor.visitLocalVariable("context", "Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;", "Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext<Lnet/minecraft/world/level/levelgen/feature/configurations/BlockStateConfiguration;>;", label0, label12, 1);
methodVisitor.visitLocalVariable("cir", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<Ljava/lang/Boolean;>;", label0, label12, 2);
methodVisitor.visitLocalVariable("configuredStructureFeatureRegistry", "Lnet/minecraft/core/Registry;", "Lnet/minecraft/core/Registry<Lnet/minecraft/world/level/levelgen/structure/Structure;>;", label3, label12, 3);
methodVisitor.visitLocalVariable("structureManager", "Lnet/minecraft/world/level/StructureManager;", null, label4, label12, 4);
methodVisitor.visitLocalVariable("availableStructures", "Ljava/util/List;", "Ljava/util/List<Ljava/util/Optional<Lnet/minecraft/world/level/levelgen/structure/Structure;>;>;", label5, label12, 5);
methodVisitor.visitMaxs(4, 8);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); } }};}
