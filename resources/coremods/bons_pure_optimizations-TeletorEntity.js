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

var BONS_KEY = "alexscaves_teletor_generation_context"; var BONS_SCRIPT = "bons_pure_optimizations-TeletorEntity.js"; var BONS_TARGET = "com.github.alexmodguy.alexscaves.server.entity.living.TeletorEntity";
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

function initializeCoreMod() {return { patch: { target: {type:"CLASS",name:"com.github.alexmodguy.alexscaves.server.entity.living.TeletorEntity"}, transformer: function(c) { return bonsGuarded(c, function(c) {
var methodVisitor, annotationVisitor0, annotationVisitor1, annotationVisitor2, fieldVisitor;
removeMethod(c,"m_6518_","(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;",74);
methodVisitor = new MethodNode(1,"m_6518_","(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;",null,[]);
{
annotationVisitor0 = methodVisitor.visitAnnotation("Ljavax/annotation/Nullable;", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitAnnotableParameterCount(5, true);
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(3, "Ljavax/annotation/Nullable;", true);
annotationVisitor0.visitEnd();
}
{
annotationVisitor0 = methodVisitor.visitParameterAnnotation(4, "Ljavax/annotation/Nullable;", true);
annotationVisitor0.visitEnd();
}
methodVisitor.visitCode();
var label0 = new Label();
methodVisitor.visitLabel(label0);
methodVisitor.visitLineNumber(223, label0);
methodVisitor.visitFieldInsn(O.GETSTATIC, "com/github/alexmodguy/alexscaves/server/entity/ACEntityRegistry", "MAGNETIC_WEAPON", "Lnet/minecraftforge/registries/RegistryObject;");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraftforge/registries/RegistryObject", "get", "()Ljava/lang/Object;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/world/entity/EntityType");
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/entity/EntityType", "m_20615_", "(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;", false);
methodVisitor.visitTypeInsn(O.CHECKCAST, "com/github/alexmodguy/alexscaves/server/entity/item/MagneticWeaponEntity");
methodVisitor.visitVarInsn(O.ASTORE, 6);
var label1 = new Label();
methodVisitor.visitLabel(label1);
methodVisitor.visitLineNumber(224, label1);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "ac$spawnRandom", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "createItemStack", "(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;", false);
methodVisitor.visitVarInsn(O.ASTORE, 7);
var label2 = new Label();
methodVisitor.visitLabel(label2);
methodVisitor.visitLineNumber(225, label2);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/DifficultyInstance", "m_19057_", "()F", false);
methodVisitor.visitVarInsn(O.FSTORE, 8);
var label3 = new Label();
methodVisitor.visitLabel(label3);
methodVisitor.visitLineNumber(226, label3);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "ac$spawnRandom", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/util/RandomSource", "m_188501_", "()F", true);
methodVisitor.visitLdcInsn(number('0.25', NT.FLOAT));
methodVisitor.visitVarInsn(O.FLOAD, 8);
methodVisitor.visitLdcInsn(number('0.5', NT.FLOAT));
methodVisitor.visitInsn(O.FADD);
methodVisitor.visitInsn(O.FMUL);
methodVisitor.visitInsn(O.FCMPG);
var label4 = new Label();
methodVisitor.visitJumpInsn(O.IFGE, label4);
var label5 = new Label();
methodVisitor.visitLabel(label5);
methodVisitor.visitLineNumber(227, label5);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "ac$spawnRandom", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitLdcInsn(number('5.0', NT.FLOAT));
methodVisitor.visitVarInsn(O.FLOAD, 8);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "ac$spawnRandom", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitIntInsn(O.BIPUSH, 18);
methodVisitor.visitMethodInsn(O.INVOKEINTERFACE, "net/minecraft/util/RandomSource", "m_188503_", "(I)I", true);
methodVisitor.visitInsn(O.I2F);
methodVisitor.visitInsn(O.FMUL);
methodVisitor.visitInsn(O.FADD);
methodVisitor.visitInsn(O.F2I);
methodVisitor.visitInsn(O.ICONST_0);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "net/minecraft/world/item/enchantment/EnchantmentHelper", "m_220292_", "(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;IZ)Lnet/minecraft/world/item/ItemStack;", false);
methodVisitor.visitVarInsn(O.ASTORE, 7);
methodVisitor.visitLabel(label4);
methodVisitor.visitLineNumber(229, label4);
methodVisitor.visitFrame(O.F_APPEND,3, ["com/github/alexmodguy/alexscaves/server/entity/item/MagneticWeaponEntity", "net/minecraft/world/item/ItemStack", O.FLOAT], 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 7);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/item/MagneticWeaponEntity", "setItemStack", "(Lnet/minecraft/world/item/ItemStack;)V", false);
var label6 = new Label();
methodVisitor.visitLabel(label6);
methodVisitor.visitLineNumber(230, label6);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "getWeaponPosition", "()Lnet/minecraft/world/phys/Vec3;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/item/MagneticWeaponEntity", "m_146884_", "(Lnet/minecraft/world/phys/Vec3;)V", false);
var label7 = new Label();
methodVisitor.visitLabel(label7);
methodVisitor.visitLineNumber(231, label7);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "m_20148_", "()Ljava/util/UUID;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/item/MagneticWeaponEntity", "setControllerUUID", "(Ljava/util/UUID;)V", false);
var label8 = new Label();
methodVisitor.visitLabel(label8);
methodVisitor.visitLineNumber(232, label8);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/item/MagneticWeaponEntity", "m_20148_", "()Ljava/util/UUID;", false);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "setWeaponUUID", "(Ljava/util/UUID;)V", false);
var label9 = new Label();
methodVisitor.visitLabel(label9);
methodVisitor.visitLineNumber(233, label9);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "m_9236_", "()Lnet/minecraft/world/level/Level;", false);
methodVisitor.visitVarInsn(O.ALOAD, 6);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKESTATIC, "com/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity", "ac$addSpawnEntity", "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z", false);
methodVisitor.visitInsn(O.POP);
var label10 = new Label();
methodVisitor.visitLabel(label10);
methodVisitor.visitLineNumber(234, label10);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitVarInsn(O.ALOAD, 3);
methodVisitor.visitVarInsn(O.ALOAD, 4);
methodVisitor.visitVarInsn(O.ALOAD, 5);
methodVisitor.visitMethodInsn(O.INVOKESPECIAL, "net/minecraft/world/entity/monster/Monster", "m_6518_", "(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;", false);
methodVisitor.visitInsn(O.ARETURN);
var label11 = new Label();
methodVisitor.visitLabel(label11);
methodVisitor.visitLocalVariable("this", "Lcom/github/alexmodguy/alexscaves/server/entity/living/TeletorEntity;", null, label0, label11, 0);
methodVisitor.visitLocalVariable("level", "Lnet/minecraft/world/level/ServerLevelAccessor;", null, label0, label11, 1);
methodVisitor.visitLocalVariable("difficultyIn", "Lnet/minecraft/world/DifficultyInstance;", null, label0, label11, 2);
methodVisitor.visitLocalVariable("reason", "Lnet/minecraft/world/entity/MobSpawnType;", null, label0, label11, 3);
methodVisitor.visitLocalVariable("spawnDataIn", "Lnet/minecraft/world/entity/SpawnGroupData;", null, label0, label11, 4);
methodVisitor.visitLocalVariable("dataTag", "Lnet/minecraft/nbt/CompoundTag;", null, label0, label11, 5);
methodVisitor.visitLocalVariable("magneticWeapon", "Lcom/github/alexmodguy/alexscaves/server/entity/item/MagneticWeaponEntity;", null, label1, label11, 6);
methodVisitor.visitLocalVariable("stack", "Lnet/minecraft/world/item/ItemStack;", null, label2, label11, 7);
methodVisitor.visitLocalVariable("f", "F", null, label3, label11, 8);
methodVisitor.visitMaxs(6, 9);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"ac$spawnRandom","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/util/RandomSource;",null,[]);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/WorldGenRegion");
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/WorldGenRegion");
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/WorldGenRegion", "m_213780_", "()Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_213780_", "()Lnet/minecraft/util/RandomSource;", false);
methodVisitor.visitInsn(O.ARETURN);
methodVisitor.visitMaxs(1, 2);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
methodVisitor = new MethodNode(10,"ac$addSpawnEntity","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ServerLevelAccessor;)Z",null,[]);
methodVisitor.visitCode();
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitTypeInsn(O.INSTANCEOF, "net/minecraft/server/level/WorldGenRegion");
var label0 = new Label();
methodVisitor.visitJumpInsn(O.IFEQ, label0);
methodVisitor.visitVarInsn(O.ALOAD, 2);
methodVisitor.visitTypeInsn(O.CHECKCAST, "net/minecraft/server/level/WorldGenRegion");
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/server/level/WorldGenRegion", "m_7967_", "(Lnet/minecraft/world/entity/Entity;)Z", false);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitLabel(label0);
methodVisitor.visitFrame(O.F_SAME, 0, null, 0, null);
methodVisitor.visitVarInsn(O.ALOAD, 0);
methodVisitor.visitVarInsn(O.ALOAD, 1);
methodVisitor.visitMethodInsn(O.INVOKEVIRTUAL, "net/minecraft/world/level/Level", "m_7967_", "(Lnet/minecraft/world/entity/Entity;)Z", false);
methodVisitor.visitInsn(O.IRETURN);
methodVisitor.visitMaxs(2, 3);
methodVisitor.visitEnd();
c.methods.add(methodVisitor);
return c;
}); } }};}
