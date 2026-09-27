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

var BONS_KEY = "tacz_sync_collections"; var BONS_SCRIPT = "bons_pure_optimizations-SyncedEntityDataEvent.js"; var BONS_TARGET = "com.tacz.guns.event.SyncedEntityDataEvent";
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

function initializeCoreMod() {return { patch: { target: {type:"CLASS",name:"com.tacz.guns.event.SyncedEntityDataEvent"}, transformer: function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"onServerTick","(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V",111);
methodVisitor = new MethodNode(9,"onServerTick","(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V",null,[]);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lnet/minecraftforge/eventbus/api/SubscribeEvent;", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(91, label0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/tacz/guns/entity/sync/core/SyncedEntityData", "instance", "()Lcom/tacz/guns/entity/sync/core/SyncedEntityData;", false);
methodVisitor.visitVarInsn(O.ASTORE, 1);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(92, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$ServerTickEvent", "side", "Lnet/minecraftforge/fml/LogicalSide;");
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraftforge/fml/LogicalSide", "SERVER", "Lnet/minecraftforge/fml/LogicalSide;");
var label2 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label2);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(93, label3);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(95, label2);
methodVisitor.visitFrame(O.F_APPEND,1, ["com/tacz/guns/entity/sync/core/SyncedEntityData"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "net/minecraftforge/event/TickEvent$ServerTickEvent", "phase", "Lnet/minecraftforge/event/TickEvent$Phase;");
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraftforge/event/TickEvent$Phase", "END", "Lnet/minecraftforge/event/TickEvent$Phase;");
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IF_ACMPEQ, label4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(96, label5);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(98, label4);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedEntityData", "isDirty", "()Z", false);
var label6 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label6);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(99, label7);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(101, label6);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedEntityData", "getDirtyEntities", "()Ljava/util/List;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(102, label8);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
var label9 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label9);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(103, label10);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedEntityData", "setDirty", "(Z)V", false);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(104, label11);
methodVisitor.visitInsn(O.RETURN);
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(106, label9);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/List"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
methodVisitor.visitVarInsn(O.ASTORE, 3);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/Iterator"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
var label13 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label13);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/entity/Entity");
methodVisitor.visitVarInsn(O.ASTORE, 4);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(107, label14);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedEntityData", "getDataHolder", "(Lnet/minecraft/world/entity/Entity;)Lcom/tacz/guns/entity/sync/core/DataHolder;", false);
methodVisitor.visitVarInsn(O.ASTORE, 5);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(108, label15);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitJumpInsn(O.IFNULL, label12);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/DataHolder", "isDirty", "()Z", false);
var label16 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label16);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(109, label17);
methodVisitor.visitJumpInsn(O.GOTO, label12);
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(111, label16);
methodVisitor.visitFrame(O.F_APPEND,2, ["net/minecraft/world/entity/Entity", "com/tacz/guns/entity/sync/core/DataHolder"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/DataHolder", "gatherDirty", "()Ljava/util/List;", false);
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(112, label18);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
var label19 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label19);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(113, label20);
methodVisitor.visitJumpInsn(O.GOTO, label12);
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(115, label19);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/List"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_pure_optimizations/com/tacz/guns/entity/sync/core/AcCollections", "self", "(Ljava/util/List;)Ljava/util/List;", false);
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(116, label21);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
var label22 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label22);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/ServerPlayer");
methodVisitor.visitJumpInsn(O.IFEQ, label22);
var label23 = new Label();
methodVisitor.visitLabel(label23);
methodVisitor.visitLineNumber(117, label23);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/network/NetworkHandler", "CHANNEL", "Lnet/minecraftforge/network/simple/SimpleChannel;");
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraftforge/network/PacketDistributor", "PLAYER", "Lnet/minecraftforge/network/PacketDistributor;");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInvokeDynamicInsn("get", "(Lnet/minecraft/world/entity/Entity;)Ljava/util/function/Supplier;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("()Ljava/lang/Object;"), new Handle(O.H_INVOKESTATIC, "com/tacz/guns/event/SyncedEntityDataEvent", "lambda$onServerTick$5", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/server/level/ServerPlayer;", false), Type.getType("()Lnet/minecraft/server/level/ServerPlayer;")]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/network/PacketDistributor", "with", "(Ljava/util/function/Supplier;)Lnet/minecraftforge/network/PacketDistributor$PacketTarget;", false);
methodVisitor.visitTypeInsn(O.NEW, "com/tacz/guns/network/message/ServerMessageUpdateEntityData");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_19879_", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "com/tacz/guns/network/message/ServerMessageUpdateEntityData", "<init>", "(ILjava/util/List;)V", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/network/simple/SimpleChannel", "send", "(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", false);
methodVisitor.visitLabel(label22);
methodVisitor.visitLineNumber(119, label22);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/List"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/consolidated/bons_pure_optimizations/com/tacz/guns/entity/sync/core/AcCollections", "tracking", "(Ljava/util/List;)Ljava/util/List;", false);
methodVisitor.visitVarInsn(O.ASTORE, 8);
var label24 = new Label();
methodVisitor.visitLabel(label24);
methodVisitor.visitLineNumber(120, label24);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "isEmpty", "()Z", true);
var label25 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label25);
var label26 = new Label();
methodVisitor.visitLabel(label26);
methodVisitor.visitLineNumber(121, label26);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/network/NetworkHandler", "CHANNEL", "Lnet/minecraftforge/network/simple/SimpleChannel;");
methodVisitor.visitFieldInsn(O.GETSTATIC, "net/minecraftforge/network/PacketDistributor", "TRACKING_ENTITY", "Lnet/minecraftforge/network/PacketDistributor;");
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitInvokeDynamicInsn("get", "(Lnet/minecraft/world/entity/Entity;)Ljava/util/function/Supplier;", new Handle(O.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false), [Type.getType("()Ljava/lang/Object;"), new Handle(O.H_INVOKESTATIC, "com/tacz/guns/event/SyncedEntityDataEvent", "lambda$onServerTick$7", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/Entity;", false), Type.getType("()Lnet/minecraft/world/entity/Entity;")]);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/network/PacketDistributor", "with", "(Ljava/util/function/Supplier;)Lnet/minecraftforge/network/PacketDistributor$PacketTarget;", false);
methodVisitor.visitTypeInsn(O.NEW, "com/tacz/guns/network/message/ServerMessageUpdateEntityData");
methodVisitor.visitInsn(O.DUP);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "m_19879_", "()I", false);
methodVisitor.visitVarInsn(O.ALOAD, 8);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "com/tacz/guns/network/message/ServerMessageUpdateEntityData", "<init>", "(ILjava/util/List;)V", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/network/simple/SimpleChannel", "send", "(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V", false);
methodVisitor.visitLabel(label25);
methodVisitor.visitLineNumber(123, label25);
methodVisitor.visitFrame(O.F_APPEND,1, ["java/util/List"], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/DataHolder", "clean", "()V", false);
var label27 = new Label();
methodVisitor.visitLabel(label27);
methodVisitor.visitLineNumber(124, label27);
methodVisitor.visitJumpInsn(O.GOTO, label12);
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(125, label13);
methodVisitor.visitFrame(O.F_FULL, 3, ["net/minecraftforge/event/TickEvent$ServerTickEvent", "com/tacz/guns/entity/sync/core/SyncedEntityData", "java/util/List"], 0, []);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "java/util/List", "clear", "()V", true);
var label28 = new Label();
methodVisitor.visitLabel(label28);
methodVisitor.visitLineNumber(126, label28);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedEntityData", "setDirty", "(Z)V", false);
var label29 = new Label();
methodVisitor.visitLabel(label29);
methodVisitor.visitLineNumber(127, label29);
methodVisitor.visitInsn(O.RETURN);
var label30 = new Label();
methodVisitor.visitLabel(label30);
methodVisitor.visitLocalVariable("holder", "Lcom/tacz/guns/entity/sync/core/DataHolder;", null, label15, label27, 5);
methodVisitor.visitLocalVariable("entries", "Ljava/util/List;", "Ljava/util/List<Lcom/tacz/guns/entity/sync/core/DataEntry<**>;>;", label18, label27, 6);
methodVisitor.visitLocalVariable("selfEntries", "Ljava/util/List;", "Ljava/util/List<Lcom/tacz/guns/entity/sync/core/DataEntry<**>;>;", label21, label27, 7);
methodVisitor.visitLocalVariable("trackingEntries", "Ljava/util/List;", "Ljava/util/List<Lcom/tacz/guns/entity/sync/core/DataEntry<**>;>;", label24, label27, 8);
methodVisitor.visitLocalVariable("entity", "Lnet/minecraft/world/entity/Entity;", null, label14, label27, 4);
methodVisitor.visitLocalVariable("event", "Lnet/minecraftforge/event/TickEvent$ServerTickEvent;", null, label0, label30, 0);
methodVisitor.visitLocalVariable("instance", "Lcom/tacz/guns/entity/sync/core/SyncedEntityData;", null, label1, label30, 1);
methodVisitor.visitLocalVariable("dirtyEntities", "Ljava/util/List;", "Ljava/util/List<Lnet/minecraft/world/entity/Entity;>;", label8, label30, 2);
methodVisitor.visitMaxs(6, 9);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); } }};}
