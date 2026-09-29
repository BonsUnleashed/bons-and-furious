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

var BONS_KEY = "ambientsounds_terrain_scan_bound"; var BONS_SCRIPT = "furious7-AmbientHeight.js"; var BONS_TARGET = "team.creative.ambientsounds.environment.TerrainEnvironment";
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

function initializeCoreMod() {return {patch:{target:{type:"CLASS",name:"team.creative.ambientsounds.environment.TerrainEnvironment"},transformer:function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"getHeightBlock","(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos$MutableBlockPos;)I",39);
methodVisitor = new MethodNode(9,"getHeightBlock","(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos$MutableBlockPos;)I",null,[]);
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(21, label0);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(23, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_151558_", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/pure/AcAmbientHeight", "start", "(ILnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos$MutableBlockPos;)I", false);
methodVisitor.visitVarInsn(O.ISTORE, 2);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_APPEND,2, [O.INTEGER, O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_141937_", "()I", false);
var label3 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPLE, label3);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(24, label4);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/core/BlockPos$MutableBlockPos", "m_142448_", "(I)Lnet/minecraft/core/BlockPos$MutableBlockPos;", false);
methodVisitor.visitInsn(O.POP);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(25, label5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_8055_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", false);
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(26, label6);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/block/state/BlockState", "m_60804_", "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z", false);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label7);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/tags/BlockTags", "f_13035_", "Lnet/minecraft/tags/TagKey;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/block/state/BlockState", "m_204336_", "(Lnet/minecraft/tags/TagKey;)Z", false);
methodVisitor.visitJumpInsn(O.IFNE, label7);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_6425_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;", false);
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraft/tags/FluidTags", "f_13131_", "Lnet/minecraft/tags/TagKey;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/material/FluidState", "m_205070_", "(Lnet/minecraft/tags/TagKey;)Z", false);
var label8 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label8);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(27, label7);
methodVisitor.visitFrame(O.F_APPEND,1, ["net/minecraft/world/level/block/state/BlockState"], 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 2);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(28, label9);
methodVisitor.visitJumpInsn(O.GOTO, label3);
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(23, label8);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitIincInsn(2, -1);
methodVisitor.visitJumpInsn(O.GOTO, label2);
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(32, label3);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitInsn(O.IRETURN);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLocalVariable("state", "Lnet/minecraft/world/level/block/state/BlockState;", null, label6, label8, 4);
methodVisitor.visitLocalVariable("level", "Lnet/minecraft/world/level/Level;", null, label0, label10, 0);
methodVisitor.visitLocalVariable("pos", "Lnet/minecraft/core/BlockPos$MutableBlockPos;", null, label0, label10, 1);
methodVisitor.visitLocalVariable("y", "I", null, label2, label10, 2);
methodVisitor.visitLocalVariable("heighest", "I", null, label1, label10, 3);
methodVisitor.visitMaxs(3, 5);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); }}};}
