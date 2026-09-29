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
var BONS_KEY = "terrain_surface_estimate_share"; var BONS_SCRIPT = "furious18-terrain-SurfaceEstimate.js"; var BONS_TARGET = "net.minecraft.world.level.levelgen.NoiseChunk";
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
function bonsVerify(c,name,desc,expected) {
 var m=null;for(var i=0;i<c.methods.size();i++){var v=c.methods.get(i);if(v.name===name&&v.desc===desc){m=v;break;}}
 if(m===null)throw new Error('Missing guarded method '+name+desc);
 var nodes=m.instructions.toArray();
 function label(l){var p=0;for(var k=0;k<nodes.length;k++){if(nodes[k]===l)return p;if(nodes[k].getOpcode()>=0)p++;}throw new Error('Unknown label');}
 var result=[];
 for(var j=0;j<nodes.length;j++) {
  var n=nodes[j],op=n.getOpcode();if(op<0)continue;var s=''+op;
  switch(n.getType()) {
   case 1:s+='|'+n.operand;break;
   case 2:s+='|'+n['var'];break;
   case 3:s+='|'+n.desc;break;
   case 4:s+='|'+n.owner+'|'+n.name+'|'+n.desc;break;
   case 5:s+='|'+n.owner+'|'+n.name+'|'+n.desc+'|'+n.itf;break;
   case 6:s+='|'+n.name+'|'+n.desc+'|'+n.bsm;for(var a=0;a<n.bsmArgs.length;a++)s+='|'+n.bsmArgs[a];break;
   case 7:s+='|'+label(n.label);break;
   case 9:s+='|'+n.cst;break;
   case 10:s+='|'+n['var']+'|'+n.incr;break;
   case 11:s+='|'+n.min+'|'+n.max+'|'+label(n.dflt);for(var a=0;a<n.labels.size();a++)s+='|'+label(n.labels.get(a));break;
   case 12:s+='|'+label(n.dflt);for(var a=0;a<n.keys.size();a++)s+='|'+n.keys.get(a)+':'+label(n.labels.get(a));break;
   case 13:s+='|'+n.desc+'|'+n.dims;break;
  }
  result.push(s);
 }
 for(var i=0;i<m.tryCatchBlocks.size();i++){var t=m.tryCatchBlocks.get(i);result.push('catch|'+label(t.start)+'|'+label(t.end)+'|'+label(t.handler)+'|'+t.type);}
 if(result.join('\n')!==expected)throw new Error('Opcode/operand mismatch '+name+desc);
}

function initializeCoreMod(){return {patch:{target:{type:"CLASS",name:"net.minecraft.world.level.levelgen.NoiseChunk"},transformer:function(c){return bonsGuarded(c,function(c) {
bonsVerify(c,"m_198249_","(J)I","22|1\n184|net/minecraft/server/level/ColumnPos|m_214969_|(J)I|false\n54|3\n22|1\n184|net/minecraft/server/level/ColumnPos|m_214971_|(J)I|false\n54|4\n25|0\n180|net/minecraft/world/level/levelgen/NoiseChunk|f_188717_|Lnet/minecraft/world/level/levelgen/NoiseSettings;\n182|net/minecraft/world/level/levelgen/NoiseSettings|f_158688_|()I|false\n54|5\n21|5\n25|0\n180|net/minecraft/world/level/levelgen/NoiseChunk|f_188717_|Lnet/minecraft/world/level/levelgen/NoiseSettings;\n182|net/minecraft/world/level/levelgen/NoiseSettings|f_64508_|()I|false\n96\n54|6\n21|6\n21|5\n161|39\n25|0\n180|net/minecraft/world/level/levelgen/NoiseChunk|f_209162_|Lnet/minecraft/world/level/levelgen/DensityFunction;\n187|net/minecraft/world/level/levelgen/DensityFunction$SinglePointContext\n89\n21|3\n21|6\n21|4\n183|net/minecraft/world/level/levelgen/DensityFunction$SinglePointContext|<init>|(III)V|false\n185|net/minecraft/world/level/levelgen/DensityFunction|m_207386_|(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D|true\n18|0.390625\n151\n158|33\n21|6\n172\n21|6\n25|0\n180|net/minecraft/world/level/levelgen/NoiseChunk|f_209171_|I\n100\n54|6\n167|16\n18|2147483647\n172");

var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"m_198249_","(J)I",41);
methodVisitor = new MethodNode(2,"m_198249_","(J)I",null,[]);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "bons/pure/terrain/SurfaceEstimateShare", "get", "(Lnet/minecraft/world/level/levelgen/NoiseChunk;J)I", false);
methodVisitor.visitVarInsn(O.ISTORE, 7);
methodVisitor.visitVarInsn(O.ILOAD, 7);
methodVisitor.visitLdcInsn(number('-2147483648', NT.INTEGER));
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPEQ, label0);
methodVisitor.visitVarInsn(O.ILOAD, 7);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(254, label0);
methodVisitor.visitFrame(O.F_APPEND,1, [O.INTEGER], 0, null);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/server/level/ColumnPos", "m_214969_", "(J)I", false);
methodVisitor.visitVarInsn(O.ISTORE, 3);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(255, label1);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/server/level/ColumnPos", "m_214971_", "(J)I", false);
methodVisitor.visitVarInsn(O.ISTORE, 4);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(257, label2);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/level/levelgen/NoiseChunk", "f_188717_", "Lnet/minecraft/world/level/levelgen/NoiseSettings;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/NoiseSettings", "f_158688_", "()I", false);
methodVisitor.visitVarInsn(O.ISTORE, 5);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(259, label3);
methodVisitor.visitVarInsn(O.ILOAD, 5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/level/levelgen/NoiseChunk", "f_188717_", "Lnet/minecraft/world/level/levelgen/NoiseSettings;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/levelgen/NoiseSettings", "f_64508_", "()I", false);
methodVisitor.visitInsn(O.IADD);
methodVisitor.visitVarInsn(O.ISTORE, 6);
var label4 = new Label();
methodVisitor.visitLabel(label4);
methodVisitor.visitFrame(O.F_FULL, 7, ["net/minecraft/world/level/levelgen/NoiseChunk", O.TOP, O.TOP, O.INTEGER, O.INTEGER, O.INTEGER, O.INTEGER], 0, []);
methodVisitor.visitVarInsn(O.ILOAD, 6);
methodVisitor.visitVarInsn(O.ILOAD, 5);
var label5 = new Label();
methodVisitor.visitJumpInsn(O.IF_ICMPLT, label5);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(260, label6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/level/levelgen/NoiseChunk", "f_209162_", "Lnet/minecraft/world/level/levelgen/DensityFunction;");
methodVisitor.visitTypeInsn(O.NEW, "net/minecraft/world/level/levelgen/DensityFunction$SinglePointContext");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ILOAD, 3);
methodVisitor.visitVarInsn(O.ILOAD, 6);
methodVisitor.visitVarInsn(O.ILOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/world/level/levelgen/DensityFunction$SinglePointContext", "<init>", "(III)V", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/world/level/levelgen/DensityFunction", "m_207386_", "(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D", true);
methodVisitor.visitLdcInsn(number('0.390625', NT.DOUBLE));
methodVisitor.visitInsn(O.DCMPL);
var label7 = new Label();
methodVisitor.visitJumpInsn(O.IFLE, label7);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(261, label8);
methodVisitor.visitVarInsn(O.ILOAD, 6);
methodVisitor.visitVarInsn(O.ISTORE, 7);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "bons/pure/terrain/SurfaceEstimateShare", "put", "(Lnet/minecraft/world/level/levelgen/NoiseChunk;JI)I", false);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(259, label7);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ILOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraft/world/level/levelgen/NoiseChunk", "f_209171_", "I");
methodVisitor.visitInsn(O.ISUB);
methodVisitor.visitVarInsn(O.ISTORE, 6);
methodVisitor.visitJumpInsn(O.GOTO, label4);
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(265, label5);
methodVisitor.visitFrame(O.F_FULL, 0, [], 0, []);
methodVisitor.visitLdcInsn(number('2147483647', NT.INTEGER));
methodVisitor.visitVarInsn(O.ISTORE, 7);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.LLOAD, 1);
methodVisitor.visitVarInsn(O.ILOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "bons/pure/terrain/SurfaceEstimateShare", "put", "(Lnet/minecraft/world/level/levelgen/NoiseChunk;JI)I", false);
methodVisitor.visitInsn(O.IRETURN);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLocalVariable("this", "Lnet/minecraft/world/level/levelgen/NoiseChunk;", null, label0, label9, 0);
methodVisitor.visitLocalVariable("p_198250_", "J", null, label0, label9, 1);
methodVisitor.visitLocalVariable("$$1", "I", null, label1, label9, 3);
methodVisitor.visitLocalVariable("$$2", "I", null, label2, label9, 4);
methodVisitor.visitLocalVariable("$$3", "I", null, label3, label9, 5);
methodVisitor.visitLocalVariable("$$4", "I", null, label4, label5, 6);
methodVisitor.visitMaxs(6, 8);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
});}}};}
