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

var BONS_KEY = "engineering_industry_recipe_index"; var BONS_SCRIPT = "pure3-BiologyProcess.js"; var BONS_TARGET = "agentcraft.bioengineering.BiologyProcess";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"agentcraft.bioengineering.BiologyProcess"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"list","(Lnet/minecraft/world/level/Level;Lagentcraft/bioengineering/Industry$Kind;)Ljava/util/List;",14);
methodVisitor = new MethodNode(9,"list","(Lnet/minecraft/world/level/Level;Lagentcraft/bioengineering/Industry$Kind;)Ljava/util/List;","(Lnet/minecraft/world/level/Level;Lagentcraft/bioengineering/Industry$Kind;)Ljava/util/List<Lagentcraft/bioengineering/BiologyProcess;>;",[]);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_7465_", "()Lnet/minecraft/world/item/crafting/RecipeManager;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/pure/more/IndustryLists", "tryList", "(Lnet/minecraft/world/item/crafting/RecipeManager;Lagentcraft/bioengineering/Industry$Kind;)Ljava/util/List;", false);
methodVisitor.visitInsn(O.DUP);
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFNULL, label0);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_FULL, 3, ["net/minecraft/world/level/Level", "agentcraft/bioengineering/Industry$Kind", "net/minecraft/world/item/crafting/RecipeManager"], 1, ["java/util/List"]);
methodVisitor.visitInsn(O.POP);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/bioengineering/BiologyProcess", "ac$fallbackList", "(Lnet/minecraft/world/item/crafting/RecipeManager;Lagentcraft/bioengineering/Industry$Kind;)Ljava/util/List;", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(2, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"ac$fallbackList","(Lnet/minecraft/world/item/crafting/RecipeManager;Lagentcraft/bioengineering/Industry$Kind;)Ljava/util/List;",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(28, label0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/item/crafting/RecipeManager", "m_44051_", "()Ljava/util/Collection;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Collection", "stream", "()Ljava/util/stream/Stream;", true);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInvokeDynamicInsn("test", "(Lagentcraft/bioengineering/Industry$Kind;)Ljava/util/function/Predicate;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Z"), new Handle(O.H_INVOKESTATIC, "agentcraft/bioengineering/BiologyProcess", "lambda$list$0", "(Lagentcraft/bioengineering/Industry$Kind;Lnet/minecraft/world/item/crafting/Recipe;)Z", false), Type.getType("(Lnet/minecraft/world/item/crafting/Recipe;)Z")]);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "filter", "(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", true);
methodVisitor.visitInvokeDynamicInsn("apply", "()Ljava/util/function/Function;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"), new Handle(O.H_INVOKESTATIC, "agentcraft/bioengineering/BiologyProcess", "lambda$list$1", "(Lnet/minecraft/world/item/crafting/Recipe;)Ljava/lang/String;", false), Type.getType("(Lnet/minecraft/world/item/crafting/Recipe;)Ljava/lang/String;")]);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(30, label1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/util/Comparator", "comparing", "(Ljava/util/function/Function;)Ljava/util/Comparator;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "sorted", "(Ljava/util/Comparator;)Ljava/util/stream/Stream;", true);
methodVisitor.visitInvokeDynamicInsn("apply", "()Ljava/util/function/Function;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"), new Handle(O.H_NEWINVOKESPECIAL, "agentcraft/bioengineering/BiologyProcess", "<init>", "(Lnet/minecraft/world/item/crafting/Recipe;)V", false), Type.getType("(Lnet/minecraft/world/item/crafting/Recipe;)Lagentcraft/bioengineering/BiologyProcess;")]);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "map", "(Ljava/util/function/Function;)Ljava/util/stream/Stream;", true);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/stream/Stream", "toList", "()Ljava/util/List;", true);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(28, label2);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(2, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
