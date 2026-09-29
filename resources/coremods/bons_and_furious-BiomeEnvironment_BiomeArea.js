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

var BONS_KEY = "ambientsounds_biome_match_cache"; var BONS_SCRIPT = "bons_and_furious-BiomeEnvironment_BiomeArea.js"; var BONS_TARGET = "team.creative.ambientsounds.environment.BiomeEnvironment$BiomeArea";
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

function initializeCoreMod() {return { patch: { target: {type:"CLASS",name:"team.creative.ambientsounds.environment.BiomeEnvironment$BiomeArea"}, transformer: function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"checkBiome","([Lteam/creative/ambientsounds/condition/BiomeCondition;)Z",40);
removeMethod(c,"lambda$checkBiome$0","(Lteam/creative/ambientsounds/condition/BiomeCondition;Lnet/minecraft/tags/TagKey;)Z",8);
methodVisitor = new MethodNode(1,"checkBiome","([Lteam/creative/ambientsounds/condition/BiomeCondition;)Z",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(85, label0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitInsn(O.ARRAYLENGTH);
methodVisitor.visitVarInsn(O.ISTORE, 3);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitFrame(O.F_APPEND,3, ["[Lteam/creative/ambientsounds/condition/BiomeCondition;", O.INTEGER, O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitVarInsn(O.ILOAD, 3);
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPGE, label2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitInsn(O.AALOAD);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(86, label3);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "team/creative/ambientsounds/condition/BiomeCondition", "tag", "()Z", false);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(87, label5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "team/creative/ambientsounds/environment/BiomeEnvironment$BiomeArea", "biome", "Lnet/minecraft/core/Holder;");
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/core/Holder", "m_203616_", "()Ljava/util/stream/Stream;", true);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitInvokeDynamicInsn("test", "(Lteam/creative/ambientsounds/condition/BiomeCondition;)Ljava/util/function/Predicate;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Z"), new Handle(O.H_INVOKESTATIC, "team/creative/ambientsounds/environment/BiomeEnvironment$BiomeArea", "lambda$checkBiome$0", "(Lteam/creative/ambientsounds/condition/BiomeCondition;Lnet/minecraft/tags/TagKey;)Z", false), Type.getType("(Lnet/minecraft/tags/TagKey;)Z")]);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "anyMatch", "(Ljava/util/function/Predicate;)Z", true);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label6);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(88, label7);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(89, label4);
methodVisitor.visitFrame(O.F_APPEND,1, ["team/creative/ambientsounds/condition/BiomeCondition"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "team/creative/ambientsounds/condition/BiomeCondition", "pattern", "()Ljava/util/regex/Pattern;", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "team/creative/ambientsounds/environment/BiomeEnvironment$BiomeArea", "location", "Lnet/minecraft/resources/ResourceLocation;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_pure_optimizations/team/creative/ambientsounds/condition/AcBiomeMatchCache", "matches", "(Ljava/util/regex/Pattern;Lnet/minecraft/resources/ResourceLocation;)Z", false);
methodVisitor.visitJumpInsn(O.IFEQ, label6);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(90, label8);
methodVisitor.visitInsn(O.ICONST_1);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(85, label6);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitIincInsn(4, 1);
methodVisitor.visitJumpInsn(O.GOTO, label1);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(92, label2);
methodVisitor.visitFrame(O.F_CHOP,3, null, 0, null);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitInsn(O.IRETURN);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLocalVariable("condition", "Lteam/creative/ambientsounds/condition/BiomeCondition;", null, label3, label6, 5);
methodVisitor.visitLocalVariable("this", "Lteam/creative/ambientsounds/environment/BiomeEnvironment$BiomeArea;", null, label0, label9, 0);
methodVisitor.visitLocalVariable("conditions", "[Lteam/creative/ambientsounds/condition/BiomeCondition;", null, label0, label9, 1);
methodVisitor.visitMaxs(2, 6);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(4106,"lambda$checkBiome$0","(Lteam/creative/ambientsounds/condition/BiomeCondition;Lnet/minecraft/tags/TagKey;)Z",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(87, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "team/creative/ambientsounds/condition/BiomeCondition", "pattern", "()Ljava/util/regex/Pattern;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/tags/TagKey", "f_203868_", "()Lnet/minecraft/resources/ResourceLocation;", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_pure_optimizations/team/creative/ambientsounds/condition/AcBiomeMatchCache", "matches", "(Ljava/util/regex/Pattern;Lnet/minecraft/resources/ResourceLocation;)Z", false);
methodVisitor.visitInsn(O.IRETURN);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLocalVariable("condition", "Lteam/creative/ambientsounds/condition/BiomeCondition;", null, label0, label1, 0);
methodVisitor.visitLocalVariable("x", "Lnet/minecraft/tags/TagKey;", null, label0, label1, 1);
methodVisitor.visitMaxs(2, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); } }};}
