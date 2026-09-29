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

var BONS_KEY = "tacz_sync_scope"; var BONS_SCRIPT = "bons_and_furious-LivingEntityMixin.js"; var BONS_TARGET = "com.tacz.guns.mixin.common.LivingEntityMixin";
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

function initializeCoreMod() {return { patch: { target: {type:"CLASS",name:"com.tacz.guns.mixin.common.LivingEntityMixin"}, transformer: function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"onTickServerSide","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",99);
methodVisitor = new MethodNode(2,"onTickServerSide","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",null,[]);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
{
var annotationVisitor1 = annotationVisitor0.visitArray("method");
annotationVisitor1.visit(null, "tick");
annotationVisitor1.visitEnd();
}
{
var annotationVisitor1 = annotationVisitor0.visitArray("at");
{
var annotationVisitor2 = annotationVisitor1.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
annotationVisitor2.visit("value", "RETURN");
annotationVisitor2.visitEnd();
}
annotationVisitor1.visitEnd();
}
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
var label1 = new Label();
var label2 = new Label();
methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(215, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/mixin/common/LivingEntityMixin", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_5776_", "()Z", false);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFNE, label4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(217, label5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$reload", "Lcom/tacz/guns/entity/shooter/LivingEntityReload;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityReload", "tickReloadState", "()Lcom/tacz/guns/api/entity/ReloadState;", false);
methodVisitor.visitVarInsn(O.ASTORE, 2);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(218, label6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$aim", "Lcom/tacz/guns/entity/shooter/LivingEntityAim;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityAim", "tickAimingProgress", "()V", false);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(219, label7);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$aim", "Lcom/tacz/guns/entity/shooter/LivingEntityAim;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityAim", "tickSprint", "()V", false);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(220, label8);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$crawl", "Lcom/tacz/guns/entity/shooter/LivingEntityCrawl;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityCrawl", "tickCrawling", "()V", false);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(221, label9);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$bolt", "Lcom/tacz/guns/entity/shooter/LivingEntityBolt;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityBolt", "tickBolt", "()V", false);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(222, label10);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$melee", "Lcom/tacz/guns/entity/shooter/LivingEntityMelee;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityMelee", "scheduleTickMelee", "()V", false);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLineNumber(223, label11);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$speed", "Lcom/tacz/guns/entity/shooter/LivingEntitySpeedModifier;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntitySpeedModifier", "updateSpeedModifier", "()V", false);
var label12 = new Label();
methodVisitor.visitLabel(label12);
methodVisitor.visitLineNumber(224, label12);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$heat", "Lcom/tacz/guns/entity/shooter/LivingEntityHeat;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityHeat", "tickHeat", "()V", false);
var label13 = new Label();
methodVisitor.visitLabel(label13);
methodVisitor.visitLineNumber(225, label13);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity", "m_20142_", "()Z", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/mixin/common/LivingEntityMixin", "getProcessedSprintStatus", "(Z)Z", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity", "m_6858_", "(Z)V", false);
var label14 = new Label();
methodVisitor.visitLabel(label14);
methodVisitor.visitLineNumber(227, label14);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/pure/TaCZScope", "begin", "(Lnet/minecraft/world/entity/Entity;)V", false);
methodVisitor.visitLabel(label0);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "SHOOT_COOL_DOWN_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shoot", "Lcom/tacz/guns/entity/shooter/LivingEntityShoot;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityShoot", "getShootCoolDown", "()J", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
var label15 = new Label();
methodVisitor.visitLabel(label15);
methodVisitor.visitLineNumber(228, label15);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "MELEE_COOL_DOWN_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$melee", "Lcom/tacz/guns/entity/shooter/LivingEntityMelee;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityMelee", "getMeleeCoolDown", "()J", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
var label16 = new Label();
methodVisitor.visitLabel(label16);
methodVisitor.visitLineNumber(229, label16);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "DRAW_COOL_DOWN_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$draw", "Lcom/tacz/guns/entity/shooter/LivingEntityDrawGun;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/shooter/LivingEntityDrawGun", "getDrawCoolDown", "()J", false);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
var label17 = new Label();
methodVisitor.visitLabel(label17);
methodVisitor.visitLineNumber(230, label17);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "IS_BOLTING_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$data", "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;");
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/entity/shooter/ShooterDataHolder", "isBolting", "Z");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
var label18 = new Label();
methodVisitor.visitLabel(label18);
methodVisitor.visitLineNumber(231, label18);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "RELOAD_STATE_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
var label19 = new Label();
methodVisitor.visitLabel(label19);
methodVisitor.visitLineNumber(232, label19);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "AIMING_PROGRESS_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$data", "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;");
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/entity/shooter/ShooterDataHolder", "aimingProgress", "F");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
var label20 = new Label();
methodVisitor.visitLabel(label20);
methodVisitor.visitLineNumber(233, label20);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "IS_AIMING_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$data", "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;");
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/entity/shooter/ShooterDataHolder", "isAiming", "Z");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
var label21 = new Label();
methodVisitor.visitLabel(label21);
methodVisitor.visitLineNumber(234, label21);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/tacz/guns/entity/sync/ModSyncedEntityData", "SPRINT_TIME_KEY", "Lcom/tacz/guns/entity/sync/core/SyncedDataKey;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$shooter", "Lnet/minecraft/world/entity/LivingEntity;");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/mixin/common/LivingEntityMixin", "tacz$data", "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;");
methodVisitor.visitFieldInsn(O.GETFIELD, "com/tacz/guns/entity/shooter/ShooterDataHolder", "sprintTimeS", "F");
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/tacz/guns/entity/sync/core/SyncedDataKey", "setValue", "(Lnet/minecraft/world/entity/Entity;Ljava/lang/Object;)V", false);
methodVisitor.visitLabel(label1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/pure/TaCZScope", "end", "()V", false);
methodVisitor.visitJumpInsn(O.GOTO, label4);
methodVisitor.visitLabel(label2);
methodVisitor.visitFrame(O.F_FULL, 3, ["com/tacz/guns/mixin/common/LivingEntityMixin", "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "com/tacz/guns/api/entity/ReloadState"], 1, ["java/lang/Throwable"]);
methodVisitor.visitVarInsn(O.ASTORE, 3);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "agentcraft/pure/TaCZScope", "end", "()V", false);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitInsn(O.ATHROW);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(236, label4);
methodVisitor.visitFrame(O.F_CHOP,1, null, 0, null);
methodVisitor.visitInsn(O.RETURN);
var label22 = new Label();
methodVisitor.visitLabel(label22);
methodVisitor.visitLocalVariable("reloadState", "Lcom/tacz/guns/api/entity/ReloadState;", null, label6, label4, 2);
methodVisitor.visitLocalVariable("this", "Lcom/tacz/guns/mixin/common/LivingEntityMixin;", null, label3, label22, 0);
methodVisitor.visitLocalVariable("ci", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;", null, label3, label22, 1);
methodVisitor.visitMaxs(4, 4);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); } }};}
